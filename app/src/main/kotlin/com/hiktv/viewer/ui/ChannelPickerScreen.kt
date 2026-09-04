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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hiktv.viewer.data.Channel

/**
 * Lists the channels actually enumerated from the DVR (real names, codec and
 * online state) with a checkbox each, replacing the old free-text CSV field.
 * Offline channels start unchecked since there is nothing to show for them.
 */
@Composable
fun ChannelPickerScreen(
    channels: List<Channel>,
    onConfirm: (List<Channel>) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember(channels) {
        mutableStateOf(channels.filter { it.online }.map { it.id }.toSet())
    }
    BackHandler(onBack = onBack)

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
                text = "Choose cameras",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${selected.size} of ${channels.size} selected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            channels.sortedBy { it.id }.forEach { ch ->
                Button(
                    onClick = {
                        selected = if (ch.id in selected) selected - ch.id else selected + ch.id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = ch.id in selected, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(ch.name)
                            Text(describeChannel(ch), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onConfirm(channels.filter { it.id in selected }) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}

private fun describeChannel(ch: Channel): String {
    val status = if (ch.online) "Online" else "No signal"
    val codec = ch.mainCodec.ifBlank { "unknown codec" }
    val audio = if (ch.hasAudio) "audio" else "no audio"
    return "$status · $codec · $audio"
}
