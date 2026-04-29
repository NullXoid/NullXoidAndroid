package com.nullxoid.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.prefs.SettingsStore
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.availableUpdateSources

internal fun onboardingAccountStatus(authenticated: Boolean, passkeyCount: Int): String =
    when {
        !authenticated -> "Password sign-in comes first on a new phone. After that, Android can save a NullXoid passkey here."
        passkeyCount > 0 -> "This phone has a NullXoid passkey path ready. Sign out once and use passkey sign-in to verify it."
        else -> "Signed in. Add a passkey now so this phone can sign in without the password next time."
    }

internal fun onboardingUpdateStatus(source: String): String =
    when (SettingsStore.normalizeUpdateSource(source)) {
        SettingsStore.UPDATE_SOURCE_FORGEJO -> "Forgejo only. Use this when testing EchoLabs internal releases."
        SettingsStore.UPDATE_SOURCE_GITHUB -> "GitHub only. Use this when testing the public mirror."
        else -> "Auto checks Forgejo first, then GitHub."
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: AppUiState,
    onSaveBackend: (String) -> Unit,
    onCheckBackend: () -> Unit,
    onOpenLogin: () -> Unit,
    onPasskeySignIn: () -> Unit,
    onRegisterPasskey: () -> Unit,
    onRefreshPasskeys: () -> Unit,
    onSelectUpdateSource: (String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenUpdateReleasePage: () -> Unit,
    onInstallUpdate: () -> Unit,
    onFinish: () -> Unit
) {
    var urlDraft by remember(state.backendUrl) { mutableStateOf(state.backendUrl) }
    val backendChanged = urlDraft.trim() != state.backendUrl.trim()
    val backendLooksPublic = urlDraft.trim() == SettingsStore.PUBLIC_BACKEND_URL
    val passkeyCount = state.passkeyCredentials.size
    val updateInfo = state.updateInfo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NullXoid setup") },
                actions = {
                    TextButton(
                        modifier = Modifier.testTag("onboarding-finish-top"),
                        onClick = onFinish
                    ) { Text("Finish later") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(20.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "Set the phone up in the order Android expects: backend, sign in, passkey, updates.",
                style = MaterialTheme.typography.bodyMedium
            )

            SetupSection(number = "1", title = "Backend") {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding-backend-url")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = backendLooksPublic,
                        onClick = { urlDraft = SettingsStore.PUBLIC_BACKEND_URL },
                        label = { Text("Hosted API") }
                    )
                    FilterChip(
                        selected = urlDraft.trim() == SettingsStore.LOCAL_BACKEND_URL,
                        onClick = { urlDraft = SettingsStore.LOCAL_BACKEND_URL },
                        label = { Text("Local") }
                    )
                    FilterChip(
                        selected = urlDraft.trim() == SettingsStore.EMBEDDED_BACKEND_URL,
                        onClick = { urlDraft = SettingsStore.EMBEDDED_BACKEND_URL },
                        label = { Text("Embedded") }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.testTag("onboarding-save-backend"),
                        onClick = { onSaveBackend(urlDraft.trim()) },
                        enabled = backendChanged
                    ) { Text("Save") }
                    OutlinedButton(
                        modifier = Modifier.testTag("onboarding-check-backend"),
                        onClick = onCheckBackend
                    ) { Text("Check") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.health != null) "Backend answered. Hosted API is the normal phone path."
                    else "Hosted API should be used for passkeys and out-of-network phones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.health != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SetupSection(number = "2", title = "Account") {
                Text(
                    onboardingAccountStatus(state.auth.authenticated, passkeyCount),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                if (state.auth.authenticated) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.testTag("onboarding-add-passkey"),
                            enabled = !state.passkeyLoading,
                            onClick = onRegisterPasskey
                        ) { Text(if (state.passkeyLoading) "Working" else "Add passkey") }
                        OutlinedButton(onClick = onRefreshPasskeys) { Text("Refresh") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.testTag("onboarding-open-login"),
                            onClick = onOpenLogin
                        ) { Text("Password sign in") }
                        OutlinedButton(
                            modifier = Modifier.testTag("onboarding-passkey-login"),
                            onClick = onPasskeySignIn
                        ) { Text("Use passkey") }
                    }
                }
                state.notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            SetupSection(number = "3", title = "Updates") {
                val updateSources = availableUpdateSources(state)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (SettingsStore.UPDATE_SOURCE_AUTO in updateSources) {
                        FilterChip(
                            selected = state.updateSource == SettingsStore.UPDATE_SOURCE_AUTO,
                            onClick = { onSelectUpdateSource(SettingsStore.UPDATE_SOURCE_AUTO) },
                            label = { Text("Auto") }
                        )
                    }
                    if (SettingsStore.UPDATE_SOURCE_FORGEJO in updateSources) {
                        FilterChip(
                            selected = state.updateSource == SettingsStore.UPDATE_SOURCE_FORGEJO,
                            onClick = { onSelectUpdateSource(SettingsStore.UPDATE_SOURCE_FORGEJO) },
                            label = { Text("Forgejo") }
                        )
                    }
                    if (SettingsStore.UPDATE_SOURCE_GITHUB in updateSources) {
                        FilterChip(
                            selected = state.updateSource == SettingsStore.UPDATE_SOURCE_GITHUB,
                            onClick = { onSelectUpdateSource(SettingsStore.UPDATE_SOURCE_GITHUB) },
                            label = { Text("GitHub") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(onboardingUpdateStatus(state.updateSource), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                updateInfo?.let {
                    Text(
                        "Installed ${state.currentAppVersionName} (${state.currentAppVersionCode}); latest ${it.latestReleaseName} from ${it.releaseSource}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCheckForUpdate,
                        enabled = !state.checkingUpdate && !state.installingUpdate
                    ) { Text(if (state.checkingUpdate) "Checking" else "Check") }
                    OutlinedButton(onClick = onOpenUpdateReleasePage) { Text("Release") }
                    OutlinedButton(
                        onClick = onInstallUpdate,
                        enabled = updateInfo?.updateAvailable == true &&
                            updateInfo.apkDownloadUrl != null &&
                            !state.installingUpdate
                    ) { Text(if (state.installingUpdate) "Installing" else "Install") }
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding-finish"),
                onClick = onFinish
            ) {
                Text(if (state.auth.authenticated) "Finish setup" else "Finish after sign-in")
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun SetupSection(
    number: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$number. $title", style = MaterialTheme.typography.titleMedium)
            AssistChip(onClick = {}, enabled = false, label = { Text(title) })
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}
