package dev.blank.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Chat : Screen
    data object Sessions : Screen
    data object Settings : Screen
}

val ScreenSaver = Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            Screen.Chat -> "chat"
            Screen.Sessions -> "sessions"
            Screen.Settings -> "settings"
        }
    },
    restore = { name ->
        when (name) {
            "chat" -> Screen.Chat
            "sessions" -> Screen.Sessions
            "settings" -> Screen.Settings
            else -> null
        }
    },
)

@Composable
fun AppRoot() {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Chat) }
    when (screen) {
        Screen.Chat -> ChatScreen(
            onSessions = { screen = Screen.Sessions },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Sessions -> SessionsScreen(onBack = { screen = Screen.Chat })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Chat })
    }
}