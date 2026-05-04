package com.nullxoid.android.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import kotlin.math.abs

enum class MainTab {
    Create,
    Gallery,
    Ask,
    Settings
}

private object MainTabSwipeState {
    var lastNavigationAtMs: Long = 0
}

fun Modifier.mainTabSwipeNavigation(
    selected: MainTab,
    onOpenCreate: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenSettings: () -> Unit
): Modifier = pointerInput(selected) {
    var horizontalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { horizontalDrag = 0f },
        onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
        onDragCancel = { horizontalDrag = 0f },
        onDragEnd = {
            if (abs(horizontalDrag) >= 120f) {
                val now = SystemClock.elapsedRealtime()
                if (now - MainTabSwipeState.lastNavigationAtMs < 650L) return@detectHorizontalDragGestures
                val tabs = MainTab.entries
                val index = tabs.indexOf(selected)
                val nextIndex = if (horizontalDrag < 0) index + 1 else index - 1
                when (tabs.getOrNull(nextIndex)) {
                    MainTab.Create -> onOpenCreate()
                    MainTab.Gallery -> onOpenGallery()
                    MainTab.Ask -> onOpenAsk()
                    MainTab.Settings -> onOpenSettings()
                    null -> Unit
                }
                MainTabSwipeState.lastNavigationAtMs = now
            }
        }
    )
}

@Composable
fun MainBottomNavigation(
    selected: MainTab,
    onOpenCreate: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenSettings: () -> Unit
) {
    NavigationBar(modifier = Modifier.testTag("main-bottom-nav")) {
        NavigationBarItem(
            selected = selected == MainTab.Create,
            onClick = onOpenCreate,
            icon = { Icon(Icons.Default.AutoAwesome, "Create") },
            label = { Text("Create") },
            modifier = Modifier.testTag("bottom-nav-create")
        )
        NavigationBarItem(
            selected = selected == MainTab.Gallery,
            onClick = onOpenGallery,
            icon = { Icon(Icons.Default.PhotoLibrary, "Gallery") },
            label = { Text("Gallery") },
            modifier = Modifier.testTag("bottom-nav-gallery")
        )
        NavigationBarItem(
            selected = selected == MainTab.Ask,
            onClick = onOpenAsk,
            icon = { Icon(Icons.Default.Favorite, "Ask") },
            label = { Text("Ask") },
            modifier = Modifier.testTag("bottom-nav-ask")
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
