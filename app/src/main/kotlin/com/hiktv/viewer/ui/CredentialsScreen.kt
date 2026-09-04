package com.hiktv.viewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hiktv.viewer.data.Channel
import com.hiktv.viewer.data.Device
import com.hiktv.viewer.data.DiscoveredDevice
import com.hiktv.viewer.data.HikClient
import com.hiktv.viewer.data.Isapi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Credentials entry: host/port/https/user/pass, pre-filled from a scan result
 * when [prefill] is not null. Connect verifies the device, enumerates its real
 * channels via ISAPI (no more hardcoded channel list), and hands both back.
 */
@Composable
fun CredentialsScreen(
    prefill: DiscoveredDevice?,
    onConnected: (Device, List<Channel>) -> Unit,
    onBack: () -> Unit,
) {
    var host by remember { mutableStateOf(prefill?.host ?: "") }
    var port by remember { mutableStateOf((prefill?.httpPort ?: 80).toString()) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }

    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    fun build(): Device = Device(
        id = "$host:${port.toIntOrNull() ?: 80}",
        host = host.trim(),
        port = port.toIntOrNull() ?: 80,
        username = user,
        password = pass,
        useHttps = useHttps,
    )

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
                text = "Connect to your DVR / NVR",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Credentials are stored encrypted on this device and entered once.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            FieldLabel("Host (IP or domain)")
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(200.dp)) {
                    FieldLabel("HTTP port")
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel("HTTPS")
                    Switch(checked = useHttps, onCheckedChange = { useHttps = it })
                }
            }

            FieldLabel("Username")
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Password")
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (connecting) return@Button
                    val device = build()
                    if (!device.isConfigured) {
                        status = "Fill in host, username, and password."
                        statusIsError = true
                        return@Button
                    }
                    connecting = true
                    status = "Connecting..."
                    statusIsError = false
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val infoBody = HikClient.fetchDeviceInfo(device)
                                val model = Regex("<model>(.*?)</model>")
                                    .find(infoBody)?.groupValues?.get(1) ?: ""
                                val client = HikClient.newOkHttp(device)
                                val channels = Isapi.enumerateChannels(device, client)
                                device.copy(model = model) to channels
                            }
                        }
                        connecting = false
                        result.fold(
                            onSuccess = { (connectedDevice, channels) ->
                                if (channels.isEmpty()) {
                                    status = "Connected, but found no channels on this device."
                                    statusIsError = true
                                } else {
                                    onConnected(connectedDevice, channels)
                                }
                            },
                            onFailure = {
                                status = "Failed: ${it.message}"
                                statusIsError = true
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (connecting) "Connecting..." else "Connect") }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status,
                    color = if (statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
}
