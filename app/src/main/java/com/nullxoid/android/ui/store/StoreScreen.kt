package com.nullxoid.android.ui.store

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.MainBottomNavigation
import com.nullxoid.android.ui.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAddon: (String) -> Unit,
    onRunStoreAddon: (String, String, String, String, String, String, Int, String) -> Unit,
    onSaveArtifact: (String, String) -> Unit,
    onShareArtifact: (String, String) -> Unit,
    onViewArtifact: (StoreArtifactRef) -> Unit,
    onLoadPreview: (StoreArtifactRef) -> Unit,
    onCloseViewer: () -> Unit,
    onResumeStoreJob: () -> Unit
) {
    val addons = state.storeCatalog.addons
        .filter { it.category == "creative-workflows" || it.id in creativeWorkflowAddonIds }
        .sortedBy {
            when (it.id) {
                "local-image-studio" -> 0
                "local-video-studio" -> 1
                "local-3d-studio" -> 2
                else -> 10
            }
        }
    var selectedAddonId by remember { mutableStateOf("local-image-studio") }
    LaunchedEffect(state.activeStoreAddonId, addons.size) {
        val active = state.activeStoreAddonId.takeIf { id -> addons.any { it.id == id } }
        if (!active.isNullOrBlank()) selectedAddonId = active
    }
    val selectedAddon = addons.firstOrNull { it.id == selectedAddonId } ?: addons.firstOrNull()
    LaunchedEffect(selectedAddon?.id) {
        selectedAddon?.id?.let(onSelectAddon)
    }
    LaunchedEffect(state.activeStoreJobId) {
        if (state.activeStoreJobId.isNotBlank()) onResumeStoreJob()
    }

    var prompt by remember { mutableStateOf("") }
    val profiles = profileOptions(selectedAddon)
    var selectedProfileId by remember(selectedAddon?.id, profiles.size) {
        mutableStateOf(profiles.firstOrNull()?.id.orEmpty())
    }
    val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    var sizeDraft by remember(selectedAddon?.id, profile?.id) {
        mutableStateOf(
            when (mediaKindForAddon(selectedAddon?.id.orEmpty())) {
                "video" -> profile?.videoSize ?: "1024x1024"
                else -> profile?.imageSize ?: "1024x1024"
            }
        )
    }
    val mediaKind = mediaKindForAddon(selectedAddon?.id.orEmpty())

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
                    ) { Icon(Icons.Default.Refresh, "Refresh Store") }
                }
            )
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainTab.Store,
                onOpenChats = onOpenChats,
                onOpenStore = {},
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .testTag("store-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Creative Workflows",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Choose a media type, request approval, then view the private result.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (addons.isEmpty()) {
                item { EmptyStoreCard(state.storeLoading) }
            } else {
                item {
                    Text("1. Choose", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StoreMediaSelector(
                        addons = addons,
                        selectedAddonId = selectedAddon?.id.orEmpty(),
                        onSelect = { option ->
                            selectedAddonId = option.addonId
                            selectedProfileId = ""
                            onSelectAddon(option.addonId)
                        }
                    )
                }
                item {
                    StoreAddonPanel(
                        addon = selectedAddon,
                        mediaKind = mediaKind,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId.ifBlank { profiles.firstOrNull()?.id.orEmpty() },
                        prompt = prompt,
                        sizeDraft = sizeDraft,
                        loading = state.storeLoading,
                        onSelectProfile = { selectedProfileId = it },
                        onPromptChange = { prompt = it },
                        onSizeChange = { sizeDraft = it },
                        onRun = {
                            val selectedProfile = profiles.firstOrNull {
                                it.id == selectedProfileId.ifBlank { profiles.firstOrNull()?.id.orEmpty() }
                            } ?: profiles.firstOrNull()
                            selectedAddon?.let {
                                onRunStoreAddon(
                                    it.id,
                                    it.approvalRoute?.action.orEmpty(),
                                    it.capabilities.firstOrNull().orEmpty(),
                                    prompt,
                                    sizeDraft,
                                    selectedProfile?.id.orEmpty(),
                                    selectedProfile?.durationMs ?: 0,
                                    selectedProfile?.format.orEmpty()
                                )
                            }
                        }
                    )
                }
                item {
                    StoreJobStatusCard(state)
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
                            "No ${selectedAddon?.name ?: "Store"} artifacts yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    itemsIndexed(state.storeGallery.items, key = { _, item -> item.artifactId }) { index, item ->
                        StoreGalleryCard(
                            item = item,
                            title = safeArtifactTitle(item, index),
                            previewBytes = state.storePreviewBytes[item.artifactId],
                            onLoadPreview = onLoadPreview,
                            onView = { onViewArtifact(item) },
                            onSave = { onSaveArtifact(item.artifactId, item.mimeType) },
                            onShare = { onShareArtifact(item.artifactId, item.mimeType) }
                        )
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

    state.storeViewerArtifact?.let { artifact ->
        StoreMediaViewer(
            artifact = artifact,
            bytes = state.storeViewerBytes,
            loading = state.storeViewerLoading,
            error = state.storeViewerError,
            onClose = onCloseViewer,
            onSave = { onSaveArtifact(artifact.artifactId, artifact.mimeType) },
            onShare = { onShareArtifact(artifact.artifactId, artifact.mimeType) }
        )
    }
}

@Composable
private fun EmptyStoreCard(loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Creative Workflows add-ons are unavailable.")
            if (loading) {
                Spacer(Modifier.height(8.dp))
                Text("Loading catalog...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StoreMediaSelector(
    addons: List<StoreAddon>,
    selectedAddonId: String,
    onSelect: (StoreMediaOption) -> Unit
) {
    val options = mediaOptions(addons, selectedAddonId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store-media-${option.mediaKind}"),
                onClick = { onSelect(option) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = { onSelect(option) },
                        label = { Text(if (option.selected) "Selected" else "Choose") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreAddonPanel(
    addon: StoreAddon?,
    mediaKind: String,
    profiles: List<StoreProfileOption>,
    selectedProfileId: String,
    prompt: String,
    sizeDraft: String,
    loading: Boolean,
    onSelectProfile: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSizeChange: (String) -> Unit,
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
            Text("2. Configure", style = MaterialTheme.typography.titleMedium)
            Text(addon?.name ?: "Creative Workflow", style = MaterialTheme.typography.headlineSmall)
            Text(
                addon?.description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Job type", style = MaterialTheme.typography.titleSmall)
            profiles.forEach { profile ->
                FilterChip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store-job-type-${profile.id}"),
                    selected = profile.id == selectedProfileId,
                    onClick = { onSelectProfile(profile.id) },
                    label = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(profile.label)
                            if (profile.description.isNotBlank()) {
                                Text(
                                    profile.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store-prompt"),
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Prompt") },
                minLines = 3
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = sizeDraft,
                onValueChange = onSizeChange,
                label = { Text(if (mediaKind == "video") "Video size" else "Image size") },
                singleLine = true
            )
            val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mediaKind == "video") {
                    AssistChip(
                        onClick = {},
                        label = { Text("${((selectedProfile?.durationMs ?: 0) / 1000).coerceAtLeast(1)} seconds") }
                    )
                }
                if (mediaKind == "3d") {
                    AssistChip(
                        onClick = {},
                        label = { Text("Format: ${(selectedProfile?.format ?: "glb").uppercase()}") }
                    )
                }
                AssistChip(onClick = {}, label = { Text("Approval required") })
            }
            Button(
                modifier = Modifier
                    .height(52.dp)
                    .testTag("store-${addon?.id ?: "addon"}-generate"),
                enabled = !loading && addon != null,
                onClick = onRun
            ) {
                Text(if (loading) "Checking job..." else "Generate with approval")
            }
        }
    }
}

@Composable
private fun StoreJobStatusCard(state: AppUiState) {
    val action = state.storeAction
    val status = friendlyStoreStatus(action?.status, action?.errorCode)
    var showDetails by remember(action?.storeJobId, action?.status, action?.errorCode) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-job-status-card")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Job status", style = MaterialTheme.typography.labelMedium)
                    Text(status.label, style = MaterialTheme.typography.titleMedium)
                }
                AssistChip(onClick = {}, label = { Text("${(status.progress * 100).toInt()}%") })
            }
            LinearProgressIndicator(
                progress = status.progress,
                modifier = Modifier.fillMaxWidth()
            )
            Text(status.helper, style = MaterialTheme.typography.bodySmall)
            if (action?.status == "pending_approval") {
                Text(
                    "Return here after approving in NullBridge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide details" else "Details")
            }
            if (showDetails) {
                Text("Status: ${action?.status ?: "ready"}", style = MaterialTheme.typography.labelSmall)
                action?.errorCode?.let { Text("Code: $it", style = MaterialTheme.typography.labelSmall) }
                (action?.storeJobId ?: action?.jobId)?.takeIf { it.isNotBlank() }?.let {
                    Text("Job: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                action?.requestId?.takeIf { it.isNotBlank() }?.let {
                    Text("Approval: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun StoreGalleryCard(
    item: StoreArtifactRef,
    title: String,
    previewBytes: ByteArray?,
    onLoadPreview: (StoreArtifactRef) -> Unit,
    onView: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    LaunchedEffect(item.artifactId, item.thumbnailUrl, item.posterUrl, item.previewUrl) {
        onLoadPreview(item)
    }
    var showDetails by remember(item.artifactId) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-gallery-card")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StorePreviewBox(item = item, bytes = previewBytes)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(artifactTypeLabel(item)) })
                AssistChip(onClick = {}, label = { Text(item.status.ifBlank { "ready" }) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onView) { Text("View") }
                OutlinedButton(onClick = onSave) { Text("Save to device") }
                OutlinedButton(onClick = onShare) { Text("Share") }
            }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide details" else "Details")
            }
            if (showDetails) {
                Text("Artifact: ${item.artifactId}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("MIME: ${item.mimeType}", style = MaterialTheme.typography.labelSmall)
                if (item.createdAt.isNotBlank()) {
                    Text("Created: ${item.createdAt}", style = MaterialTheme.typography.labelSmall)
                }
                if (item.format.isNotBlank()) {
                    Text("Format: ${item.format}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StorePreviewBox(item: StoreArtifactRef, bytes: ByteArray?) {
    val imageBitmap = remember(bytes) {
        bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap() }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "${artifactTypeLabel(item)} preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(artifactTypeLabel(item), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StoreMediaViewer(
    artifact: StoreArtifactRef,
    bytes: ByteArray,
    loading: Boolean,
    error: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(artifactTypeLabel(artifact), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close viewer", tint = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> Text("Opening...", color = Color.White)
                        error.isNotBlank() -> Text(error, color = Color.White)
                        artifact.mimeType.startsWith("image/") && bytes.isNotEmpty() -> ZoomableImage(bytes)
                        artifact.mimeType.startsWith("video/") ->
                            Text("Video is ready. Use Save or Share to open it with a player.", color = Color.White)
                        else -> Text("Preview is not available yet.", color = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSave) { Text("Save to device") }
                    OutlinedButton(onClick = onShare) { Text("Share") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ZoomableImage(bytes: ByteArray) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap = bitmap,
        contentDescription = "Generated image preview",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset += pan
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentScale = ContentScale.Fit
    )
}
