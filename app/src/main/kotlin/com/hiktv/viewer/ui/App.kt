package com.hiktv.viewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hiktv.viewer.data.HikClient
import com.hiktv.viewer.data.SecureStore
import com.hiktv.viewer.ui.theme.HikTvTheme

private sealed interface Screen {
    data object Setup : Screen
    data object Grid : Screen
    data class Live(val channel: Int) : Screen
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    val client = remember(settings) { HikClient.newOkHttp(settings) }

    var screen: Screen by remember {
        mutableStateOf(if (settings.isConfigured) Screen.Grid else Screen.Setup)
    }

    HikTvTheme {
        when (val s = screen) {
            is Screen.Setup -> SetupScreen(
                initial = settings,
                onSaved = { saved ->
                    store.save(saved)
                    settings = saved
                    screen = Screen.Grid
                },
            )
            is Screen.Grid -> GridScreen(
                settings = settings,
                client = client,
                onPickChannel = { ch -> screen = Screen.Live(ch) },
                onOpenSettings = { screen = Screen.Setup },
            )
            is Screen.Live -> LiveScreen(
                settings = settings,
                client = client,
                initialChannel = s.channel,
                onClose = { screen = Screen.Grid },
            )
        }
    }
}
