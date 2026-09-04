package com.hiktv.viewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hiktv.viewer.data.Device
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

private const val LIVE_POLL_MS = 100L // snapshot-fallback rate: 10 fps ceiling
private const val HUD_AUTOHIDE_MS = 4000L

/**
 * Fullscreen single-camera view: real video with audio via libVLC, main
 * stream. Falls back to the snapshot poller if the channel is offline or its
 * video fails. Back always returns to the grid via BackHandler (the previous
 * onKeyEvent-based Back handling did not fire on modern Android).
 */
@Composable
fun LiveScreen(
    device: Device,
    client: OkHttpClient,
    initialChannelId: Int,
    onClose: () -> Unit,
) {
    var index by remember {
        mutableIntStateOf(
            device.channels.indexOfFirst { it.id == initialChannelId }.coerceAtLeast(0),
        )
    }
    val channel = device.channels.getOrElse(index) { device.channels.first() }

    var videoFailed by remember(channel.id) { mutableStateOf(false) }
    var muted by remember(channel.id) { mutableStateOf(!channel.hasAudio) }
    val showVideo = channel.online && !videoFailed

    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hudVisible by remember { mutableStateOf(true) }
    LaunchedEffect(lastInteraction) {
        hudVisible = true
        delay(HUD_AUTOHIDE_MS)
        hudVisible = false
    }

    BackHandler(onBack = onClose)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyUp) return@onKeyEvent false
                lastInteraction = System.currentTimeMillis()
                when (ev.key) {
                    Key.DirectionRight -> {
                        index = (index + 1) % device.channels.size; true
                    }
                    Key.DirectionLeft -> {
                        index = (index - 1 + device.channels.size) % device.channels.size; true
                    }
                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                        if (channel.hasAudio) muted = !muted; true
                    }
                    else -> false
                }
            },
    ) {
        if (showVideo) {
            VlcPlayer(
                rtspUrl = device.rtspUrl(channel.id, sub = false),
                muted = muted,
                smartCodecEnabled = channel.smartCodecEnabled,
                modifier = Modifier.fillMaxSize(),
                onError = { videoFailed = true },
            )
        } else {
            val frame = rememberSnapshotPoller(
                client = client,
                device = device,
                channel = channel.id,
                sub = false,
                periodMs = LIVE_POLL_MS,
            )
            when (val s = frame.value) {
                is SnapshotState.Frame -> Image(
                    bitmap = s.bitmap,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                is SnapshotState.Error -> CenteredStatus("${channel.name}: no signal")
                SnapshotState.Loading -> CenteredStatus("Connecting to ${channel.name}...")
            }
        }

        AnimatedVisibility(visible = hudVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color(0xCC11111B), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                val audioHint = if (channel.hasAudio) {
                    if (muted) " · OK to unmute" else " · OK to mute"
                } else {
                    ""
                }
                Text(
                    text = "${channel.name} · < > to switch · Back to grid$audioHint",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun CenteredStatus(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color(0xFF6C7086))
    }
}
