package com.hiktv.viewer.data

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Answers "how many of these can THIS TV actually decode at once" by asking
 * the platform, instead of assuming a fixed number for "cheap" or "capable"
 * hardware. The grid sizes its live-video tile budget from this.
 *
 * Cached per mime type: the device's decoder set is fixed for the process's
 * lifetime, and MediaCodecList(REGULAR_CODECS) enumerates every codec on the
 * device, expensive enough to be a visible hitch if repeated on every Back
 * press back into the grid.
 */
object DecoderCapabilities {

    private val cache = mutableMapOf<String, Int>()

    fun maxConcurrentInstances(mimeType: String): Int = cache.getOrPut(mimeType) {
        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val probeFormat = MediaFormat.createVideoFormat(mimeType, 1920, 1080)
            val codecName = list.findDecoderForFormat(probeFormat) ?: return@getOrPut 0
            val info = list.codecInfos.firstOrNull { it.name == codecName } ?: return@getOrPut 0
            info.getCapabilitiesForType(mimeType).maxSupportedInstances
        } catch (_: Exception) {
            0
        }
    }

    fun maxConcurrentHevc(): Int = maxConcurrentInstances(MediaFormat.MIMETYPE_VIDEO_HEVC)
    fun maxConcurrentAvc(): Int = maxConcurrentInstances(MediaFormat.MIMETYPE_VIDEO_AVC)
}
