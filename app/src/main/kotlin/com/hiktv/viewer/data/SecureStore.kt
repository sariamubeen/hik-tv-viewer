package com.hiktv.viewer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hik_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): Settings = Settings(
        host = prefs.getString(KEY_HOST, "") ?: "",
        port = prefs.getInt(KEY_PORT, 80),
        rtspPort = prefs.getInt(KEY_RTSP_PORT, 554),
        username = prefs.getString(KEY_USER, "") ?: "",
        password = prefs.getString(KEY_PASS, "") ?: "",
        useHttps = prefs.getBoolean(KEY_HTTPS, false),
        channels = (prefs.getString(KEY_CHANNELS, "1,2,3,4,5,7") ?: "1,2,3,4,5,7")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(1, 2, 3, 4, 5, 7) },
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString(KEY_HOST, s.host.trim())
            .putInt(KEY_PORT, s.port)
            .putInt(KEY_RTSP_PORT, s.rtspPort)
            .putString(KEY_USER, s.username)
            .putString(KEY_PASS, s.password)
            .putBoolean(KEY_HTTPS, s.useHttps)
            .putString(KEY_CHANNELS, s.channels.joinToString(","))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_RTSP_PORT = "rtsp_port"
        const val KEY_USER = "user"
        const val KEY_PASS = "pass"
        const val KEY_HTTPS = "https"
        const val KEY_CHANNELS = "channels"
    }
}
