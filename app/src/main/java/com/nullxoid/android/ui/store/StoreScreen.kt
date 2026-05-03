package com.nullxoid.android.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.ui.AppUiState

private val creativeWorkflowAddonIds = setOf("local-image-studio", "local-video-studio", "local-3d-studio")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRunStoreAddon: (String, String, String, String, String) -> Unit,
    onSaveArtifact: (String, String) -> Unit
) {
    val addons = state.storeCatalog.addons.filter { it.category == "creative-workflows" || it.id in creativeWorkflowAddonIds }
    var selectedAddonId by remember { mutableStateOf("local-image-studio") }
    val addon = addons.firstOrNull { it.id == selectedAddonId } ?: addons.firstOrNull()
    var prompt by remember { mutableStateOf("") }
    var imageSize by remember { mutableStateOf("1024x1024") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("store-refresh"),
                        onClick = onRefresh
                    ) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .testTag("store-screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Creative Workflows",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Approval-gated local creative tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (addons.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Creative Workflows add-ons are unavailable.")
                            if (state.storeLoading) {
                                Spacer(Modifier.height(8.dp))
                                Text("Loading catalog...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                items(addons, key = { it.id }) { item ->
                    StoreAddonCard(
                        addon = item,
                        selected = item.id == addon?.id,
                        onSelect = { selectedAddonId = item.id }
                    )
                }
                item {
                    StoreAddonPanel(
                        addon = addon,
                        prompt = prompt,
                        imageSize = imageSize,
                        loading = state.storeLoading,
                        status = state.storeAction?.status,
                        errorCode = state.storeAction?.errorCode,
                        storeJobId = state.storeAction?.storeJobId ?: state.storeAction?.jobId,
                        onPromptChange = { prompt = it },
                        onImageSizeChange = { imageSize = it },
                        onRun = {
                            addon?.let {
                                onRunStoreAddon(
                                    it.id,
                                    it.approvalRoute?.action.orEmpty(),
                                    it.capabilities.firstOrNull().orEmpty(),
                                    prompt,
                                    imageSize
                                )
                            }
                        }
                    )
                }
                item {
                    Text(
                        "Gallery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (state.storeGallery.items.isEmpty()) {
                    item {
                        Text(
                            "No generated artifacts yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.storeGallery.items, key = { it.artifactId }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    item.artifactId,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(item.mimeType, style = MaterialTheme.typography.labelSmall)
                                Text(item.status, style = MaterialTheme.typography.labelSmall)
                                Button(onClick = { onSaveArtifact(item.artifactId, item.mimeType) }) {
                                    Text("Save to device")
                                }
                            }
                        }
                    }
                }
                if (state.storeSaveStatus.isNotBlank()) {
                    item {
                        Text(state.storeSaveStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreAddonCard(addon: StoreAddon, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-${addon.id}-card"),
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(addon.name, style = MaterialTheme.typography.titleLarge)
            Text(addon.description, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(addon.categoryLabel) })
                AssistChip(onClick = {}, label = { Text(addon.visibility) })
                if (selected) {
                    AssistChip(onClick = {}, label = { Text("Selected") })
                }
            }
            Text(
                "Capability: ${addon.capabilities.firstOrNull().orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoreAddonPanel(
    addon: StoreAddon?,
    prompt: String,
    imageSize: String,
    loading: Boolean,
    status: String?,
    errorCode: String?,
    storeJobId: String?,
    onPromptChange: (String) -> Unit,
    onImageSizeChange: (String) -> Unit,
    onRun: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-${addon?.id ?: "addon"}-detail")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(addon?.name ?: "Creative Workflow", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store-local-image-prompt"),
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Prompt") },
                minLines = 3
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = imageSize,
                onValueChange = onImageSizeChange,
                label = { Text(if (addon?.id == "local-3d-studio") "Format or size" else "Image/video size") },
                singleLine = true
            )
            Button(
                modifier = Modifier.testTag("store-${addon?.id ?: "addon"}-generate"),
                enabled = !loading && addon != null,
                onClick = onRun
            ) {
                Text(if (loading) "Waiting..." else "Generate with approval")
            }
            val statusText = when {
                errorCode != null -> "Result: $errorCode"
                status != null -> "Result: $status"
                else -> "Result: ready"
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            if (!storeJobId.isNullOrBlank()) {
                Text("Job: $storeJobId", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status.orEmpty() in setOf("pending_approval", "approved", "queued_connector", "running_provider", "uploading_artifact")) {
                Text("Keep this screen open or return after approving in NullBridge; NullXoid will resume polling.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
