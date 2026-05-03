package com.nullxoid.android.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.MainBottomNavigation
import com.nullxoid.android.ui.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveArtifact: (String, String) -> Unit,
    onShareArtifact: (String, String) -> Unit,
    onViewArtifact: (StoreArtifactRef) -> Unit,
    onLoadPreview: (StoreArtifactRef) -> Unit,
    onCloseViewer: () -> Unit
) {
    val items = state.storeGalleryAll.ifEmpty { state.storeGallery.items }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("gallery-refresh"),
                        onClick = onRefresh
                    ) { Icon(Icons.Default.Refresh, "Refresh gallery") }
                }
            )
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainTab.Gallery,
                onOpenChats = onOpenChats,
                onOpenStore = onOpenStore,
                onOpenGallery = {},
                onOpenSettings = onOpenSettings
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .testTag("gallery-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Private media", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Generated Store results appear here without exposing backend paths.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "No Store media yet. Generate an image in Creative Workflows first.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                itemsIndexed(items, key = { _, item -> item.artifactId }) { index, item ->
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
                item { Text(state.storeSaveStatus, style = MaterialTheme.typography.bodySmall) }
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
