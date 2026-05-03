package com.nullxoid.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

enum class MainTab {
    Chats,
    Store,
    Gallery,
    Settings
}

@Composable
fun MainBottomNavigation(
    selected: MainTab,
    onOpenChats: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    NavigationBar(modifier = Modifier.testTag("main-bottom-nav")) {
        NavigationBarItem(
            selected = selected == MainTab.Chats,
            onClick = onOpenChats,
            icon = { Icon(Icons.Default.ChatBubble, "Chats") },
            label = { Text("Chats") },
            modifier = Modifier.testTag("bottom-nav-chats")
        )
        NavigationBarItem(
            selected = selected == MainTab.Store,
            onClick = onOpenStore,
            icon = { Icon(Icons.Default.Storefront, "Store") },
            label = { Text("Store") },
            modifier = Modifier.testTag("bottom-nav-store")
        )
        NavigationBarItem(
            selected = selected == MainTab.Gallery,
            onClick = onOpenGallery,
            icon = { Icon(Icons.Default.PhotoLibrary, "Gallery") },
            label = { Text("Gallery") },
            modifier = Modifier.testTag("bottom-nav-gallery")
        )
        NavigationBarItem(
            selected = selected == MainTab.Settings,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Default.Settings, "Settings") },
            label = { Text("Settings") },
            modifier = Modifier.testTag("bottom-nav-settings")
        )
    }
}
