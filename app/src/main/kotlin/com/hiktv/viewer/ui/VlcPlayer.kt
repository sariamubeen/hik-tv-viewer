package com.hiktv.viewer.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * One shared LibVLC engine for the whole app (the documented pattern - many
 * MediaPlayers per LibVLC instance, not one LibVLC each). Per-channel tuning
 * (smart-codec threading/caching) is applied per-Media via addOption, not
 * baked into this instance-wide default.
 */
private object VlcEngine {
    @Volatile private var instance: LibVLC? = null

    fun get(context: Context): LibVLC = instance ?: synchronized(this) {
        instance ?: LibVLC(
            context.applicationContext,
            arrayListOf("--rtsp-tcp", "--network-caching=150"),
        ).also { instance = it }
    }
}

/**
 * Plays an RTSP stream via libVLC, forced to RTSP-over-TCP (Hikvision over UDP
 * is a known source of dropped packets / the black-frame symptom). When
 * [smartCodecEnabled] is true (Hikvision's proprietary H.265+/H.264+), lowers
 * avcodec threading and raises network caching for that stream only - the
 * documented mitigation for the frame-threading interaction that variant
 * triggers at low frame rates. Calls [onError] once if the stream fails to
 * play, so the caller can fall back (e.g. to the snapshot poller).
 *
 * A single MediaPlayer's full lifecycle (create, attach, play; stop, detach,
 * release, in that order) lives in one DisposableEffect keyed on the stream
 * identity, deliberately not muted - splitting create/play/attach across
 * several sibling effects risked release() racing ahead of stop() on
 * teardown, a native use-after-free on the ordinary "open a camera, press
 * Back" path. Mute is a separate, lightweight LaunchedEffect with no
 * teardown of its own.
 */
@Composable
fun VlcPlayer(
    rtspUrl: String,
    muted: Boolean,
    smartCodecEnabled: Boolean,
    modifier: Modifier = Modifier,
    onError: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val libVLC = remember { VlcEngine.get(context) }
    val currentOnError = rememberUpdatedState(onError)
    val videoLayout = remember { VLCVideoLayout(context) }

    var currentPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(rtspUrl, smartCodecEnabled) {
        val player = MediaPlayer(libVLC)
        player.setEventListener { event ->
            if (event.type == MediaPlayer.Event.EncounteredError) currentOnError.value?.invoke()
        }
        player.attachViews(videoLayout, null, false, false)
        val media = Media(libVLC, Uri.parse(rtspUrl)).apply {
            setHWDecoderEnabled(true, false)
            if (smartCodecEnabled) {
                addOption(":network-caching=300")
                addOption(":avcodec-threads=1")
            }
        }
        player.media = media
        media.release()
        player.play()
        currentPlayer = player

        onDispose {
            currentPlayer = null
            player.stop()
            player.detachViews()
            player.release()
        }
    }

    LaunchedEffect(currentPlayer, muted) {
        currentPlayer?.setVolume(if (muted) 0 else 100)
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { videoLayout })
}
