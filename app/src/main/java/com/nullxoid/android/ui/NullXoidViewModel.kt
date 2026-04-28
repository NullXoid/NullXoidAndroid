package com.nullxoid.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nullxoid.android.BuildConfig
import com.nullxoid.android.backend.BackendService
import com.nullxoid.android.data.auth.OidcLaunch
import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatSession
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val OIDC_REDIRECT_URI = "nullxoid://auth/oidc/callback"

data class AppUiState(
    val auth: AuthState = AuthState(),
    val loading: Boolean = false,
    val error: String? = null,
    val backendUrl: String = "",
    val models: List<ModelDescriptor> = emptyList(),
    val selectedModel: String? = null,
    val chats: List<ChatRecord> = emptyList(),
    val activeChat: ChatRecord? = null,
    val activeMessages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val streamBuffer: String = "",
    val health: HealthFeatures? = null,
    val embeddedEnabled: Boolean = false,
    val embeddedEngine: String = SettingsStore.EMBEDDED_ENGINE_ECHO,
    val ollamaUrl: String = SettingsStore.DEFAULT_OLLAMA_URL,
    val ollamaModel: String = SettingsStore.DEFAULT_OLLAMA_MODEL,
    val updateInfo: AppUpdateInfo? = null,
    val checkingUpdate: Boolean = false,
    val installingUpdate: Boolean = false,
    val updatePromptDismissed: Boolean = false,
    val passkeyProvider: PasskeyProviderStatus? = null,
    val passkeyCredentials: List<PasskeyCredentialRecord> = emptyList(),
    val passkeyLoading: Boolean = false,
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
            if (embedded) ensureBackendRunning()
            val auth = runCatching { repo.bootstrap() }.getOrElse { AuthState() }
            _state.value = _state.value.copy(
                loading = false,
                embeddedEnabled = embedded,
                embeddedEngine = embeddedEngine,
                ollamaUrl = ollamaUrl,
                ollamaModel = ollamaModel,
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
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
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
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.loginWithPasskey(context) }
                .onSuccess { auth ->
                    _state.value = _state.value.copy(loading = false, auth = auth)
                    refreshPostLogin()
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = t.message ?: "Passkey sign-in is not configured"
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
            _state.value = AppUiState(backendUrl = _state.value.backendUrl)
        }
    }

    fun refreshChats() {
        viewModelScope.launch {
            runCatching { repo.chats() }
                .onSuccess { _state.value = _state.value.copy(chats = it) }
                .onFailure { t -> _state.value = _state.value.copy(error = t.message) }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            runCatching { repo.models() }
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        models = list,
                        selectedModel = _state.value.selectedModel ?: list.firstOrNull()?.id
                    )
                }
                .onFailure { t -> _state.value = _state.value.copy(error = t.message) }
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
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
                passkeyLoading = false
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null)
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
            _state.value = _state.value.copy(error = "Sign in before adding a passkey")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null)
            runCatching { repo.registerPasskey(context) }
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        passkeyProvider = response.provider ?: _state.value.passkeyProvider,
                        passkeyCredentials = response.credentials
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        error = t.message ?: "Passkey enrollment failed"
                    )
                }
        }
    }

    fun revokePasskey(credentialId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(passkeyLoading = true, error = null)
            runCatching { repo.revokePasskey(credentialId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        passkeyLoading = false,
                        passkeyCredentials = _state.value.passkeyCredentials
                            .filterNot { item -> item.credentialId == credentialId }
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

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val model = _state.value.selectedModel ?: run {
            _state.value = _state.value.copy(error = "Select a model first")
            return
        }
        val userMsg = ChatMessage(role = "user", content = trimmed)
        val nextHistory = _state.value.activeMessages + userMsg
        _state.value = _state.value.copy(
            activeMessages = nextHistory,
            streaming = true,
            streamBuffer = "",
            error = null
        )
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val acc = StringBuilder()
            runCatching {
                val chatId = _state.value.activeChat?.id ?: run {
                    val created = repo.createChat(
                        title = trimmed.take(60),
                        messages = nextHistory
                    )
                    _state.value = _state.value.copy(
                        activeChat = created,
                        chats = listOf(created) + _state.value.chats.filterNot { it.id == created.id }
                    )
                    created.id
                }
                repo.streamReply(
                    model = model,
                    messages = nextHistory,
                    chatId = chatId
                ).collect { evt ->
                    when (evt) {
                        is StreamEvent.Delta -> {
                            acc.append(evt.text)
                            _state.value = _state.value.copy(streamBuffer = acc.toString())
                        }
                        is StreamEvent.Error -> {
                            _state.value = _state.value.copy(
                                streaming = false,
                                error = evt.message
                            )
                        }
                        StreamEvent.Completed -> {
                            val finalText = acc.toString()
                            val updated = _state.value.activeMessages +
                                ChatMessage(role = "assistant", content = finalText)
                            _state.value = _state.value.copy(
                                streaming = false,
                                activeMessages = updated,
                                activeChat = _state.value.activeChat?.copy(
                                    session = ChatSession(messages = updated)
                                ),
                                streamBuffer = ""
                            )
                            refreshChats()
                        }
                        else -> Unit
                    }
                }
            }.onFailure { t ->
                _state.value = _state.value.copy(streaming = false, error = t.message)
            }
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _state.value = _state.value.copy(streaming = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
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
            runCatching { AppUpdateChecker().checkLatestDebugRelease() }
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
                ?: BuildConfig.APP_UPDATE_RELEASE_PAGE_BASE.ifBlank {
                    BuildConfig.APP_UPDATE_FALLBACK_RELEASE_PAGE_BASE
                }
        )
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
                selectedModel = _state.value.selectedModel ?: list.firstOrNull()?.id
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
