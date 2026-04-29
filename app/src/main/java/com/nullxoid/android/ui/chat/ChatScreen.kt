package com.nullxoid.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.formatStreamMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshModels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    fun sendDraft() {
        val text = draft
        if (text.isBlank() || state.streaming) return
        draft = ""
        onSend(text)
    }

    val renderedList = buildList {
        addAll(state.activeMessages)
        if (state.streaming) {
            add(
                ChatMessage(
                    role = "assistant",
                    content = streamingAssistantText(state)
                )
            )
        }
    }

    LaunchedEffect(renderedList.size) {
        if (renderedList.isNotEmpty()) {
            listState.animateScrollToItem(renderedList.lastIndex)
        }
    }

    LaunchedEffect(state.streaming, state.streamBuffer.length) {
        if (state.streaming && renderedList.isNotEmpty()) {
            listState.scrollToItem(renderedList.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeChat?.title ?: "New chat")
                        state.selectedModel?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                        if (state.streaming) {
                            Text(
                                streamMetricLabel(state),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (state.lastStreamMetric.isNotBlank()) {
                            Text(
                                state.lastStreamMetric,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(12.dp)) {
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (state.lastFailedPrompt != null && !state.streaming) {
                            Spacer(Modifier.height(6.dp))
                            AssistChip(
                                modifier = Modifier.testTag("chat-retry-last-message"),
                                onClick = onRetry,
                                label = { Text("Retry last message") }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Enter && !event.isShiftPressed) {
                                        if (event.type == KeyEventType.KeyUp) sendDraft()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .testTag("chat-message-input"),
                            placeholder = { Text("Message...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                            maxLines = 6,
                            enabled = !state.streaming
                        )
                        Spacer(Modifier.widthIn(min = 8.dp))
                        if (state.streaming) {
                            IconButton(onClick = onCancel) { Icon(Icons.Default.Stop, "stop") }
                        } else {
                            IconButton(
                                modifier = Modifier.testTag("chat-send"),
                                onClick = { sendDraft() },
                                enabled = draft.isNotBlank()
                            ) { Icon(Icons.AutoMirrored.Filled.Send, "send") }
                        }
                    }
                }
            }
        }
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(inner).fillMaxSize()
        ) {
            if (renderedList.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Start the conversation.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    if (state.selectedModel != null) {
                        AssistChip(onClick = {}, label = { Text(state.selectedModel) })
                    } else {
                        Text(
                            "No model is selected yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                modifier = Modifier.testTag("chat-refresh-models"),
                                onClick = onRefreshModels,
                                label = { Text("Refresh models") }
                            )
                            AssistChip(
                                modifier = Modifier.testTag("chat-open-settings"),
                                onClick = onOpenSettings,
                                label = { Text("Settings") }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 12.dp,
                        end = 12.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(renderedList) { msg -> MessageBubble(msg) }
                }
            }
        }
    }
}

private fun streamingAssistantText(state: AppUiState): String {
    return state.streamBuffer.ifBlank { "Thinking..." }
}

private fun streamMetricLabel(state: AppUiState): String {
    val status = state.streamStatus.ifBlank {
        if (state.streamBuffer.isBlank()) "Thinking" else "Streaming"
    }
    return formatStreamMetric(
        status = status,
        tokens = state.streamApproxTokens,
        tokensPerSecond = state.streamTokensPerSecond
    )
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role.equals("user", ignoreCase = true)
    val bg = if (isUser) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    msg.role.uppercase(),
                    color = fg.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.content, color = fg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Suppress("unused")
private val DebugTransparent = Color.Transparent
