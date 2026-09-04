package com.hiktv.viewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hiktv.viewer.data.Device

/**
 * Reached from the grid's Menu key. Credentials are entered once and never
 * re-prompted automatically - Sign out is the one deliberate way back to the
 * scan/login flow, and it wipes the saved credentials.
 */
@Composable
fun DeviceSettingsScreen(
    device: Device,
    onEditChannels: () -> Unit,
    onAddDevice: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Device settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = device.model.ifBlank { "Hikvision device" } + " · ${device.host}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Button(onClick = onEditChannels, modifier = Modifier.fillMaxWidth()) {
                Text("Edit cameras")
            }
            Button(onClick = onAddDevice, modifier = Modifier.fillMaxWidth()) {
                Text("Add another device")
            }
            Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out (forget this device)")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Video playback uses libVLC (LGPL-2.1 / GPL). " +
                    "See THIRD-PARTY-LICENSES.md in the project repository.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
