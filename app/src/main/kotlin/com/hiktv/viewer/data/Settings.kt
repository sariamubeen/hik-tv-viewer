package com.hiktv.viewer.data

data class Settings(
    val host: String = "",
    val port: Int = 80,
    val rtspPort: Int = 554,
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = false,
    val channels: List<Int> = listOf(1, 2, 3, 4, 5, 7),
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun httpBaseUrl(): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$host:$port"
    }

    fun snapshotUrl(channel: Int): String =
        "${httpBaseUrl()}/ISAPI/Streaming/channels/${channel}01/picture?t=" + System.currentTimeMillis()

    fun deviceInfoUrl(): String = "${httpBaseUrl()}/ISAPI/System/deviceInfo"

    fun rtspUrl(channel: Int, sub: Boolean = false): String {
        val streamId = "${channel}0${if (sub) 2 else 1}"
        val encUser = java.net.URLEncoder.encode(username, "UTF-8")
        val encPass = java.net.URLEncoder.encode(password, "UTF-8")
        return "rtsp://$encUser:$encPass@$host:$rtspPort/Streaming/Channels/$streamId"
    }
}
