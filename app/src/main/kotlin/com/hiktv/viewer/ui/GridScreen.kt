package com.hiktv.viewer.ui

import android.media.MediaFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.hiktv.viewer.data.Channel
import com.hiktv.viewer.data.DecoderCapabilities
import com.hiktv.viewer.data.Device
import okhttp3.OkHttpClient

private const val GRID_POLL_MS = 200L // 5 fps ceiling per snapshot-fallback tile
private const val GRID_COLUMNS = 3

/**
 * Scrolling grid, sized to however many channels the device actually has (not
 * a fixed 3x2). Each tile plays live muted video up to what
 * [DecoderCapabilities] says this TV can actually decode concurrently for
 * that channel's codec; tiles beyond that budget - and any tile whose video
 * fails - fall back to the snapshot poller.
 */
@Composable
fun GridScreen(
    device: Device,
    client: OkHttpClient,
    initialFocusedChannelId: Int?,
    onPickChannel: (Int) -> Unit,
    onFocusedChannelChanged: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val hevcBudget = remember { DecoderCapabilities.maxConcurrentInstances(MediaFormat.MIMETYPE_VIDEO_HEVC) }
    val avcBudget = remember { DecoderCapabilities.maxConcurrentInstances(MediaFormat.MIMETYPE_VIDEO_AVC) }

    // Precomputed once per channel set so tile assignment doesn't shuffle on
    // every recomposition - a stable, deterministic pass over the real budget.
    val videoAssignment = remember(device.channels, hevcBudget, avcBudget) {
        var hevcUsed = 0
        var avcUsed = 0
        device.channels.associate { ch ->
            val codec = ch.subCodec.ifBlank { ch.mainCodec }
            val useVideo = when {
                !ch.online -> false
                codec.equals("H.265", ignoreCase = true) && hevcUsed < hevcBudget -> {
                    hevcUsed++; true
                }
                codec.equals("H.264", ignoreCase = true) && avcUsed < avcBudget -> {
                    avcUsed++; true
                }
                else -> false
            }
            ch.id to useVideo
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(device.channels, key = { it.id }) { channel ->
                CameraTile(
                    channel = channel,
                    device = device,
                    client = client,
                    useVideo = videoAssignment[channel.id] == true,
                    autoFocus = channel.id == (initialFocusedChannelId ?: device.channels.firstOrNull()?.id),
                    onFocused = { onFocusedChannelChanged(channel.id) },
                    onClick = { onPickChannel(channel.id) },
                    onMenu = onOpenSettings,
                    // A vertically-scrolling LazyVerticalGrid gives items
                    // infinite height, so fillMaxSize() alone would resolve to
                    // the child's intrinsic size (an AndroidView reports none:
                    // a 0-height tile). aspectRatio derives height from the
                    // measured column width instead.
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(1.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraTile(
    channel: Channel,
    device: Device,
    client: OkHttpClient,
    useVideo: Boolean,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoFailed by remember(channel.id) { mutableStateOf(false) }
    val showVideo = useVideo && !videoFailed

    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    val borderColor = if (focused) Color(0xFF89B4FA) else Color.Transparent
    val borderWidth = if (focused) 3.dp else 0.dp

    Box(
        modifier = modifier
            .background(Color.Black)
            .border(borderWidth, borderColor)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                        onClick(); true
                    }
                    Key.Menu -> {
                        onMenu(); true
                    }
                    else -> false
                }
            },
    ) {
        if (showVideo) {
            VlcPlayer(
                rtspUrl = device.rtspUrl(channel.id, sub = true),
                muted = true,
                smartCodecEnabled = channel.smartCodecEnabled,
                modifier = Modifier.fillMaxSize(),
                onError = { videoFailed = true },
            )
        } else {
            val frame = rememberSnapshotPoller(
                client = client,
                device = device,
                channel = channel.id,
                sub = true,
                periodMs = GRID_POLL_MS,
            )
            when (val s = frame.value) {
                is SnapshotState.Frame -> Image(
                    bitmap = s.bitmap,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                is SnapshotState.Error -> TileStatus(channel.name, "No signal")
                SnapshotState.Loading -> TileStatus(channel.name, "Connecting...")
            }
        }

        Text(
            text = channel.name,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TileStatus(name: String, status: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$name\n$status", color = Color(0xFF6C7086))
    }
}
