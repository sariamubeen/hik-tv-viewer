package com.hiktv.viewer.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Polls a JPEG snapshot URL on a fixed interval. The previous bitmap stays
 * in state until a new one decodes successfully — gives a smooth handoff
 * between frames with no blank flash.
 */
@Composable
fun rememberSnapshotPoller(
    client: OkHttpClient,
    urlProvider: () -> String,
    periodMs: Long,
): State<ImageBitmap?> {
    val state = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(client, urlProvider) {
        while (true) {
            val bmp = runCatching {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(urlProvider()).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }.getOrNull()
            if (bmp != null) state.value = bmp.asImageBitmap()
            delay(periodMs)
        }
    }
    return state
}
