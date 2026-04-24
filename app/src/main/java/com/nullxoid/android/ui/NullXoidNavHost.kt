package com.nullxoid.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nullxoid.android.NullXoidApplication
import com.nullxoid.android.ui.auth.LoginScreen
import com.nullxoid.android.ui.chat.ChatListScreen
import com.nullxoid.android.ui.chat.ChatScreen
import com.nullxoid.android.ui.health.HealthScreen
import com.nullxoid.android.ui.settings.SettingsScreen
import com.nullxoid.android.ui.theme.NullXoidTheme

object Routes {
    const val Login = "login"
    const val ChatList = "chats"
    const val Chat = "chat"
    const val Settings = "settings"
    const val Health = "health"
}

@Composable
fun NullXoidApp(app: NullXoidApplication) {
    val vm: NullXoidViewModel = viewModel(
        factory = NullXoidViewModel.Factory(app.repository, app, app.settingsStore)
    )
    val state by vm.state.collectAsState()
    val nav = rememberNavController()

    LaunchedEffect(Unit) { vm.bootstrap() }

    LaunchedEffect(state.auth.authenticated) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (state.auth.authenticated && current == Routes.Login) {
            nav.navigate(Routes.ChatList) {
                popUpTo(Routes.Login) { inclusive = true }
            }
        } else if (!state.auth.authenticated &&
            current != null && current != Routes.Login && current != Routes.Settings) {
            nav.navigate(Routes.Login) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NullXoidTheme {
        Surface(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (state.auth.authenticated) Routes.ChatList else Routes.Login
            ) {
                composable(Routes.Login) {
                    LoginScreen(
                        state = state,
                        onLogin = vm::login,
                        onOpenSettings = { nav.navigate(Routes.Settings) }
                    )
                }
                composable(Routes.ChatList) {
                    ChatListScreen(
                        state = state,
                        onOpenChat = { chat ->
                            vm.openChat(chat)
                            nav.navigate(Routes.Chat)
                        },
                        onNewChat = {
                            vm.startNewChat()
                            nav.navigate(Routes.Chat)
                        },
                        onRefresh = vm::refreshChats,
                        onOpenSettings = { nav.navigate(Routes.Settings) },
                        onOpenHealth = { nav.navigate(Routes.Health) },
                        onLogout = vm::logout
                    )
                }
                composable(Routes.Chat) {
                    ChatScreen(
                        state = state,
                        onBack = {
                            vm.cancelStream()
                            nav.popBackStack()
                        },
                        onSend = vm::sendMessage,
                        onCancel = vm::cancelStream
                    )
                }
                composable(Routes.Settings) {
                    SettingsScreen(
                        state = state,
                        onBack = { nav.popBackStack() },
                        onSave = vm::setBackendUrl,
                        onSelectModel = vm::selectModel,
                        onRefreshModels = vm::refreshModels,
                        onToggleEmbedded = vm::setEmbeddedBackend,
                        onCheckForUpdate = vm::checkForUpdate,
                        onOpenUpdateReleasePage = vm::openUpdateReleasePage,
                        onOpenDirectApkDownload = vm::openDirectApkDownload
                    )
                }
                composable(Routes.Health) {
                    HealthScreen(
                        state = state,
                        onBack = { nav.popBackStack() },
                        onRefresh = vm::refreshHealth
                    )
                }
            }
        }
    }
}
