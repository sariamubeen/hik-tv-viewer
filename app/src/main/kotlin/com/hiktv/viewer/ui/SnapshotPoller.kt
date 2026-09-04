package com.hiktv.viewer.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hiktv.viewer.data.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface SnapshotState {
    data object Loading : SnapshotState
    data class Frame(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : SnapshotState
    data class Error(val message: String) : SnapshotState
}

/**
 * Polls a JPEG snapshot URL on a fixed interval. Frame-paced: [periodMs] is the
 * target time between frame *starts*, so the delay after each fetch is only
 * the remainder - the configured fps is the real fps, not an optimistic
 * ceiling on top of however long the fetch took.
 *
 * Keyed on [device] and [channel] (stable values - Device is @Immutable), not
 * on a per-recomposition lambda: an unstable urlProvider lambda changing
 * identity on every recomposition, restarting this effect on every single
 * frame, was the previous version's actual bug. The URL is rebuilt fresh on
 * every loop iteration (not once at effect start) so its cache-busting
 * timestamp is live rather than frozen for the poller's entire lifetime -
 * without a fresh URL each request, the DVR (or an intermediate cache) can
 * keep serving the same frame back.
 *
 * A single failed fetch keeps showing the last good frame (no flicker); three
 * in a row surfaces [SnapshotState.Error] so a dead camera is visibly distinct
 * from a static scene, instead of silently freezing forever.
 */
@Composable
fun rememberSnapshotPoller(
    client: OkHttpClient,
    device: Device,
    channel: Int,
    sub: Boolean,
    periodMs: Long,
): State<SnapshotState> {
    val state = remember(device.id, channel) {
        mutableStateOf<SnapshotState>(SnapshotState.Loading)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(client, device, channel, sub) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var consecutiveFailures = 0
            while (true) {
                val started = System.currentTimeMillis()
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(device.snapshotUrl(channel, sub)).get().build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            val bytes = resp.body?.bytes() ?: error("empty response")
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("corrupt image")
                        }
                    }
                }
                result.fold(
                    onSuccess = { bmp ->
                        consecutiveFailures = 0
                        state.value = SnapshotState.Frame(bmp.asImageBitmap())
                    },
                    onFailure = { e ->
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            state.value = SnapshotState.Error(e.message ?: "unreachable")
                        }
                    },
                )
                val elapsed = System.currentTimeMillis() - started
                delay((periodMs - elapsed).coerceAtLeast(0))
            }
        }
    }
    return state
}
