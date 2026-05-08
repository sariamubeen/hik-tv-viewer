package com.hiktv.viewer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.hiktv.viewer.data.Settings
import okhttp3.OkHttpClient

private const val GRID_POLL_MS = 200L  // 5 fps per tile

@Composable
fun GridScreen(
    settings: Settings,
    client: OkHttpClient,
    onPickChannel: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val rows = settings.channels.chunked(3)
        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                row.forEachIndexed { colIdx, channel ->
                    val isFirst = rowIdx == 0 && colIdx == 0
                    CameraTile(
                        channel = channel,
                        client = client,
                        urlProvider = { settings.snapshotUrl(channel) },
                        autoFocus = isFirst,
                        onClick = { onPickChannel(channel) },
                        onMenu = onOpenSettings,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(Color.Black),
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraTile(
    channel: Int,
    client: OkHttpClient,
    urlProvider: () -> String,
    autoFocus: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = rememberSnapshotPoller(client, urlProvider, GRID_POLL_MS)

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
            .onFocusChanged { focused = it.isFocused }
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
        val bitmap = frame.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Camera $channel",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
