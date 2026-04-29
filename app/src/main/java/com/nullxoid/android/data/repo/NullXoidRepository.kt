package com.nullxoid.android.data.repo

import android.content.Context
import com.nullxoid.android.data.api.ChatStream
import com.nullxoid.android.data.api.BackendEndpoint
import com.nullxoid.android.data.api.NullXoidApi
import com.nullxoid.android.data.auth.NativeAuthCoordinator
import com.nullxoid.android.data.auth.OidcLaunch
import com.nullxoid.android.data.e2ee.AndroidSavedChatKeyProvider
import com.nullxoid.android.data.e2ee.SavedChatE2ee
import com.nullxoid.android.data.e2ee.SavedChatKeyProvider
import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatCreateRequest
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatSession
import com.nullxoid.android.data.model.ChatStreamRequest
import com.nullxoid.android.data.model.ChatUpdateRequest
import com.nullxoid.android.data.model.ClientManifest
import com.nullxoid.android.data.model.HealthFeatures
import com.nullxoid.android.data.model.ModelDescriptor
import com.nullxoid.android.data.model.OidcCompleteRequest
import com.nullxoid.android.data.model.PasskeyCredentialsResponse
import com.nullxoid.android.data.model.ProjectCreateRequest
import com.nullxoid.android.data.model.StreamEvent
import com.nullxoid.android.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Single place the UI goes through. Same role as the desktop Controller
 * (AiAssistant/src/app) which sits between MainWindow and the bridge.
 */
