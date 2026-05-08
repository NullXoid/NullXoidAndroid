package com.nullxoid.android.ui.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.chat.friendlyChatError
import com.nullxoid.android.ui.formatStreamMetric
import kotlinx.coroutines.launch

private const val ASSISTANT_SWIPE_THRESHOLD = 120f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppUiState,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpen3d: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFullChat: () -> Unit,
    onNewChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCancelMessage: () -> Unit,
    onRetryMessage: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var assistantVisible by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var lastSwipeAtMs by remember { mutableStateOf(0L) }
    val activeJobs = activeHomeJobCount(state.storeJobs, state.activeStoreJobId)
    val latestArtifact = latestReadyHomeArtifact(state.storeGalleryAll.ifEmpty { state.storeGallery.items })
    val continueDestination = homeContinueDestination(activeJobs)

    fun openAssistant(startNew: Boolean = false) {
        if (startNew) onNewChat()
        assistantVisible = true
        scope.launch { sheetState.show() }
    }

    fun closeAssistant() {
        scope.launch {
            sheetState.hide()
            assistantVisible = false
        }
    }

    BackHandler(enabled = assistantVisible) { closeAssistant() }

    Scaffold { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    var verticalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { verticalDrag = 0f },
                        onVerticalDrag = { _, dragAmount -> verticalDrag += dragAmount },
                        onDragCancel = { verticalDrag = 0f },
                        onDragEnd = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastSwipeAtMs < 500L) return@detectVerticalDragGestures
                            if (verticalDrag < -ASSISTANT_SWIPE_THRESHOLD) {
                                lastSwipeAtMs = now
                                openAssistant()
                            }
                        }
                    )
                }
                .testTag("home-screen")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
                contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item("home-header") {
                    HomeHeader()
                }
                item("home-continue") {
                    ContinueCard(
                        detail = homeContinueText(activeJobs, latestArtifact),
                        onClick = {
                            when (continueDestination) {
                                HomeContinueDestination.Jobs -> onOpenJobs()
                                HomeContinueDestination.Gallery -> onOpenGallery()
                            }
                        }
                    )
                }
                item("home-primary-tiles") {
                    HomeTileGrid(
                        tiles = homeTiles.filter { it.primary },
                        activeJobs = activeJobs,
                        latestArtifactReady = latestArtifact != null,
                        onOpenTile = { id ->
                            when (id) {
                                HOME_ROUTE_IMAGE -> onOpenImage()
                                HOME_ROUTE_VIDEO -> onOpenVideo()
                                HOME_ROUTE_3D -> onOpen3d()
                            }
                        }
                    )
                }
                item("home-utility-tiles") {
                    HomeTileGrid(
                        tiles = homeTiles.filterNot { it.primary },
                        activeJobs = activeJobs,
                        latestArtifactReady = latestArtifact != null,
                        onOpenTile = { id ->
                            when (id) {
                                HOME_ROUTE_GALLERY -> onOpenGallery()
                                HOME_ROUTE_JOBS -> onOpenJobs()
                                HOME_ROUTE_SETTINGS -> onOpenSettings()
                            }
                        }
                    )
                }
            }

            AskNullXoidPill(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .navigationBarsPadding(),
                onClick = { openAssistant() }
            )
        }
    }

    if (assistantVisible) {
        ModalBottomSheet(
            onDismissRequest = { assistantVisible = false },
            sheetState = sheetState,
            dragHandle = { AssistantDragHandle() }
        ) {
            AssistantSheetContent(
                state = state,
                draft = draft,
                onDraftChange = { draft = it },
                onSendDraft = {
                    val text = draft.trim()
                    if (text.isNotBlank()) {
                        draft = ""
                        onSendMessage(text)
                    }
                },
                onClose = { closeAssistant() },
                onNewChat = {
                    draft = ""
                    onNewChat()
                },
                onOpenFullChat = onOpenFullChat,
                onOpenImage = {
                    closeAssistant()
                    onOpenImage()
                },
                onOpenJobs = {
                    closeAssistant()
                    onOpenJobs()
                },
                onCancel = onCancelMessage,
                onRetry = onRetryMessage
            )
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "NullXoid",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "What do you want to create?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("N", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ContinueCard(detail: String, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-continue-card"),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HomeTileGrid(
    tiles: List<HomeTile>,
    activeJobs: Int,
    latestArtifactReady: Boolean,
    onOpenTile: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        tiles.forEach { tile ->
            HomeLauncherTile(
                tile = tile,
                badge = when {
                    tile.id == HOME_ROUTE_3D -> tile.badge
                    tile.id == HOME_ROUTE_JOBS && activeJobs > 0 -> activeJobs.toString()
                    tile.id == HOME_ROUTE_GALLERY && latestArtifactReady -> "New"
                    else -> tile.badge
                },
                onClick = { onOpenTile(tile.id) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HomeLauncherTile(
    tile: HomeTile,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = tileIconColor(tile)
    val badgeShape = RoundedCornerShape(if (tile.id == HOME_ROUTE_3D) 14.dp else 8.dp)
    val badgeHorizontalPadding = if (tile.id == HOME_ROUTE_3D) 5.dp else 6.dp
    val badgeVerticalPadding = if (tile.id == HOME_ROUTE_3D) 1.dp else 2.dp
    Column(
        modifier = modifier
            .semantics {
                contentDescription = if (badge.isBlank()) {
                    tile.label
                } else {
                    "${tile.label}, $badge"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick)
                .testTag("home-tile-${tile.id}"),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (tile.primary) iconColor.copy(alpha = 0.92f)
                else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                1.dp,
                if (tile.primary) iconColor.copy(alpha = 0.35f) else iconColor.copy(alpha = 0.36f)
            )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    tileIcon(tile.id),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = if (tile.primary) Color.White else iconColor
                )
                if (badge.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(if (tile.id == HOME_ROUTE_3D) 8.dp else 6.dp),
                        shape = badgeShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (tile.id == HOME_ROUTE_3D) 0.9f else 1f)
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(
                                horizontal = badgeHorizontalPadding,
                                vertical = badgeVerticalPadding
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tile.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AskNullXoidPill(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .testTag("home-ask-nullxoid"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Ask NullXoid", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AssistantDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 64.dp, height = 5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
    )
}

@Composable
private fun AssistantSheetContent(
    state: AppUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenFullChat: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenJobs: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val messages = renderedAssistantMessages(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-assistant-sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NullXoid Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (state.activeChat == null) "New chat" else state.activeChat.title.ifBlank { "Open chat" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpenFullChat) { Text("Open full chat") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close assistant") }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Tell me what you want to make.", fontWeight = FontWeight.SemiBold)
                Text(
                    "I can help choose Image, Video, or 3D.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onDraftChange("Give me an image idea for ") }, label = { Text("Image idea") })
            AssistChip(onClick = { onDraftChange("Improve this prompt: ") }, label = { Text("Fix prompt") })
            AssistChip(
                onClick = { onDraftChange("Explain the 3D beta mesh and wrapping limits.") },
                label = { Text("Explain 3D beta") }
            )
        }
        AssistChip(
            onClick = onOpenJobs,
            label = { Text("Open latest job") }
        )
        if (messages.isEmpty()) {
            Text(
                "Ask for workflow help, prompt cleanup, or a quick recommendation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.takeLast(8)) { message ->
                    AssistantMessageBubble(message)
                }
            }
        }
        state.error?.let { error ->
            Text(
                friendlyChatError(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            if (state.lastFailedPrompt != null && !state.streaming) {
                AssistChip(onClick = onRetry, label = { Text("Retry last message") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home-assistant-input"),
                placeholder = { Text("Ask or describe what to create...") },
                maxLines = 4,
                enabled = !state.streaming,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendDraft() })
            )
            if (state.streaming) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Stop, "Stop assistant") }
            } else {
                IconButton(
                    modifier = Modifier.testTag("home-assistant-send"),
                    onClick = onSendDraft,
                    enabled = draft.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send to assistant")
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: ChatMessage) {
    val isUser = message.role.equals("user", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(8.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun renderedAssistantMessages(state: AppUiState): List<ChatMessage> =
    buildList {
        addAll(state.activeMessages)
        if (state.streaming) {
            add(
                ChatMessage(
                    role = "assistant",
                    content = if (state.streamBuffer.isBlank()) {
                        formatStreamMetric(
                            status = state.streamStatus.ifBlank { "Thinking" },
                            tokens = state.streamApproxTokens,
                            tokensPerSecond = state.streamTokensPerSecond
                        )
                    } else {
                        state.streamBuffer
                    }
                )
            )
        }
    }

private fun tileIcon(id: String): ImageVector = when (id) {
    HOME_ROUTE_IMAGE -> Icons.Default.Image
    HOME_ROUTE_VIDEO -> Icons.Default.VideoLibrary
    HOME_ROUTE_3D -> Icons.Default.ViewInAr
    HOME_ROUTE_GALLERY -> Icons.Default.PhotoLibrary
    HOME_ROUTE_JOBS -> Icons.Default.Inventory2
    HOME_ROUTE_SETTINGS -> Icons.Default.Settings
    else -> Icons.Default.AutoAwesome
}

private fun tileIconColor(tile: HomeTile): Color = when (tile.id) {
    HOME_ROUTE_IMAGE -> Color(0xFF3F7DFF)
    HOME_ROUTE_VIDEO -> Color(0xFFB144F2)
    HOME_ROUTE_3D -> Color(0xFF14BFA6)
    else -> Color(0xFF5E6E9E)
}
