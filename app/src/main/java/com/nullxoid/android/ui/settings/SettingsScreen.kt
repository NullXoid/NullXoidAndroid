package com.nullxoid.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    onCheckForUpdate: () -> Unit,
    onOpenUpdateReleasePage: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    var urlDraft by remember(state.backendUrl) { mutableStateOf(state.backendUrl) }
    var providerUrlDraft by remember(state.ollamaUrl) { mutableStateOf(state.ollamaUrl) }
    var providerModelDraft by remember(state.ollamaModel) { mutableStateOf(state.ollamaModel) }
    val selectedProviderName = when (state.embeddedEngine) {
        SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP -> "llama.cpp"
        SettingsStore.EMBEDDED_ENGINE_OLLAMA -> "Ollama"
        else -> "Echo"
    }
    val usesExternalProvider = state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_OLLAMA ||
        state.embeddedEngine == SettingsStore.EMBEDDED_ENGINE_LLAMA_CPP

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
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            Text("On-device backend", style = MaterialTheme.typography.titleMedium)
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
                Button(onClick = { onSaveOllamaSettings(providerUrlDraft, providerModelDraft) }) {
                    Text("Save $selectedProviderName")
                }
            }

            Spacer(Modifier.height(24.dp))
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
                "Use Hosted API for out-of-network phones, or Local/Embedded for development.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { urlDraft = SettingsStore.PUBLIC_BACKEND_URL },
                    label = { Text("Hosted API") }
                )
                AssistChip(
                    onClick = { urlDraft = SettingsStore.DEFAULT_BACKEND_URL },
                    label = { Text("Local") }
                )
                AssistChip(
                    onClick = { urlDraft = SettingsStore.EMBEDDED_BACKEND_URL },
                    label = { Text("Embedded") }
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.testTag("settings-save-backend-url"),
                onClick = { onSave(urlDraft.trim()) }
            ) { Text("Save") }

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
        }
    }
}
