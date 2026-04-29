package com.nullxoid.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nullxoid.android.BuildConfig
import com.nullxoid.android.backend.BackendService
import com.nullxoid.android.data.api.ApiException
import com.nullxoid.android.data.auth.OidcLaunch
import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatSession
import com.nullxoid.android.data.model.ClientManifest
import com.nullxoid.android.data.model.HealthFeatures
import com.nullxoid.android.data.model.ModelDescriptor
import com.nullxoid.android.data.model.PasskeyCredentialRecord
import com.nullxoid.android.data.model.PasskeyProviderStatus
import com.nullxoid.android.data.model.StreamEvent
import com.nullxoid.android.data.prefs.SettingsStore
import com.nullxoid.android.data.repo.NullXoidRepository
import com.nullxoid.android.data.update.AppUpdateChecker
import com.nullxoid.android.data.update.AppUpdateInfo
import com.nullxoid.android.data.update.AppUpdateInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val OIDC_REDIRECT_URI = "nullxoid://auth/oidc/callback"

internal fun parseSavedChatRecoveryImport(json: Json, raw: String): JsonObject {
    val bundle = json.parseToJsonElement(raw.trim()).jsonObject
    if (bundle["p"]?.jsonPrimitive?.contentOrNull != "nx.aik1") return bundle
    val envelope = bundle["r"]?.jsonObject ?: error("QR import kit is missing recovery fields.")
    return buildJsonObject {
        put("version", 1)
        put("purpose", "nullxoid.android.saved_chat_recovery_bundle.v1")
        put("kit_type", "compact_qr_android_import")
        put("requires_separate_recovery_secret", false)
        put("tenant_id", bundle["t"]?.jsonPrimitive?.contentOrNull ?: "default")
        put("user_id", bundle["u"]?.jsonPrimitive?.contentOrNull ?: "anonymous")
        put("epoch", bundle["e"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1)
        put("plaintext_storage", "forbidden")
        put("recovery_secret", bundle["k"]?.jsonPrimitive?.contentOrNull.orEmpty())
        put("recovery_secret_boundary", "user_initiated_qr_transfer")
        put(
            "recovery_envelope",
            buildJsonObject {
                put("version", 1)
                put("algorithm", "AES-GCM")
                put("salt", envelope["s"]?.jsonPrimitive?.contentOrNull.orEmpty())
                put("nonce", envelope["n"]?.jsonPrimitive?.contentOrNull.orEmpty())
                put("ciphertext", envelope["c"]?.jsonPrimitive?.contentOrNull.orEmpty())
                put("plaintext_storage", "forbidden")
            }
        )
    }
}

internal fun estimateStreamTokens(text: String): Int =
    if (text.isBlank()) 0 else maxOf(1, (text.length + 3) / 4)

internal fun formatStreamMetric(
    status: String,
    tokens: Int,
    tokensPerSecond: Double
): String {
    val speed = String.format(java.util.Locale.US, "%.1f", tokensPerSecond)
    return "$status | tokens ~$tokens | $speed tok/s"
}

internal fun streamStatusLabel(status: String): String =
    when (status.trim().lowercase()) {
        "", "started", "start" -> "Thinking"
        "queued", "pending" -> "Queued"
        "running", "streaming", "token" -> "Streaming"
        "completed", "done", "complete" -> "Done"
        else -> status.replaceFirstChar { it.uppercase() }
    }

internal fun nowIsoTimestamp(): String = Instant.now().toString()

internal fun mobilePasskeySignInError(t: Throwable): String {
    val details = listOfNotNull(t.message, t::class.simpleName).joinToString(" ").lowercase()
    return when {
        t is ApiException && t.code == 401 && "passkey_credential_not_registered" in t.body ->
            "This passkey is not registered for this account. Sign in with password, remove the stale NullXoid passkey from Google Password Manager or Samsung Pass, then add a new passkey in Settings."
        "nocredential" in details || "no credential" in details || "no available" in details ->
            "No saved passkey was found for this phone. Sign in with password once, then open Settings > Account security > Add passkey."
        "rp id" in details || "rpid" in details || "relying party" in details || "validate" in details ->
            "Android could not validate the echolabs.diy passkey domain for this app. Install the latest Forgejo APK, keep the backend on Hosted API, then try again."
        "cancel" in details ->
            "Passkey sign-in was cancelled."
        "unsupported" in details || "provider" in details ->
            "This phone does not have a usable passkey provider enabled. Enable Google Password Manager or Samsung Pass, then try again."
        else -> t.message ?: "Passkey sign-in failed. Sign in with password once, then add a passkey in Settings."
    }
}

internal fun mobilePasskeyRegistrationError(t: Throwable): String {
    val details = listOfNotNull(t.message, t::class.simpleName).joinToString(" ").lowercase()
    return when {
        "rp id" in details || "rpid" in details || "relying party" in details || "validate" in details ->
            "Android could not validate the echolabs.diy passkey domain for this app. Confirm version 0.1.46 or newer is installed, then try Add passkey again."
        "cancel" in details -> "Passkey setup was cancelled."
        "unsupported" in details || "provider" in details ->
            "This phone does not have a usable passkey provider enabled. Enable Google Password Manager or Samsung Pass, then try again."
        else -> t.message ?: "Passkey setup failed. Try Google Password Manager first; Samsung Pass can be tested separately."
    }
}

internal fun passkeyEnrollmentStatusText(
    authenticated: Boolean,
    provider: PasskeyProviderStatus?,
    credentialCount: Int
): String = when {
    !authenticated -> "Sign in with password once to add a passkey on this phone."
    provider?.registrationEnabled == true && credentialCount > 0 ->
        "This account has $credentialCount passkey(s). This phone can sign in only after Android saves one for NullXoid."
    provider?.registrationEnabled == true ->
        "Signed in. Add a passkey here so this phone can use passkey sign-in next time."
    provider?.configured == true -> "Passkey enrollment is disabled by the backend."
    else -> "Passkey provider is not configured on the backend."
}

data class AppUiState(
    val auth: AuthState = AuthState(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val backendUrl: String = "",
    val models: List<ModelDescriptor> = emptyList(),
    val selectedModel: String? = null,
    val chats: List<ChatRecord> = emptyList(),
    val activeChat: ChatRecord? = null,
    val activeMessages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val streamBuffer: String = "",
    val streamStartedAtMs: Long = 0L,
    val streamApproxTokens: Int = 0,
    val streamTokensPerSecond: Double = 0.0,
    val streamStatus: String = "",
    val lastStreamMetric: String = "",
    val lastFailedPrompt: String? = null,
    val health: HealthFeatures? = null,
    val clientManifest: ClientManifest? = null,
    val embeddedEnabled: Boolean = false,
    val embeddedEngine: String = SettingsStore.EMBEDDED_ENGINE_ECHO,
    val ollamaUrl: String = SettingsStore.DEFAULT_OLLAMA_URL,
    val ollamaModel: String = SettingsStore.DEFAULT_OLLAMA_MODEL,
    val updateSource: String = SettingsStore.UPDATE_SOURCE_AUTO,
    val updateInfo: AppUpdateInfo? = null,
    val checkingUpdate: Boolean = false,
    val installingUpdate: Boolean = false,
    val updatePromptDismissed: Boolean = false,
    val passkeyProvider: PasskeyProviderStatus? = null,
    val passkeyCredentials: List<PasskeyCredentialRecord> = emptyList(),
    val passkeyLoading: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val currentAppVersionName: String = BuildConfig.VERSION_NAME,
    val currentAppVersionCode: Int = BuildConfig.VERSION_CODE
)

class NullXoidViewModel(
    private val repo: NullXoidRepository,
    private val appContext: Context,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var updateCheckJob: Job? = null
    private var pendingOidcLaunch: OidcLaunch? = null
    private val importJson = Json { ignoreUnknownKeys = true }

    fun bootstrap() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val url = runCatching { repo.backendUrl.first() }.getOrDefault("")
            val embedded = runCatching { settingsStore.embeddedEnabled.first() }.getOrDefault(false)
            val embeddedEngine = runCatching { settingsStore.embeddedEngine.first() }
                .getOrDefault(SettingsStore.EMBEDDED_ENGINE_ECHO)
            val ollamaUrl = runCatching { settingsStore.ollamaUrl.first() }
                .getOrDefault(SettingsStore.DEFAULT_OLLAMA_URL)
            val ollamaModel = runCatching { settingsStore.ollamaModel.first() }
                .getOrDefault(SettingsStore.DEFAULT_OLLAMA_MODEL)
            val updateSource = runCatching { settingsStore.updateSource.first() }
                .getOrDefault(SettingsStore.UPDATE_SOURCE_AUTO)
            val onboardingCompleted = runCatching { settingsStore.onboardingCompleted.first() }
                .getOrDefault(false)
            if (embedded) ensureBackendRunning()
            val manifest = runCatching { repo.clientManifest() }.getOrNull()
            val auth = runCatching { repo.bootstrap() }.getOrElse { AuthState() }
            val manifestUpdateSource = updateSourceAllowedByManifest(
                AppUiState(updateSource = updateSource, clientManifest = manifest)
            )
            if (manifestUpdateSource != updateSource) {
                settingsStore.setUpdateSource(manifestUpdateSource)
            }
            _state.value = _state.value.copy(
                loading = false,
                embeddedEnabled = embedded,
                embeddedEngine = embeddedEngine,
                ollamaUrl = ollamaUrl,
                ollamaModel = ollamaModel,
                updateSource = manifestUpdateSource,
                onboardingCompleted = onboardingCompleted,
                clientManifest = manifest,
                auth = auth,
                backendUrl = url,
                selectedModel = repo.selectedModel()
            )
            if (auth.authenticated) refreshPostLogin()
            checkForUpdateSilently()
            startPeriodicUpdateChecks()
        }
    }

    fun setBackendUrl(url: String) {
        viewModelScope.launch {
            repo.setBackendUrl(url)
            _state.value = _state.value.copy(backendUrl = url)
            runCatching { repo.health() }.onSuccess { health ->
                _state.value = _state.value.copy(health = health)
            }
            runCatching { repo.clientManifest() }.onSuccess { manifest ->
                val next = _state.value.copy(clientManifest = manifest)
                val source = updateSourceAllowedByManifest(next)
                if (source != next.updateSource) {
                    settingsStore.setUpdateSource(source)
                }
                _state.value = next.copy(updateSource = source)
            }
            if (_state.value.auth.authenticated) {
                runCatching { repo.models() }.onSuccess { list ->
                    _state.value = _state.value.copy(
                        models = list,
                        selectedModel = selectUsableModel(_state.value.selectedModel, list)
                    )
                }
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, notice = null)
            runCatching { repo.login(username, password) }
                .onSuccess { auth ->
                    _state.value = _state.value.copy(loading = false, auth = auth)
                    refreshPostLogin()
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(loading = false, error = t.message)
                }
        }
    }

    fun loginWithPasskey(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, notice = null)
            runCatching { repo.loginWithPasskey(context) }
                .onSuccess { auth ->
                    _state.value = _state.value.copy(loading = false, auth = auth)
                    refreshPostLogin()
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = mobilePasskeySignInError(t)
                    )
                }
        }
    }

    fun startOidcSignIn() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.startOidcSignIn(OIDC_REDIRECT_URI) }
                .onSuccess { launch ->
                    pendingOidcLaunch = launch
                    _state.value = _state.value.copy(loading = false)
                    openExternalUrl(launch.authorizationUrl)
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = t.message ?: "OIDC sign-in is not configured"
                    )
                }
        }
    }

    fun completeOidcSignIn(uri: Uri?) {
        if (uri == null) return
        val launch = pendingOidcLaunch
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (launch == null) {
            _state.value = _state.value.copy(error = "OIDC callback arrived without a pending sign-in")
            return
        }
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "OIDC callback was missing code or state")
            return
        }
        if (state != launch.state) {
            _state.value = _state.value.copy(error = "OIDC state mismatch")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                repo.completeOidcSignIn(
                    code = code,
                    state = state,
                    redirectUri = launch.redirectUri,
                    codeVerifier = launch.codeVerifier
                )
            }.onSuccess { auth ->
                pendingOidcLaunch = null
                _state.value = _state.value.copy(loading = false, auth = auth)
                refreshPostLogin()
            }.onFailure { t ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = t.message ?: "OIDC sign-in failed"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            streamJob?.cancel()
            repo.logout()
            val current = _state.value
            _state.value = AppUiState(
                backendUrl = current.backendUrl,
                embeddedEnabled = current.embeddedEnabled,
                embeddedEngine = current.embeddedEngine,
                ollamaUrl = current.ollamaUrl,
                ollamaModel = current.ollamaModel,
                updateSource = current.updateSource,
                onboardingCompleted = current.onboardingCompleted
            )
        }
    }

    fun refreshChats() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.chats() }
                .onSuccess { _state.value = _state.value.copy(loading = false, chats = it) }
                .onFailure { t -> _state.value = _state.value.copy(loading = false, error = t.message) }
        }
    }

    fun refreshActiveChat() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, notice = null)
            runCatching { repo.chats() }
                .onSuccess { chats ->
                    val activeId = _state.value.activeChat?.id
                    val refreshedActive = activeId?.let { id -> chats.firstOrNull { it.id == id } }
                    _state.value = _state.value.copy(
                        loading = false,
                        chats = chats,
                        activeChat = refreshedActive ?: _state.value.activeChat,
                        activeMessages = refreshedActive?.session?.messages
                            ?: _state.value.activeMessages,
                        streamBuffer = ""
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = t.message ?: "Refresh failed"
                    )
                }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            runCatching { repo.models() }
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        models = list,
                        selectedModel = selectUsableModel(_state.value.selectedModel, list)
                    )
                }
                .onFailure { t -> _state.value = _state.value.copy(error = t.message) }
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            runCatching { repo.clientManifest() }
                .onSuccess {
                    val next = _state.value.copy(clientManifest = it)
                    val source = updateSourceAllowedByManifest(next)
                    if (source != next.updateSource) {
                        settingsStore.setUpdateSource(source)
                    }
                    _state.value = next.copy(updateSource = source)
                }
            runCatching { repo.health() }
                .onSuccess { _state.value = _state.value.copy(health = it) }
                .onFailure { t -> _state.value = _state.value.copy(error = t.message) }
        }
    }

    fun refreshPasskeys() {
        if (!_state.value.auth.authenticated) {
            _state.value = _state.value.copy(
                passkeyProvider = null,
                passkeyCredentials = emptyList(),
                passkeyLoading = false,
                notice = null
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null, notice = null)
            runCatching { repo.passkeyCredentials() }
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        passkeyProvider = response.provider,
                        passkeyCredentials = response.credentials
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        error = t.message ?: "Could not load passkeys"
                    )
                }
        }
    }

    fun registerPasskey(context: Context) {
        if (!_state.value.auth.authenticated) {
            _state.value = _state.value.copy(
                error = "Sign in with password once before adding a passkey on this phone.",
                notice = null
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null, notice = null)
            runCatching { repo.registerPasskey(context) }
                .onSuccess { response ->
                    val added = response.credential != null ||
                        response.credentials.size > _state.value.passkeyCredentials.size
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        passkeyProvider = response.provider ?: _state.value.passkeyProvider,
                        passkeyCredentials = response.credentials,
                        notice = if (added)
                            "Passkey saved. Sign out, then use existing passkey to test this phone."
                        else
                            "Passkey setup returned, but no new credential was reported. Tap Refresh or try another provider."
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        error = mobilePasskeyRegistrationError(t),
                        notice = null
                    )
                }
        }
    }

    fun revokePasskey(credentialId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null, notice = null)
            runCatching { repo.revokePasskey(credentialId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        passkeyCredentials = _state.value.passkeyCredentials
                            .filterNot { item -> item.credentialId == credentialId },
                        notice = "Passkey removed."
                    )
                    refreshPasskeys()
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        error = t.message ?: "Could not remove passkey"
                    )
                }
        }
    }

    fun importSavedChatRecovery(recoverySecret: String, recoveryBundleJson: String) {
        if (!_state.value.auth.authenticated) {
            _state.value = _state.value.copy(
                error = "Sign in before importing a saved-chat recovery key.",
                notice = null
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null, notice = null)
            var importWasTwoPartBundle = false
            runCatching {
                val bundle = parseSavedChatRecoveryImport(importJson, recoveryBundleJson)
                val bundledSecret = bundle["recovery_secret"]?.jsonPrimitive?.contentOrNull.orEmpty()
                importWasTwoPartBundle = bundledSecret.isBlank()
                val resolvedSecret = recoverySecret.trim().ifBlank { bundledSecret.trim() }
                require(resolvedSecret.isNotBlank()) {
                    "This is an older two-part recovery bundle. Since you do not have the recovery secret, go to the web app, open Settings > Privacy/Security, click Copy Android import kit, then paste that full new JSON here with the recovery secret field blank. The new JSON says \"kit_type\":\"one_paste_android_import\" and includes \"recovery_secret\"."
                }
                repo.importSavedChatRecoveryEnvelope(resolvedSecret, bundle)
            }
                .onSuccess { epoch ->
                    val chats = runCatching { repo.chats() }.getOrDefault(_state.value.chats)
                    val activeId = _state.value.activeChat?.id
                    val refreshedActive = activeId?.let { id -> chats.firstOrNull { it.id == id } }
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        chats = chats,
                        activeChat = refreshedActive ?: _state.value.activeChat,
                        activeMessages = refreshedActive?.session?.messages ?: _state.value.activeMessages,
                        notice = "Saved-chat recovery key imported for epoch $epoch.",
                        error = null
                    )
            }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        error = savedChatRecoveryImportError(t, importWasTwoPartBundle),
                        notice = null
                    )
                }
        }
    }

    private fun savedChatRecoveryImportError(t: Throwable, importWasTwoPartBundle: Boolean = false): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("unexpected JSON", ignoreCase = true) ||
                message.contains("Expected", ignoreCase = true) ->
                "Android import kit JSON is not valid. Paste the full kit copied from web Settings > Privacy/Security > Copy Android import kit."
            message.contains("BAD_DECRYPT", ignoreCase = true) ||
                message.contains("did not unlock", ignoreCase = true) ->
                if (importWasTwoPartBundle) {
                    "This is a two-part recovery bundle and the recovery secret entered here did not match it. If you do not know the exact secret, copy a fresh one-paste Android import kit from the web app. The fresh JSON says \"kit_type\":\"one_paste_android_import\" and includes \"recovery_secret\"; leave the Recovery secret field blank."
                } else {
                    "Saved-chat recovery did not unlock. Paste a fresh one-paste Android import kit copied from the same signed-in browser account. The JSON should say \"kit_type\":\"one_paste_android_import\" and include \"recovery_secret\"."
                }
            message.contains("two-part recovery bundle", ignoreCase = true) -> message
            message.isNotBlank() -> message
            else -> "Saved-chat recovery import failed."
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            repo.setSelectedModel(modelId)
            _state.value = _state.value.copy(selectedModel = modelId)
        }
    }

    fun openChat(chat: ChatRecord) {
        val messages = chat.session?.messages.orEmpty()
        _state.value = _state.value.copy(
            activeChat = chat,
            activeMessages = messages,
            streamBuffer = ""
        )
    }

    fun startNewChat() {
        _state.value = _state.value.copy(
            activeChat = null,
            activeMessages = emptyList(),
            streamBuffer = ""
        )
    }

    fun sendMessage(text: String) = sendMessageInternal(text)

    fun retryLastMessage() {
        val prompt = _state.value.lastFailedPrompt?.takeIf { it.isNotBlank() } ?: return
        sendMessageInternal(prompt)
    }

    private fun sendMessageInternal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.streaming) return
        val model = _state.value.selectedModel ?: run {
            _state.value = _state.value.copy(error = "Select a model first")
            return
        }
        if (!repo.hasSharedSavedChatKey()) {
            _state.value = _state.value.copy(
                error = "Import the shared saved-chat E2EE key in Settings before sending synced encrypted chats.",
                lastFailedPrompt = trimmed
            )
            return
        }
        val userMsg = ChatMessage(role = "user", content = trimmed, createdAt = nowIsoTimestamp())
        val previousHistory = _state.value.activeMessages
        val previousChat = _state.value.activeChat
        val nextHistory = previousHistory + userMsg
        _state.value = _state.value.copy(
            activeMessages = nextHistory,
            streaming = true,
            streamBuffer = "",
            streamStartedAtMs = System.currentTimeMillis(),
            streamApproxTokens = 0,
            streamTokensPerSecond = 0.0,
            streamStatus = "Thinking",
            lastStreamMetric = "",
            lastFailedPrompt = null,
            error = null
        )
        streamJob = viewModelScope.launch {
            val acc = StringBuilder()
            val startedAtMs = _state.value.streamStartedAtMs
            var streamFailed = false
            var createdChatId: String? = null
            runCatching {
                val active = _state.value.activeChat
                val chat = active ?: run {
                    val created = repo.createChat(
                        title = trimmed.take(60),
                        messages = emptyList()
                    )
                    createdChatId = created.id
                    _state.value = _state.value.copy(
                        activeChat = created.copy(session = ChatSession(messages = nextHistory)),
                        chats = listOf(created) + _state.value.chats.filterNot { it.id == created.id }
                    )
                    created.copy(session = ChatSession(messages = nextHistory))
                }
                repo.streamReply(
                    model = model,
                    messages = nextHistory,
                    chatId = chat.id,
                    workspaceId = chat.workspaceId,
                    projectId = chat.projectId
                ).collect { evt ->
                    when (evt) {
                        is StreamEvent.Delta -> {
                            acc.append(evt.text)
                            val tokenCount = _state.value.streamApproxTokens +
                                estimateStreamTokens(evt.text)
                            val elapsedSeconds = maxOf(
                                0.25,
                                (System.currentTimeMillis() - startedAtMs) / 1000.0
                            )
                            _state.value = _state.value.copy(
                                streamBuffer = acc.toString(),
                                streamApproxTokens = tokenCount,
                                streamTokensPerSecond = tokenCount / elapsedSeconds,
                                streamStatus = "Streaming"
                            )
                        }
                        is StreamEvent.Thinking -> {
                            val tokenCount = maxOf(_state.value.streamApproxTokens, evt.tokens)
                            val elapsedSeconds = maxOf(
                                0.25,
                                (System.currentTimeMillis() - startedAtMs) / 1000.0
                            )
                            _state.value = _state.value.copy(
                                streamApproxTokens = tokenCount,
                                streamTokensPerSecond = tokenCount / elapsedSeconds,
                                streamStatus = "Thinking"
                            )
                        }
                        is StreamEvent.Error -> {
                            streamFailed = true
                            restoreFailedSend(
                                previousHistory = previousHistory,
                                previousChat = previousChat,
                                createdChatId = createdChatId,
                                failedPrompt = trimmed,
                                error = evt.message
                            )
                        }
                        is StreamEvent.Job -> {
                            _state.value = _state.value.copy(
                                streamStatus = streamStatusLabel(evt.status)
                            )
                        }
                        StreamEvent.Completed -> {
                            if (streamFailed) return@collect
                            val finalText = acc.toString()
                            if (finalText.isBlank()) {
                                restoreFailedSend(
                                    previousHistory = previousHistory,
                                    previousChat = previousChat,
                                    createdChatId = createdChatId,
                                    failedPrompt = trimmed,
                                    error = "No assistant response was received from the backend."
                                )
                                return@collect
                            }
                            val updated = _state.value.activeMessages +
                                ChatMessage(role = "assistant", content = finalText, createdAt = nowIsoTimestamp())
                            val finalMetric = formatStreamMetric(
                                status = "Done",
                                tokens = _state.value.streamApproxTokens,
                                tokensPerSecond = _state.value.streamTokensPerSecond
                            )
                            _state.value = _state.value.copy(
                                streaming = false,
                                activeMessages = updated,
                                activeChat = _state.value.activeChat?.copy(
                                    session = ChatSession(messages = updated)
                                ),
                                streamBuffer = "",
                                streamStartedAtMs = 0L,
                                streamApproxTokens = 0,
                                streamTokensPerSecond = 0.0,
                                lastStreamMetric = finalMetric,
                                lastFailedPrompt = null,
                                streamStatus = ""
                            )
                            persistActiveChat(updated)
                        }
                        else -> Unit
                    }
                }
            }.onFailure { t ->
                if (t is CancellationException) {
                    _state.value = _state.value.copy(
                        streaming = false,
                        streamBuffer = "",
                        streamStartedAtMs = 0L,
                        streamApproxTokens = 0,
                        streamTokensPerSecond = 0.0,
                        lastStreamMetric = "",
                        streamStatus = ""
                    )
                } else {
                    restoreFailedSend(
                        previousHistory = previousHistory,
                        previousChat = previousChat,
                        createdChatId = createdChatId,
                        failedPrompt = trimmed,
                        error = t.message ?: "Message failed"
                    )
                }
            }
        }
    }

    private fun restoreFailedSend(
        previousHistory: List<ChatMessage>,
        previousChat: ChatRecord?,
        createdChatId: String?,
        failedPrompt: String?,
        error: String
    ) {
        _state.value = _state.value.copy(
            activeMessages = previousHistory,
            activeChat = previousChat,
            chats = if (createdChatId == null) {
                _state.value.chats
            } else {
                _state.value.chats.filterNot { it.id == createdChatId }
            },
            streaming = false,
            streamBuffer = "",
            streamStartedAtMs = 0L,
            streamApproxTokens = 0,
            streamTokensPerSecond = 0.0,
            streamStatus = "",
            lastStreamMetric = "",
            lastFailedPrompt = failedPrompt,
            error = error
        )
    }

    private suspend fun persistActiveChat(messages: List<ChatMessage>) {
        val chat = _state.value.activeChat ?: return
        val title = chat.title.ifBlank {
            messages.firstOrNull { it.role == "user" }?.content?.take(60) ?: "New chat"
        }
        runCatching {
            repo.updateChat(chat = chat, title = title, messages = messages)
        }.onSuccess { saved ->
            val local = saved.copy(session = ChatSession(messages = messages))
            _state.value = _state.value.copy(
                activeChat = local,
                chats = listOf(local) + _state.value.chats.filterNot { it.id == local.id }
            )
        }.onFailure { t ->
            _state.value = _state.value.copy(
                notice = "Chat stayed local because encrypted save failed: ${t.message ?: "unknown error"}"
            )
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _state.value = _state.value.copy(
            streaming = false,
            streamStartedAtMs = 0L,
            streamBuffer = "",
            streamApproxTokens = 0,
            streamTokensPerSecond = 0.0,
            lastStreamMetric = "",
            streamStatus = ""
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun checkForUpdate() {
        checkForUpdate(showErrors = true)
    }

    private fun checkForUpdateSilently() {
        checkForUpdate(showErrors = false)
    }

    private fun startPeriodicUpdateChecks() {
        if (updateCheckJob != null) return
        updateCheckJob = viewModelScope.launch {
            while (true) {
                delay(UPDATE_CHECK_INTERVAL_MS)
                checkForUpdateSilently()
            }
        }
    }

    private fun checkForUpdate(showErrors: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                checkingUpdate = showErrors,
                error = if (showErrors) null else _state.value.error
            )
            runCatching { AppUpdateChecker(_state.value.updateSource).checkLatestDebugRelease() }
                .onSuccess { info ->
                    val shouldResetPrompt =
                        info.updateAvailable &&
                            info.versionCode != _state.value.updateInfo?.versionCode
                    _state.value = _state.value.copy(
                        checkingUpdate = false,
                        updateInfo = info,
                        updatePromptDismissed = if (shouldResetPrompt) false
                            else _state.value.updatePromptDismissed
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        checkingUpdate = false,
                        error = if (showErrors) t.message ?: "Update check failed"
                            else _state.value.error
                    )
                }
        }
    }

    fun dismissUpdatePrompt() {
        _state.value = _state.value.copy(updatePromptDismissed = true)
    }

    fun openUpdateReleasePage() {
        openExternalUrl(
            _state.value.updateInfo?.releasePageUrl
                ?: defaultUpdateReleasePage()
        )
    }

    private fun defaultUpdateReleasePage(): String =
        when (SettingsStore.normalizeUpdateSource(_state.value.updateSource)) {
            SettingsStore.UPDATE_SOURCE_GITHUB -> BuildConfig.APP_UPDATE_FALLBACK_RELEASE_PAGE_BASE
            else -> BuildConfig.APP_UPDATE_RELEASE_PAGE_BASE.ifBlank {
                BuildConfig.APP_UPDATE_FALLBACK_RELEASE_PAGE_BASE
            }
        }

    fun installLatestUpdate() {
        val url = _state.value.updateInfo?.apkDownloadUrl
        if (url.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "No APK download link found. Run update check first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(installingUpdate = true, error = null)
            runCatching { AppUpdateInstaller(appContext).downloadAndInstall(url) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        installingUpdate = false,
                        error = result.message
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        installingUpdate = false,
                        error = t.message ?: "Could not install update"
                    )
                }
        }
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
            .onFailure { t ->
                _state.value = _state.value.copy(error = t.message ?: "Could not open link")
            }
    }

    fun setEmbeddedBackend(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setEmbeddedEnabled(enabled)
            _state.value = _state.value.copy(embeddedEnabled = enabled)
            if (enabled) {
                ensureBackendRunning()
                val newUrl = SettingsStore.EMBEDDED_BACKEND_URL
                repo.setBackendUrl(newUrl)
                _state.value = _state.value.copy(backendUrl = newUrl)
                runCatching { repo.bootstrap() }.onSuccess { auth ->
                    _state.value = _state.value.copy(auth = auth)
                    if (auth.authenticated) refreshPostLogin()
                }
            } else {
                stopBackend()
            }
        }
    }

    fun setUpdateSource(source: String) {
        val normalized = SettingsStore.normalizeUpdateSource(source)
        viewModelScope.launch {
            settingsStore.setUpdateSource(normalized)
            _state.value = _state.value.copy(
                updateSource = normalized,
                updateInfo = null,
                updatePromptDismissed = false
            )
            checkForUpdateSilently()
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
            _state.value = _state.value.copy(onboardingCompleted = true, notice = null, error = null)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(false)
            _state.value = _state.value.copy(onboardingCompleted = false)
        }
    }

    fun setEmbeddedEngine(engine: String) {
        viewModelScope.launch {
            settingsStore.setEmbeddedEngine(engine)
            _state.value = _state.value.copy(embeddedEngine = engine)
            restartEmbeddedBackendIfNeeded()
        }
    }

    fun setOllamaSettings(baseUrl: String, model: String) {
        viewModelScope.launch {
            val cleanUrl = baseUrl.trim().ifBlank { SettingsStore.DEFAULT_OLLAMA_URL }
            val cleanModel = model.trim().ifBlank { SettingsStore.DEFAULT_OLLAMA_MODEL }
            settingsStore.setOllamaUrl(cleanUrl)
            settingsStore.setOllamaModel(cleanModel)
            _state.value = _state.value.copy(
                ollamaUrl = cleanUrl,
                ollamaModel = cleanModel
            )
            restartEmbeddedBackendIfNeeded()
        }
    }

    private fun ensureBackendRunning() {
        val intent = android.content.Intent(appContext, BackendService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopBackend() {
        val intent = android.content.Intent(appContext, BackendService::class.java)
        appContext.stopService(intent)
    }

    private fun restartEmbeddedBackendIfNeeded() {
        if (!_state.value.embeddedEnabled) return
        stopBackend()
        ensureBackendRunning()
        refreshModels()
        refreshHealth()
    }

    private suspend fun refreshPostLogin() {
        runCatching { repo.models() }.onSuccess { list ->
            _state.value = _state.value.copy(
                models = list,
                selectedModel = selectUsableModel(_state.value.selectedModel, list)
            )
        }
        runCatching { repo.chats() }.onSuccess { chats ->
            _state.value = _state.value.copy(chats = chats)
        }
        runCatching { repo.health() }.onSuccess { health ->
            _state.value = _state.value.copy(health = health)
        }
        runCatching { repo.passkeyCredentials() }.onSuccess { response ->
            _state.value = _state.value.copy(
                passkeyProvider = response.provider,
                passkeyCredentials = response.credentials
            )
        }
    }

    private fun selectUsableModel(
        current: String?,
        models: List<ModelDescriptor>
    ): String? {
        if (current != null && models.any { it.id == current }) return current
        return models.firstOrNull()?.id
    }

    class Factory(
        private val repo: NullXoidRepository,
        private val appContext: Context,
        private val settingsStore: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NullXoidViewModel(repo, appContext, settingsStore) as T
    }
}
