package com.hiktv.viewer.ui

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
import com.hiktv.viewer.data.HikClient
import com.hiktv.viewer.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(
    initial: Settings,
    onSaved: (Settings) -> Unit,
) {
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    var channels by remember {
        mutableStateOf(initial.channels.joinToString(",").ifBlank { "1,2,3,4" })
    }
    var useHttps by remember { mutableStateOf(initial.useHttps) }

    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun build(): Settings = Settings(
        host = host.trim(),
        port = port.toIntOrNull() ?: 80,
        rtspPort = 554,
        username = user,
        password = pass,
        useHttps = useHttps,
        channels = channels.split(",").mapNotNull { it.trim().toIntOrNull() },
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
                text = "Hik TV Viewer",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Enter your Hikvision DVR / NVR connection details. Stored encrypted on this device.",
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

            FieldLabel("Channels (comma-separated, e.g. 1,2,3,4)")
            OutlinedTextField(
                value = channels,
                onValueChange = { channels = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (testing) return@Button
                        val s = build()
                        if (!s.isConfigured) {
                            status = "Fill in host, username, and password."
                            statusIsError = true
                            return@Button
                        }
                        testing = true
                        status = "Testing…"
                        statusIsError = false
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { HikClient.fetchDeviceInfo(s) }
                            }
                            testing = false
                            result.fold(
                                onSuccess = { body ->
                                    val model = Regex("<model>(.*?)</model>")
                                        .find(body)?.groupValues?.get(1) ?: "device"
                                    status = "Connected to $model"
                                    statusIsError = false
                                },
                                onFailure = {
                                    status = "Failed: ${it.message}"
                                    statusIsError = true
                                },
                            )
                        }
                    },
                ) { Text(if (testing) "Testing…" else "Test connection") }

                Button(
                    onClick = {
                        val s = build()
                        if (!s.isConfigured) {
                            status = "Fill in host, username, and password."
                            statusIsError = true
                        } else {
                            onSaved(s)
                        }
                    },
                ) { Text("Save & continue") }
            }

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