class NullXoidRepository(
    private val settingsStore: SettingsStore,
    private val savedChatKeyProvider: SavedChatKeyProvider = AndroidSavedChatKeyProvider()
) {
    private val api = NullXoidApi { currentBaseUrl }
    private val nativeAuth = NativeAuthCoordinator(api)
    private val chatStream = ChatStream { currentBaseUrl }

    @Volatile
    private var cachedBaseUrl: String = SettingsStore.DEFAULT_BACKEND_URL
    private val currentBaseUrl: String get() = cachedBaseUrl

    @Volatile
    private var cachedChatContext: ChatContext? = null

    private val _auth = MutableStateFlow(AuthState())
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    suspend fun refreshBaseUrl() {
        cachedBaseUrl = settingsStore.backendUrl.first()
    }

    val backendUrl: Flow<String> = settingsStore.backendUrl

    suspend fun setBackendUrl(url: String) {
        val normalized = BackendEndpoint.normalize(url)
        settingsStore.setBackendUrl(normalized)
        cachedBaseUrl = normalized
        cachedChatContext = null
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
        cachedChatContext = null
        return state
    }

    suspend fun loginWithPasskey(context: Context): AuthState {
        refreshBaseUrl()
        val state = nativeAuth.signInWithPasskey(context)
        _auth.value = state
        cachedChatContext = null
        return state
    }

    suspend fun passkeyCredentials(): PasskeyCredentialsResponse {
        refreshBaseUrl()
        return api.passkeyCredentials()
    }

    suspend fun registerPasskey(context: Context): PasskeyCredentialsResponse {
        refreshBaseUrl()
        return nativeAuth.registerPasskey(context)
    }

    suspend fun revokePasskey(credentialId: String) {
        refreshBaseUrl()
        api.revokePasskey(credentialId)
    }

    suspend fun startOidcSignIn(redirectUri: String): OidcLaunch {
        refreshBaseUrl()
        return nativeAuth.startOidcSignIn(redirectUri)
    }

    suspend fun completeOidcSignIn(
        code: String,
        state: String,
        redirectUri: String,
        codeVerifier: String
    ): AuthState {
        refreshBaseUrl()
        val authState = api.completeOidc(
            OidcCompleteRequest(
                code = code,
                state = state,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier
            )
        )
        _auth.value = authState
        cachedChatContext = null
        return authState
    }

    suspend fun logout() {
        runCatching { api.logout() }
        _auth.value = AuthState()
        cachedChatContext = null
    }

    suspend fun models(): List<ModelDescriptor> = api.models().models

    suspend fun chats(): List<ChatRecord> {
        val auth = requireScopedAuth()
        val context = resolveChatContext()
        return api.chats(
            tenantId = auth.tenantId.orEmpty(),
            userId = auth.userId.orEmpty(),
            workspaceId = context.workspaceId
        ).chats.map { decryptChatRecord(auth, it) }
    }

    suspend fun createChat(
        title: String,
        messages: List<ChatMessage>,
        workspaceId: String? = null,
        projectId: String? = null
    ): ChatRecord {
        val auth = requireScopedAuth()
        val context = if (workspaceId.isNullOrBlank() || projectId.isNullOrBlank()) {
            resolveChatContext()
        } else {
            ChatContext(workspaceId, projectId)
        }
        return api.createChat(
            ChatCreateRequest(
                tenantId = auth.tenantId.orEmpty(),
                userId = auth.userId.orEmpty(),
                workspaceId = context.workspaceId,
                projectId = context.projectId,
                title = title,
                messages = emptyList(),
                e2ee = savedChatEnvelopeOrNull(
                    auth.tenantId.orEmpty(),
                    auth.userId.orEmpty(),
                    title,
                    messages
                )
            )
        )
    }

    suspend fun updateChat(
        chat: ChatRecord,
        title: String,
        messages: List<ChatMessage>
    ): ChatRecord {
        val auth = requireScopedAuth()
        val context = if (chat.workspaceId.isNullOrBlank() || chat.projectId.isNullOrBlank()) {
            resolveChatContext()
        } else {
            ChatContext(chat.workspaceId, chat.projectId)
        }
        return api.updateChat(
            chat.id,
            ChatUpdateRequest(
                tenantId = auth.tenantId.orEmpty(),
                userId = auth.userId.orEmpty(),
                workspaceId = context.workspaceId,
                projectId = context.projectId,
                title = title,
                messages = emptyList(),
                e2ee = savedChatEnvelopeOrNull(
                    auth.tenantId.orEmpty(),
                    auth.userId.orEmpty(),
                    title,
                    messages
                )
            )
        )
    }

    suspend fun archiveChat(chatId: String, archived: Boolean) =
        api.archiveChat(chatId, archived)

    suspend fun health(): HealthFeatures = api.healthFeatures()

    suspend fun clientManifest(): ClientManifest = api.clientManifest("android")

    fun streamReply(
        model: String,
        messages: List<ChatMessage>,
        chatId: String? = null,
        workspaceId: String? = null,
        projectId: String? = null
    ): Flow<StreamEvent> = flow {
        val auth = requireScopedAuth()
        val context = if (workspaceId.isNullOrBlank() || projectId.isNullOrBlank()) {
            resolveChatContext()
        } else {
            ChatContext(workspaceId, projectId)
        }
        emitAll(
            chatStream.stream(
                ChatStreamRequest(
                    model = model,
                    messages = messages,
                    chatId = chatId,
                    workspaceId = context.workspaceId,
                    projectId = context.projectId,
                    tenantId = auth.tenantId,
                    userId = auth.userId,
                    providerThinking = providerThinkingFor(model)
                )
            )
        )
    }

    private fun providerThinkingFor(model: String): Boolean? {
        val normalized = model.lowercase()
        return if (
            normalized.contains("qwen") ||
            normalized.contains("deepseek") ||
            normalized.contains("reasoning") ||
            normalized.contains("r1")
        ) {
            true
        } else {
            null
        }
    }

    private fun savedChatEnvelopeOrNull(
        tenantId: String,
        userId: String,
        title: String,
        messages: List<ChatMessage>
    ) = if (messages.isEmpty()) {
        null
    } else {
        SavedChatE2ee.envelope(
            tenantId = tenantId,
            userId = userId,
            title = title,
            messages = messages,
            keyProvider = savedChatKeyProvider
        )
    }

    private fun decryptChatRecord(auth: AuthState, chat: ChatRecord): ChatRecord {
        val localKeyId = savedChatKeyProvider.keyId(auth.tenantId.orEmpty(), auth.userId.orEmpty())
        val envelopeInfo = SavedChatE2ee.inspectEnvelope(chat.e2ee, localKeyId)
        if (!chat.session?.messages.isNullOrEmpty()) {
            return chat.copy(e2eeStatus = if (chat.e2ee != null) "plaintext_with_e2ee_metadata" else null)
        }
        val payload = runCatching {
            SavedChatE2ee.decryptPayload(
                tenantId = auth.tenantId.orEmpty(),
                userId = auth.userId.orEmpty(),
                e2ee = chat.e2ee,
                keyProvider = savedChatKeyProvider
            )
        }.getOrNull() ?: return chat.copy(e2eeStatus = envelopeInfo.status)
        return chat.copy(
            title = chat.title.ifBlank { payload.title },
            session = ChatSession(messages = payload.messages),
            e2eeStatus = "unlocked"
        )
    }

    suspend fun selectedModel(): String? = settingsStore.selectedModel.first()
    suspend fun setSelectedModel(modelId: String) = settingsStore.setSelectedModel(modelId)

    private suspend fun requireScopedAuth(): AuthState {
        val current = _auth.value
        if (!current.userId.isNullOrBlank() && !current.tenantId.isNullOrBlank()) {
            return current
        }
        val refreshed = api.fetchAuthState()
        _auth.value = refreshed
        if (refreshed.userId.isNullOrBlank()) {
            error("Sign in again before starting a hosted chat")
        }
        if (refreshed.tenantId.isNullOrBlank()) {
            return refreshed.copy(tenantId = "default").also { _auth.value = it }
        }
        return refreshed
    }

    private suspend fun resolveChatContext(): ChatContext {
        cachedChatContext?.let { return it }
        val workspaces = api.workspaces()
        val workspaceId = (
            workspaces.workspaces.firstOrNull { it.workspaceId == workspaces.activeWorkspaceId }
                ?: workspaces.workspaces.firstOrNull { it.slug == "lobby" }
                ?: workspaces.workspaces.firstOrNull()
            )?.workspaceId?.takeIf { it.isNotBlank() }
            ?: error("No workspace available for chat")

        val projects = api.projects(workspaceId).projects
        val project = projects.firstOrNull { it.slug == "general" }
            ?: projects.firstOrNull()
            ?: api.createProject(ProjectCreateRequest(name = "General", workspaceId = workspaceId))
        val projectId = project.resolvedProjectId.takeIf { it.isNotBlank() }
            ?: error("No project available for chat")

        return ChatContext(workspaceId = workspaceId, projectId = projectId).also {
            cachedChatContext = it
        }
    }

    data class ChatContext(
        val workspaceId: String,
        val projectId: String
    )
}
