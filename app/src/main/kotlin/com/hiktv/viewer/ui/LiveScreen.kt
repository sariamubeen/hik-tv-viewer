package com.hiktv.viewer.ui

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
import com.hiktv.viewer.data.Settings
import okhttp3.OkHttpClient

private const val LIVE_POLL_MS = 100L  // 10 fps for fullscreen single channel

@Composable
fun LiveScreen(
    settings: Settings,
    client: OkHttpClient,
    initialChannel: Int,
    onClose: () -> Unit,
) {
    var index by remember {
        mutableIntStateOf(settings.channels.indexOf(initialChannel).coerceAtLeast(0))
    }
    val channel = settings.channels.getOrElse(index) { settings.channels.first() }

    val frame = rememberSnapshotPoller(
        client = client,
        urlProvider = { settings.snapshotUrl(channel) },
        periodMs = LIVE_POLL_MS,
    )

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
                when (ev.key) {
                    Key.DirectionRight -> {
                        index = (index + 1) % settings.channels.size; true
                    }
                    Key.DirectionLeft -> {
                        index = (index - 1 + settings.channels.size) % settings.channels.size; true
                    }
                    Key.Back, Key.Escape -> {
                        onClose(); true
                    }
                    else -> false
                }
            },
    ) {
        val bitmap = frame.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Channel $channel",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Color(0xCC11111B), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Channel $channel · ◀ ▶ to switch · Back to grid",
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
