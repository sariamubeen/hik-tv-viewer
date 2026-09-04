package com.hiktv.viewer.data

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One saved Hikvision DVR/NVR. Compose-stable: all collection fields are immutable. */
@Immutable
data class Device(
    val id: String,              // stable key: serial number if known, else "host:port"
    val host: String,
    val port: Int = 80,
    val rtspPort: Int = 554,
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = false,
    val model: String = "",      // from /ISAPI/System/deviceInfo
    val channels: ImmutableList<Channel> = persistentListOf(),
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun httpBaseUrl(): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$host:$port"
    }

    /**
     * channel is the physical channel number (1-based). Grid tiles should pass
     * sub=true - the substream is far smaller (352x288 on this device's own
     * DVR vs. 960x1080 main), which is a bigger win for per-tile decode cost
     * than any client-side downscaling trick.
     */
    fun snapshotUrl(channel: Int, sub: Boolean = false): String =
        "${httpBaseUrl()}/ISAPI/Streaming/channels/${streamId(channel, sub)}/picture?t=" +
            System.currentTimeMillis()

    fun deviceInfoUrl(): String = "${httpBaseUrl()}/ISAPI/System/deviceInfo"

    fun rtspUrl(channel: Int, sub: Boolean = false): String {
        val encUser = java.net.URLEncoder.encode(username, "UTF-8")
        val encPass = java.net.URLEncoder.encode(password, "UTF-8")
        return "rtsp://$encUser:$encPass@$host:$rtspPort/Streaming/Channels/${streamId(channel, sub)}"
    }
}

/**
 * ISAPI stream id: (channel * 100) + streamType, streamType 1=main 2=sub.
 * Confirmed against Hikvision's own doc and a live device: this is arithmetic,
 * not string concatenation - matters once channel numbers reach two digits.
 */
fun streamId(channel: Int, sub: Boolean): Int = channel * 100 + if (sub) 2 else 1

/** One physical channel on a [Device], as enumerated from the DVR itself. */
@Immutable
data class Channel(
    val id: Int,                    // physical channel number, e.g. 3
    val name: String,                // real name from the DVR, "Channel N" if blank
    val online: Boolean,
    val mainCodec: String,           // "H.265" / "H.264" / "MJPEG", from videoCodecType
    val subCodec: String,
    val hasAudio: Boolean,           // from Audio/enabled
    val smartCodecEnabled: Boolean,  // from SmartCodec/enabled (Hikvision H.265+/H.264+)
)
