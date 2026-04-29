package com.nullxoid.android.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    state: AppUiState,
    onOpenChat: (ChatRecord) -> Unit,
    onNewChat: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealth: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NullXoid")
                        val who = state.auth.displayName ?: state.auth.username ?: "signed in"
                        Text(who, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("chat-list-refresh"),
                        onClick = onRefresh
                    ) { Icon(Icons.Default.Refresh, null) }
                    IconButton(
                        modifier = Modifier.testTag("chat-list-health"),
                        onClick = onOpenHealth
                    ) { Icon(Icons.Default.Favorite, "health") }
                    IconButton(
                        modifier = Modifier.testTag("chat-list-settings"),
                        onClick = onOpenSettings
                    ) { Icon(Icons.Default.Settings, null) }
                    IconButton(
                        modifier = Modifier.testTag("chat-list-logout"),
                        onClick = onLogout
                    ) { Icon(Icons.AutoMirrored.Filled.Logout, null) }
                }
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
                .fillMaxSize()
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
                        "No chats yet. Tap + to start one.",
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
            }
            chat.updatedAt?.let {
                Spacer(Modifier.height(4.dp))
                Row { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
