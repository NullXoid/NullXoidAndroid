package com.nullxoid.android.data.repo

import com.nullxoid.android.data.api.ChatStream
import com.nullxoid.android.data.api.NullXoidApi
import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatCreateRequest
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatStreamRequest
import com.nullxoid.android.data.model.HealthFeatures
import com.nullxoid.android.data.model.ModelDescriptor
import com.nullxoid.android.data.model.StreamEvent
import com.nullxoid.android.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Single place the UI goes through. Same role as the desktop Controller
 * (AiAssistant/src/app) which sits between MainWindow and the bridge.
 */
class NullXoidRepository(
    private val settingsStore: SettingsStore
) {
    private val api = NullXoidApi { currentBaseUrl }
    private val chatStream = ChatStream { currentBaseUrl }

    @Volatile
    private var cachedBaseUrl: String = SettingsStore.DEFAULT_BACKEND_URL
    private val currentBaseUrl: String get() = cachedBaseUrl

    private val _auth = MutableStateFlow(AuthState())
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    suspend fun refreshBaseUrl() {
        cachedBaseUrl = settingsStore.backendUrl.first()
    }

    val backendUrl: Flow<String> = settingsStore.backendUrl

    suspend fun setBackendUrl(url: String) {
        settingsStore.setBackendUrl(url)
        cachedBaseUrl = url
    }

    suspend fun bootstrap(): AuthState {
        refreshBaseUrl()
        val state = runCatching { api.fetchAuthState() }.getOrElse { AuthState() }
        _auth.value = state
        return state
    }

    suspend fun login(username: String, password: String): AuthState {
        refreshBaseUrl()
        val state = api.login(username, password)
        _auth.value = state
        return state
    }

    suspend fun logout() {
        runCatching { api.logout() }
        _auth.value = AuthState()
    }

    suspend fun models(): List<ModelDescriptor> = api.models().models

    suspend fun chats(): List<ChatRecord> = api.chats().chats

    suspend fun createChat(
        title: String,
        messages: List<ChatMessage>,
        workspaceId: String? = null,
        projectId: String? = null
    ): ChatRecord {
        val auth = _auth.value
        return api.createChat(
            ChatCreateRequest(
                tenantId = auth.tenantId.orEmpty(),
                userId = auth.userId.orEmpty(),
                workspaceId = workspaceId,
                projectId = projectId,
                title = title,
                messages = messages
            )
        )
    }

    suspend fun archiveChat(chatId: String, archived: Boolean) =
        api.archiveChat(chatId, archived)

    suspend fun health(): HealthFeatures = api.healthFeatures()

    fun streamReply(
        model: String,
        messages: List<ChatMessage>,
        chatId: String? = null
    ): Flow<StreamEvent> {
        val auth = _auth.value
        return chatStream.stream(
            ChatStreamRequest(
                model = model,
                messages = messages,
                chatId = chatId,
                tenantId = auth.tenantId,
                userId = auth.userId
            )
        )
    }

    suspend fun selectedModel(): String? = settingsStore.selectedModel.first()
    suspend fun setSelectedModel(modelId: String) = settingsStore.setSelectedModel(modelId)
}
