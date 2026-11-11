package dev.blank.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

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
    val vm: ChatViewModel = viewModel()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Chat) }
    when (screen) {
        Screen.Chat -> ChatScreen(
            vm = vm,
            onSessions = { screen = Screen.Sessions },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Sessions -> SessionsScreen(
            sessions = vm.sessions,
            onBack = { screen = Screen.Chat },
            onOpen = { id ->
                vm.openSession(id)
                screen = Screen.Chat
            },
            onNew = {
                vm.newSession()
                screen = Screen.Chat
            },
        )
        Screen.Settings -> SettingsScreen(config = vm.config, onBack = { screen = Screen.Chat })
    }
}