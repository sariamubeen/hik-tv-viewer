package com.hiktv.viewer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.collections.immutable.toPersistentList
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the saved device list and which one is selected, encrypted on-device.
 * Credentials are entered once and kept until [clear] (sign out / forget device)
 * is called - the app never re-prompts on its own.
 */
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

    data class State(
        val devices: List<Device>,
        val selectedDeviceId: String?,
    ) {
        val selected: Device?
            get() = devices.find { it.id == selectedDeviceId }
    }

    fun load(): State {
        migrateLegacyIfNeeded()
        val devices = prefs.getString(KEY_DEVICES, null)
            ?.let { runCatching { parseDevices(it) }.getOrNull() }
            ?: emptyList()
        val selectedId = prefs.getString(KEY_SELECTED_ID, null)
        return State(devices, selectedId)
    }

    fun save(devices: List<Device>, selectedDeviceId: String?) {
        prefs.edit()
            .putString(KEY_DEVICES, serializeDevices(devices))
            .putString(KEY_SELECTED_ID, selectedDeviceId)
            .apply()
    }

    fun saveDevice(device: Device, selectAsCurrent: Boolean = true) {
        val current = load()
        val updated = current.devices.filterNot { it.id == device.id } + device
        save(updated, if (selectAsCurrent) device.id else current.selectedDeviceId)
    }

    /** Sign out / forget: wipes every saved device and credential. */
    fun clear() = prefs.edit().clear().apply()

    private fun serializeDevices(devices: List<Device>): String {
        val array = JSONArray()
        for (d in devices) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("host", d.host)
            obj.put("port", d.port)
            obj.put("rtspPort", d.rtspPort)
            obj.put("username", d.username)
            obj.put("password", d.password)
            obj.put("useHttps", d.useHttps)
            obj.put("model", d.model)
            val channelsArray = JSONArray()
            for (c in d.channels) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("name", c.name)
                cObj.put("online", c.online)
                cObj.put("mainCodec", c.mainCodec)
                cObj.put("subCodec", c.subCodec)
                cObj.put("hasAudio", c.hasAudio)
                cObj.put("smartCodecEnabled", c.smartCodecEnabled)
                channelsArray.put(cObj)
            }
            obj.put("channels", channelsArray)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseDevices(json: String): List<Device> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val channelsArray = obj.optJSONArray("channels") ?: JSONArray()
            val channels = (0 until channelsArray.length()).map { j ->
                val c = channelsArray.getJSONObject(j)
                Channel(
                    id = c.getInt("id"),
                    name = c.getString("name"),
                    online = c.optBoolean("online", true),
                    mainCodec = c.optString("mainCodec", ""),
                    subCodec = c.optString("subCodec", ""),
                    hasAudio = c.optBoolean("hasAudio", false),
                    smartCodecEnabled = c.optBoolean("smartCodecEnabled", false),
                )
            }
            Device(
                id = obj.getString("id"),
                host = obj.getString("host"),
                port = obj.optInt("port", 80),
                rtspPort = obj.optInt("rtspPort", 554),
                username = obj.optString("username", ""),
                password = obj.optString("password", ""),
                useHttps = obj.optBoolean("useHttps", false),
                model = obj.optString("model", ""),
                channels = channels.toPersistentList(),
            )
        }
    }

    /** One-time migration from the pre-multi-device flat key layout. */
    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_DEVICES)) return
        val legacyHost = prefs.getString(KEY_LEGACY_HOST, null) ?: return
        if (legacyHost.isBlank()) return

        val device = buildLegacyDevice(
            host = legacyHost,
            port = prefs.getInt(KEY_LEGACY_PORT, 80),
            rtspPort = prefs.getInt(KEY_LEGACY_RTSP_PORT, 554),
            username = prefs.getString(KEY_LEGACY_USER, "") ?: "",
            password = prefs.getString(KEY_LEGACY_PASS, "") ?: "",
            useHttps = prefs.getBoolean(KEY_LEGACY_HTTPS, false),
            channelsCsv = prefs.getString(KEY_LEGACY_CHANNELS, null) ?: "",
        )

        prefs.edit()
            .putString(KEY_DEVICES, serializeDevices(listOf(device)))
            .putString(KEY_SELECTED_ID, device.id)
            .remove(KEY_LEGACY_HOST)
            .remove(KEY_LEGACY_PORT)
            .remove(KEY_LEGACY_RTSP_PORT)
            .remove(KEY_LEGACY_USER)
            .remove(KEY_LEGACY_PASS)
            .remove(KEY_LEGACY_HTTPS)
            .remove(KEY_LEGACY_CHANNELS)
            .apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices_v2"
        const val KEY_SELECTED_ID = "selected_device_id"

        // Pre-v2 flat keys, read once for migration then removed.
        const val KEY_LEGACY_HOST = "host"
        const val KEY_LEGACY_PORT = "port"
        const val KEY_LEGACY_RTSP_PORT = "rtsp_port"
        const val KEY_LEGACY_USER = "user"
        const val KEY_LEGACY_PASS = "pass"
        const val KEY_LEGACY_HTTPS = "https"
        const val KEY_LEGACY_CHANNELS = "channels"
    }
}

/**
 * Pure transformation from the old flat-key field values to a single [Device]
 * with placeholder channel names (the old model only ever stored a bare
 * channel-number CSV). Separated from [SecureStore.migrateLegacyIfNeeded] so
 * it is unit-testable without an Android EncryptedSharedPreferences instance.
 */
internal fun buildLegacyDevice(
    host: String,
    port: Int,
    rtspPort: Int,
    username: String,
    password: String,
    useHttps: Boolean,
    channelsCsv: String,
): Device {
    val legacyChannels = channelsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
    return Device(
        id = "$host:$port",
        host = host,
        port = port,
        rtspPort = rtspPort,
        username = username,
        password = password,
        useHttps = useHttps,
        channels = legacyChannels.map { id ->
            Channel(
                id = id,
                name = "Channel $id",
                online = true,
                mainCodec = "",
                subCodec = "",
                hasAudio = false,
                smartCodecEnabled = false,
            )
        }.toPersistentList(),
    )
}
