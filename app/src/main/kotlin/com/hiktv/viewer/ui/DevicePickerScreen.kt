package com.hiktv.viewer.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hiktv.viewer.data.DiscoveredDevice
import com.hiktv.viewer.data.Discovery

/**
 * Scans the LAN (SADP + subnet sweep, both credential-free) and shows results
 * as they arrive. Manual IP entry stays a first-class option alongside
 * scanning, since scans fail on segmented networks.
 */
@Composable
fun DevicePickerScreen(
    onSelect: (DiscoveredDevice) -> Unit,
    onManualEntry: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val found = remember { mutableStateMapOf<String, DiscoveredDevice>() }
    var scanning by remember { mutableStateOf(true) }

    if (onBack != null) {
        androidx.activity.compose.BackHandler(onBack = onBack)
    }

    LaunchedEffect(Unit) {
        found.clear()
        scanning = true
        Discovery.scan(context) { device -> found[device.host] = device }
        scanning = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Find your DVR / NVR",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (scanning) "Scanning the local network..." else {
                    if (found.isEmpty()) "No devices found." else "${found.size} device(s) found."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            found.values.sortedBy { it.host }.forEach { device ->
                Button(
                    onClick = { onSelect(device) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(device.model.ifBlank { "Hikvision device" })
                        Text(
                            text = device.host + (device.serial.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
                Text("Enter IP address manually")
            }
        }
    }
}
