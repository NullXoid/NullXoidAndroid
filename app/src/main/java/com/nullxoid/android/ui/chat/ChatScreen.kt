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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.formatStreamMetric
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
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
                    content = streamingAssistantText(state),
                    createdAt = streamingAssistantCreatedAt(state)
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
                    if (state.activeChat?.e2ee != null) {
                        Text("Encrypted chat from another key.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            lockedChatDetail(state.activeChat?.e2eeStatus),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Start the conversation.", style = MaterialTheme.typography.bodyMedium)
                    }
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
                    itemsIndexed(renderedList) { index, msg ->
                        val previousUserCreatedAt = if (msg.role.equals("assistant", ignoreCase = true)) {
                            renderedList
                                .take(index)
                                .lastOrNull { it.role.equals("user", ignoreCase = true) }
                                ?.createdAt
                        } else {
                            null
                        }
                        MessageBubble(
                            msg = msg,
                            previousUserCreatedAt = previousUserCreatedAt,
                            onClick = { selectedMessage = msg }
                        )
                    }
                }
            }
        }
    }

    selectedMessage?.let { msg ->
        MessageDetailsSheet(
            msg = msg,
            metric = if (
                !state.streaming &&
                msg.role.equals("assistant", ignoreCase = true) &&
                msg == state.activeMessages.lastOrNull()
            ) {
                state.lastStreamMetric
            } else {
                ""
            },
            onDismiss = { selectedMessage = null },
            onCopy = {
                clipboard.setText(AnnotatedString(msg.content))
                selectedMessage = null
            }
        )
    }
}

private fun streamingAssistantText(state: AppUiState): String {
    return state.streamBuffer.ifBlank { "Thinking..." }
}

private fun lockedChatDetail(status: String?): String = when (status) {
    "browser_indexeddb_key", "browser_local_storage_key" ->
        "This transcript was saved by a browser device key. Android needs the upcoming cross-device saved-chat key handoff to unlock it."
    "android_other_install" ->
        "This transcript was saved by a different Android install key. Device recovery/handoff is required before this install can decrypt it."
    "android_local_key" ->
        "This transcript matches the local Android key metadata but decryption failed. Refresh, sign in again, or keep this chat for diagnostics."
    "unsupported_key_envelope", "unsupported_version", "missing_key_envelope" ->
        "This transcript uses an unsupported E2EE envelope. The server still stores only ciphertext."
    else ->
        "This transcript exists on the backend, but this Android install cannot decrypt it yet. Cross-device key handoff is still on the E2EE roadmap."
}

private fun streamingAssistantCreatedAt(state: AppUiState): String? {
    return state.streamStartedAtMs
        .takeIf { it > 0L }
        ?.let { Instant.ofEpochMilli(it).toString() }
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
private fun MessageBubble(
    msg: ChatMessage,
    previousUserCreatedAt: String?,
    onClick: () -> Unit
) {
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
            tonalElevation = 0.dp,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    msg.role.uppercase(),
                    color = fg.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.content, color = fg, style = MaterialTheme.typography.bodyMedium)
                formatMessageTiming(
                    createdAt = msg.createdAt,
                    previousUserCreatedAt = previousUserCreatedAt
                )?.let { timestamp ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        timestamp,
                        color = fg.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageDetailsSheet(
    msg: ChatMessage,
    metric: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Message", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(msg.role.lowercase())
                    formatMessageTimestamp(msg.createdAt)?.let { append(" | ").append(it) }
                    append(" | ")
                    append(msg.content.length)
                    append(" chars | tokens ~")
                    append(estimateMessageTokens(msg.content))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (metric.isNotBlank()) {
                Text(
                    metric,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopy) { Text("Copy") }
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = onDismiss, label = { Text("Close") })
            }
        }
    }
}

private fun estimateMessageTokens(text: String): Int =
    if (text.isBlank()) 0 else maxOf(1, (text.length + 3) / 4)

internal fun formatMessageTimestamp(value: String?): String? {
    val input = value?.trim().orEmpty()
    if (input.isBlank()) return null
    val instant = parseMessageInstant(input) ?: return input
    return DateTimeFormatter
        .ofPattern("MMM d, h:mm:ss a", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

internal fun formatMessageTiming(createdAt: String?, previousUserCreatedAt: String?): String? {
    val timestamp = formatMessageTimestamp(createdAt) ?: return null
    val responseInstant = parseMessageInstant(createdAt) ?: return timestamp
    val userInstant = parseMessageInstant(previousUserCreatedAt) ?: return timestamp
    val latencyMs = responseInstant.toEpochMilli() - userInstant.toEpochMilli()
    if (latencyMs < 0L) return timestamp
    val latencySeconds = latencyMs / 1000.0
    val latency = if (latencySeconds < 10) {
        String.format(Locale.US, "%.1fs", latencySeconds)
    } else {
        "${latencySeconds.toInt()}s"
    }
    return "$timestamp | +$latency"
}

private fun parseMessageInstant(value: String?): Instant? {
    val input = value?.trim().orEmpty()
    if (input.isBlank()) return null
    return runCatching { Instant.parse(input) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(input).toInstant() }.getOrNull()
}

@Suppress("unused")
private val DebugTransparent = Color.Transparent
