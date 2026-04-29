package com.nullxoid.android.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nullxoid.android.NullXoidApplication
import com.nullxoid.android.ui.auth.LoginScreen
import com.nullxoid.android.ui.chat.ChatListScreen
import com.nullxoid.android.ui.chat.ChatScreen
import com.nullxoid.android.ui.health.HealthScreen
import com.nullxoid.android.ui.onboarding.OnboardingScreen
import com.nullxoid.android.ui.settings.SettingsScreen
import com.nullxoid.android.ui.theme.NullXoidTheme

object Routes {
    const val Onboarding = "onboarding"
    const val Login = "login"
    const val ChatList = "chats"
    const val Chat = "chat"
    const val Settings = "settings"
    const val Health = "health"
}

@Composable
fun NullXoidApp(
    app: NullXoidApplication,
    oidcRedirect: Uri? = null,
    onOidcRedirectConsumed: () -> Unit = {}
) {
    val vm: NullXoidViewModel = viewModel(
        factory = NullXoidViewModel.Factory(app.repository, app, app.settingsStore)
    )
    val state by vm.state.collectAsState()
    val nav = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.bootstrap() }

    LaunchedEffect(oidcRedirect) {
        if (oidcRedirect != null) {
            vm.completeOidcSignIn(oidcRedirect)
            onOidcRedirectConsumed()
        }
    }

    LaunchedEffect(state.auth.authenticated, state.onboardingCompleted) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (state.auth.authenticated && current == Routes.Login) {
            nav.navigate(if (state.onboardingCompleted) Routes.ChatList else Routes.Onboarding) {
                popUpTo(Routes.Login) { inclusive = true }
            }
        } else if (!state.auth.authenticated &&
            current != null &&
            current != Routes.Login &&
            current != Routes.Settings &&
            current != Routes.Onboarding) {
            nav.navigate(Routes.Login) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(state.onboardingCompleted, state.auth.authenticated) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (state.onboardingCompleted && current == Routes.Onboarding) {
            nav.navigate(if (state.auth.authenticated) Routes.ChatList else Routes.Login) {
                popUpTo(Routes.Onboarding) { inclusive = true }
            }
        }
    }

    NullXoidTheme {
        Surface(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = Routes.Onboarding
            ) {
                composable(Routes.Onboarding) {
                    OnboardingScreen(
                        state = state,
                        onSaveBackend = vm::setBackendUrl,
                        onCheckBackend = vm::refreshHealth,
                        onOpenLogin = { nav.navigate(Routes.Login) },
                        onPasskeySignIn = { vm.loginWithPasskey(context) },
                        onRegisterPasskey = { vm.registerPasskey(context) },
                        onRefreshPasskeys = vm::refreshPasskeys,
                        onSelectUpdateSource = vm::setUpdateSource,
                        onCheckForUpdate = vm::checkForUpdate,
                        onOpenUpdateReleasePage = vm::openUpdateReleasePage,
                        onInstallUpdate = vm::installLatestUpdate,
                        onFinish = vm::finishOnboarding
                    )
                }
                composable(Routes.Login) {
                    LoginScreen(
                        state = state,
                        onLogin = vm::login,
                        onOpenSettings = { nav.navigate(Routes.Settings) },
                        onPasskeySignIn = { vm.loginWithPasskey(context) },
                        onOidcSetup = vm::startOidcSignIn
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
                        onCancel = vm::cancelStream,
                        onRefreshModels = vm::refreshModels,
                        onOpenSettings = { nav.navigate(Routes.Settings) }
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
                        onSelectEmbeddedEngine = vm::setEmbeddedEngine,
                        onSaveOllamaSettings = vm::setOllamaSettings,
                        onSelectUpdateSource = vm::setUpdateSource,
                        onCheckForUpdate = vm::checkForUpdate,
                        onOpenUpdateReleasePage = vm::openUpdateReleasePage,
                        onInstallUpdate = vm::installLatestUpdate,
                        onRefreshPasskeys = vm::refreshPasskeys,
                        onRegisterPasskey = { vm.registerPasskey(context) },
                        onRevokePasskey = vm::revokePasskey,
                        onRunOnboarding = {
                            vm.resetOnboarding()
                            nav.navigate(Routes.Onboarding)
                        }
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
