package com.nullxoid.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.prefs.SettingsStore
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.MainBottomNavigation
import com.nullxoid.android.ui.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppUiState,
    onCreateImage: () -> Unit,
    onCreateVideo: () -> Unit,
    onCreate3d: () -> Unit,
    onAskEchoLabs: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("EchoLabs") })
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainTab.Home,
                onOpenHome = {},
                onOpenCreate = onCreateImage,
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .testTag("home-screen"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "EchoLabs",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Create, ask, and keep your private media in one place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                StatusPills(state)
            }
            item {
                Text("What do you want to do?", style = MaterialTheme.typography.titleMedium)
            }
            item {
                HomeActionCard(
                    title = "Create image",
                    subtitle = "Start Local Image Studio with approval.",
                    icon = Icons.Default.Wallpaper,
                    testTag = "home-create-image",
                    onClick = onCreateImage
                )
            }
            item {
                HomeActionCard(
                    title = "Create video",
                    subtitle = "Try the experimental Local Video Studio flow.",
                    icon = Icons.Default.VideoLibrary,
                    testTag = "home-create-video",
                    onClick = onCreateVideo
                )
            }
            item {
                HomeActionCard(
                    title = "Create 3D model",
                    subtitle = "Prepare a GLB/glTF model card.",
                    icon = Icons.Default.ViewInAr,
                    testTag = "home-create-3d",
                    onClick = onCreate3d
                )
            }
            item {
                HomeActionCard(
                    title = "Ask EchoLabs",
                    subtitle = "Open recent conversations and start a new chat.",
                    icon = Icons.Default.ChatBubble,
                    testTag = "home-ask-echolabs",
                    onClick = onAskEchoLabs
                )
            }
            item {
                HomeActionCard(
                    title = "Open gallery",
                    subtitle = "View, save, or share your private results.",
                    icon = Icons.Default.PhotoLibrary,
                    testTag = "home-open-gallery",
                    onClick = onOpenGallery
                )
            }
        }
    }
}

@Composable
private fun StatusPills(state: AppUiState) {
    val hosted = state.backendUrl.trim() == SettingsStore.PUBLIC_BACKEND_URL
    val approvalReady = state.storeCatalog.addons.any { addon ->
        addon.requiresApproval && addon.approvalRoute?.action.orEmpty().isNotBlank()
    }
    val imageAddon = state.storeCatalog.addons.firstOrNull { it.id == "local-image-studio" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = {},
            label = { Text(if (hosted) "Hosted API connected" else "Backend selected") }
        )
        AssistChip(
            onClick = {},
            label = { Text(if (approvalReady) "Approval ready" else "Approval unavailable") }
        )
    }
    Spacer(Modifier.height(8.dp))
    AssistChip(
        onClick = {},
        label = {
            Text(
                if (imageAddon?.enabled == true) "Local engine status: available when configured"
                else "Local engine unavailable"
            )
        }
    )
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
        }
    }
}
