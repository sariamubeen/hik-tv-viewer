package com.hiktv.viewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hiktv.viewer.data.Channel
import com.hiktv.viewer.data.Device
import com.hiktv.viewer.data.DiscoveredDevice
import com.hiktv.viewer.data.HikClient
import com.hiktv.viewer.data.SecureStore
import com.hiktv.viewer.ui.theme.HikTvTheme
import kotlinx.collections.immutable.toPersistentList

private sealed interface Screen {
    data object DevicePicker : Screen
    data class Credentials(val prefill: DiscoveredDevice?) : Screen
    data class ChannelPicker(val device: Device, val channels: List<Channel>) : Screen
    data object Grid : Screen
    data class Live(val channelId: Int) : Screen
    data object DeviceSettings : Screen
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    var storeState by remember { mutableStateOf(store.load()) }
    val device = storeState.selected

    var screen: Screen by remember {
        mutableStateOf(
            if (device != null && device.channels.isNotEmpty()) Screen.Grid else Screen.DevicePicker,
        )
    }

    // A tile can only be clicked while focused, so this is already correct by
    // the time the grid opens a camera - restoring focus to the channel just
    // viewed needs no extra plumbing through screen transitions.
    var lastFocusedChannelId by remember { mutableStateOf<Int?>(null) }

    val client = remember(device?.id, device?.username, device?.password) {
        device?.let { HikClient.newOkHttp(it) }
    }

    HikTvTheme {
        when (val s = screen) {
            Screen.DevicePicker -> DevicePickerScreen(
                onSelect = { discovered -> screen = Screen.Credentials(discovered) },
                onManualEntry = { screen = Screen.Credentials(null) },
                onBack = if (device != null) ({ screen = Screen.Grid }) else null,
            )

            is Screen.Credentials -> CredentialsScreen(
                prefill = s.prefill,
                onConnected = { connectedDevice, channels ->
                    screen = Screen.ChannelPicker(connectedDevice, channels)
                },
                onBack = { screen = if (device != null) Screen.Grid else Screen.DevicePicker },
            )

            is Screen.ChannelPicker -> ChannelPickerScreen(
                channels = s.channels,
                onConfirm = { selectedChannels ->
                    val finalDevice = s.device.copy(channels = selectedChannels.toPersistentList())
                    store.saveDevice(finalDevice)
                    storeState = store.load()
                    lastFocusedChannelId = null
                    screen = Screen.Grid
                },
                onBack = { screen = Screen.DevicePicker },
            )

            Screen.Grid -> {
                val d = device
                if (d == null || client == null) {
                    screen = Screen.DevicePicker
                } else {
                    GridScreen(
                        device = d,
                        client = client,
                        initialFocusedChannelId = lastFocusedChannelId,
                        onPickChannel = { channelId -> screen = Screen.Live(channelId) },
                        onFocusedChannelChanged = { id -> lastFocusedChannelId = id },
                        onOpenSettings = { screen = Screen.DeviceSettings },
                    )
                }
            }

            is Screen.Live -> {
                val d = device
                if (d == null || client == null) {
                    screen = Screen.DevicePicker
                } else {
                    LiveScreen(
                        device = d,
                        client = client,
                        initialChannelId = s.channelId,
                        onClose = { screen = Screen.Grid },
                    )
                }
            }

            Screen.DeviceSettings -> {
                val d = device
                if (d == null) {
                    screen = Screen.DevicePicker
                } else {
                    DeviceSettingsScreen(
                        device = d,
                        onEditChannels = { screen = Screen.ChannelPicker(d, d.channels) },
                        onAddDevice = { screen = Screen.DevicePicker },
                        onSignOut = {
                            store.clear()
                            storeState = store.load()
                            screen = Screen.DevicePicker
                        },
                        onBack = { screen = Screen.Grid },
                    )
                }
            }
        }
    }
}
