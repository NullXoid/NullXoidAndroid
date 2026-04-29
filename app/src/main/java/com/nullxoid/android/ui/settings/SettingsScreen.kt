package com.nullxoid.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.prefs.SettingsStore
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.availableUpdateSources
import com.nullxoid.android.ui.passkeyEnrollmentStatusText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onToggleEmbedded: (Boolean) -> Unit,
    onSelectEmbeddedEngine: (String) -> Unit,
    onSaveOllamaSettings: (String, String) -> Unit,
    onSelectUpdateSource: (String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenUpdateReleasePage: () -> Unit,
    onInstallUpdate: () -> Unit,
    onRefreshPasskeys: () -> Unit,
    onRegisterPasskey: () -> Unit,
    onRevokePasskey: (String) -> Unit,
    onImportSavedChatRecovery: (String, String) -> Unit,
    onRunOnboarding: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var urlDraft by remember(state.backendUrl) { mutableStateOf(state.backendUrl) }
    var providerUrlDraft by remember(state.ollamaUrl) { mutableStateOf(state.ollamaUrl) }
    var providerModelDraft by remember(state.ollamaModel) { mutableStateOf(state.ollamaModel) }
    var recoverySecretDraft by remember { mutableStateOf("") }
    var recoveryEnvelopeDraft by remember { mutableStateOf("") }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotBlank() }?.let { scanned ->
            recoveryEnvelopeDraft = scanned
            recoverySecretDraft = ""
        }
    }
    val selectedProviderName = when (state.embeddedEngine) {
        SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP -> "llama.cpp"
        SettingsStore.EMBEDDED_ENGINE_OLLAMA -> "Ollama"
        else -> "Echo"
    }
    val usesExternalProvider = state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_OLLAMA ||
        state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP
    val backendUrlChanged = urlDraft.trim() != state.backendUrl.trim()
    val providerSettingsChanged = providerUrlDraft.trim() != state.ollamaUrl.trim() ||
        providerModelDraft.trim() != state.ollamaModel.trim()

    LaunchedEffect(state.auth.authenticated, state.backendUrl) {
        if (state.auth.authenticated) onRefreshPasskeys()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.testTag("settings-back"),
                        onClick = onBack
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
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
                .fillMaxSize()
        ) {
            Text("Remote backend", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-backend-url")
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use Hosted API for normal phone use. Local and Embedded are developer options.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = urlDraft.trim() == SettingsStore.PUBLIC_BACKEND_URL,
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
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.testTag("settings-save-backend-url"),
                onClick = { onSave(urlDraft.trim()) },
                enabled = backendUrlChanged
            ) { Text("Save") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.testTag("settings-run-onboarding"),
                onClick = onRunOnboarding
            ) { Text("Run guided setup") }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Account security", style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = onRefreshPasskeys, label = { Text("Refresh") })
            }
            Spacer(Modifier.height(8.dp))
            val provider = state.passkeyProvider
            Text(
                passkeyEnrollmentStatusText(
                    authenticated = state.auth.authenticated,
                    provider = provider,
                    credentialCount = state.passkeyCredentials.size
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.testTag("settings-passkey-add"),
                onClick = onRegisterPasskey,
                enabled = state.auth.authenticated &&
                    provider?.registrationEnabled == true &&
                    !state.passkeyLoading
            ) {
                Text(if (state.passkeyLoading) "Working" else "Add passkey")
            }
            state.notice?.let { notice ->
                Spacer(Modifier.height(8.dp))
                Text(
                    notice,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.passkeyCredentials.isEmpty()) {
                Text("No passkeys enrolled.", style = MaterialTheme.typography.bodySmall)
            } else {
                state.passkeyCredentials.forEach { credential ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                credential.rpId.ifBlank { provider?.rpId ?: "Passkey" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                credential.credentialId.take(18),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.testTag("settings-passkey-remove"),
                            enabled = !state.passkeyLoading,
                            onClick = { onRevokePasskey(credential.credentialId) }
                        ) {
                            Text("Remove")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Saved-chat recovery", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "If you do not remember a recovery secret, use the web app: Settings > Privacy/Security > Reset browser device setup, then Copy Android import kit. Scan the QR or paste the full JSON below and leave the optional secret field blank. The fresh JSON says \"kit_type\":\"one_paste_android_import\".",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings-recovery-scan-qr"),
                    enabled = !state.passkeyLoading,
                    onClick = {
                        qrScanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan NullXoid Android import kit")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
                    }
                ) {
                    Text("Scan QR")
                }
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings-recovery-paste-clipboard"),
                    enabled = !state.passkeyLoading,
                    onClick = {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotBlank() }?.let { copied ->
                            recoveryEnvelopeDraft = copied
                            recoverySecretDraft = ""
                        }
                    }
                ) {
                    Text("Paste clipboard")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = recoverySecretDraft,
                onValueChange = { recoverySecretDraft = it },
                label = { Text("Recovery secret (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-recovery-secret")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = recoveryEnvelopeDraft,
                onValueChange = { recoveryEnvelopeDraft = it },
                label = { Text("Android import kit JSON (one-paste)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-recovery-envelope")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.testTag("settings-recovery-import"),
                enabled = state.auth.authenticated &&
                    recoveryEnvelopeDraft.isNotBlank() &&
                    !state.passkeyLoading,
                onClick = { onImportSavedChatRecovery(recoverySecretDraft, recoveryEnvelopeDraft) }
            ) {
                Text(if (state.passkeyLoading) "Importing" else "Import Android kit")
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Model", style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = onRefreshModels, label = { Text("Refresh") })
            }
            Spacer(Modifier.height(8.dp))
            if (state.models.isEmpty()) {
                Text("No models loaded yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                state.models.forEach { model ->
                    FilterChip(
                        selected = state.selectedModel == model.id,
                        onClick = { onSelectModel(model.id) },
                        label = { Text(model.name ?: model.id) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("App update", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Release channel", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
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
            Text(
                when (state.updateSource) {
                    SettingsStore.UPDATE_SOURCE_FORGEJO -> "Use EchoLabs Forgejo releases only."
                    SettingsStore.UPDATE_SOURCE_GITHUB -> "Use the public GitHub mirror only."
                    else -> "Try EchoLabs Forgejo first, then GitHub mirror."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            val updateInfo = state.updateInfo
            Text(
                "Installed: ${state.currentAppVersionName} (${state.currentAppVersionCode})",
                style = MaterialTheme.typography.bodySmall
            )
            updateInfo?.let { info ->
                Spacer(Modifier.height(4.dp))
                Text("Source: ${info.releaseSource}", style = MaterialTheme.typography.bodySmall)
                Text("Latest: ${info.latestReleaseName}", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (info.updateAvailable) "Update available" else "Already current",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (info.updateAvailable)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            Spacer(Modifier.height(32.dp))
            Text("Advanced backend", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Run backend inside this app", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (state.embeddedEnabled)
                            "Running on 127.0.0.1:8090 - $selectedProviderName provider"
                        else
                            "Use a remote NullXoid backend via the Base URL below",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    modifier = Modifier.testTag("settings-embedded-switch"),
                    checked = state.embeddedEnabled,
                    onCheckedChange = onToggleEmbedded
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Provider", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_ECHO,
                    onClick = { onSelectEmbeddedEngine(SettingsStore.EMBEDDED_ENGINE_ECHO) },
                    label = { Text("Echo") }
                )
                FilterChip(
                    selected = state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_OLLAMA,
                    onClick = { onSelectEmbeddedEngine(SettingsStore.EMBEDDED_ENGINE_OLLAMA) },
                    label = { Text("Ollama") }
                )
                FilterChip(
                    selected = state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP,
                    onClick = { onSelectEmbeddedEngine(SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP) },
                    label = { Text("llama.cpp") }
                )
            }

            if (usesExternalProvider) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP)
                        "Use this for Termux llama-server, usually http://127.0.0.1:8080."
                    else
                        "Use this for Ollama, usually http://127.0.0.1:11434.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = providerUrlDraft,
                    onValueChange = { providerUrlDraft = it },
                    label = { Text("$selectedProviderName URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = providerModelDraft,
                    onValueChange = { providerModelDraft = it },
                    label = { Text("$selectedProviderName model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSaveOllamaSettings(providerUrlDraft, providerModelDraft) },
                    enabled = providerSettingsChanged
                ) {
                    Text("Save $selectedProviderName")
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
