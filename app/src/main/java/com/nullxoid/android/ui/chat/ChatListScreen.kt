package com.nullxoid.android.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.MainBottomNavigation
import com.nullxoid.android.ui.MainTab
import com.nullxoid.android.ui.mainTabSwipeNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    state: AppUiState,
    onOpenChat: (ChatRecord) -> Unit,
    onNewChat: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealth: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ask EchoLabs")
                        Text("Recent conversations", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("chat-list-refresh"),
                        onClick = onRefresh
                    ) { Icon(Icons.Default.Refresh, "Refresh chats") }
                    IconButton(
                        modifier = Modifier.testTag("chat-list-health"),
                        onClick = onOpenHealth
                    ) { Icon(Icons.Default.Favorite, "Backend health") }
                }
            )
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainTab.Ask,
                onOpenCreate = onOpenCreate,
                onOpenGallery = onOpenGallery,
                onOpenAsk = onOpenAsk,
                onOpenSettings = onOpenSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.testTag("chat-list-new-chat"),
                onClick = onNewChat
            ) { Icon(Icons.Default.Add, "New chat") }
        }
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(inner)
                .navigationBarsPadding()
                .imePadding()
                .fillMaxSize()
                .mainTabSwipeNavigation(
                    selected = MainTab.Ask,
                    onOpenCreate = onOpenCreate,
                    onOpenGallery = onOpenGallery,
                    onOpenAsk = onOpenAsk,
                    onOpenSettings = onOpenSettings
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            val active = state.chats.filter { !it.archived }
            if (active.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No conversations yet. Tap + to ask EchoLabs.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    items(active, key = { it.id }) { chat ->
                        ChatRow(chat = chat, onClick = { onOpenChat(chat) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                chat.title.ifBlank { "Untitled chat" },
                style = MaterialTheme.typography.titleMedium
            )
            val preview = chat.session?.messages?.lastOrNull()?.content.orEmpty()
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    preview.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            } else if (chat.e2ee != null && chat.e2eeStatus != "unlocked") {
                Spacer(Modifier.height(4.dp))
                Text(
                    lockedChatPreview(chat.e2eeStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            formatMessageTimestamp(chat.updatedAt ?: chat.createdAt)?.let {
                Spacer(Modifier.height(4.dp))
                Row { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun lockedChatPreview(status: String?): String = when (status) {
    "account_epoch_wrapped_key" -> "Shared E2EE envelope detected. Native handoff unlock is next."
    "browser_indexeddb_key", "browser_local_storage_key" -> "Locked by a browser device key. Cross-device handoff is pending."
    "android_other_install" -> "Locked by another Android install key."
    "android_local_key" -> "Locked local Android key; refresh or sign in again."
    "unsupported_key_envelope", "unsupported_version", "missing_key_envelope" -> "Encrypted with an unsupported saved-chat envelope."
    else -> "Encrypted chat is locked on this device."
}
