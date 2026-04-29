package com.nullxoid.android.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nullxoid.android.BuildConfig
import com.nullxoid.android.data.api.BackendEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("nullxoid_settings")

class SettingsStore(private val context: Context) {

    private val backendUrlKey = stringPreferencesKey("backend_url")
    private val selectedModelKey = stringPreferencesKey("selected_model")
    private val embeddedEnabledKey = booleanPreferencesKey("embedded_enabled")
    private val embeddedEngineKey = stringPreferencesKey("embedded_engine")
    private val ollamaUrlKey = stringPreferencesKey("ollama_url")
    private val ollamaModelKey = stringPreferencesKey("ollama_model")
    private val updateSourceKey = stringPreferencesKey("update_source")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    val backendUrl: Flow<String> = context.settingsDataStore.data.map {
        it[backendUrlKey] ?: DEFAULT_BACKEND_URL
    }

    val selectedModel: Flow<String?> = context.settingsDataStore.data.map {
        it[selectedModelKey]
    }

    val embeddedEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[embeddedEnabledKey] ?: false
    }

    val embeddedEngine: Flow<String> = context.settingsDataStore.data.map {
        it[embeddedEngineKey] ?: EMBEDDED_ENGINE_ECHO
    }

    val ollamaUrl: Flow<String> = context.settingsDataStore.data.map {
        it[ollamaUrlKey] ?: DEFAULT_OLLAMA_URL
    }

    val ollamaModel: Flow<String> = context.settingsDataStore.data.map {
        it[ollamaModelKey] ?: DEFAULT_OLLAMA_MODEL
    }

    val updateSource: Flow<String> = context.settingsDataStore.data.map {
        normalizeUpdateSource(it[updateSourceKey])
    }

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map {
        it[onboardingCompletedKey] ?: false
    }

    suspend fun setBackendUrl(url: String) {
        context.settingsDataStore.edit { it[backendUrlKey] = BackendEndpoint.normalize(url) }
    }

    suspend fun setSelectedModel(modelId: String) {
        context.settingsDataStore.edit { it[selectedModelKey] = modelId }
    }

    suspend fun setEmbeddedEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[embeddedEnabledKey] = enabled }
    }

    suspend fun setEmbeddedEngine(engine: String) {
        context.settingsDataStore.edit { it[embeddedEngineKey] = engine }
    }

    suspend fun setOllamaUrl(url: String) {
        context.settingsDataStore.edit { it[ollamaUrlKey] = url }
    }

    suspend fun setOllamaModel(model: String) {
        context.settingsDataStore.edit { it[ollamaModelKey] = model }
    }

    suspend fun setUpdateSource(source: String) {
        context.settingsDataStore.edit { it[updateSourceKey] = normalizeUpdateSource(source) }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[onboardingCompletedKey] = completed }
    }

    companion object {
        val DEFAULT_BACKEND_URL: String = BackendEndpoint.normalize(
            BuildConfig.DEFAULT_BACKEND_URL,
            BackendEndpoint.PUBLIC_ECHOLABS_URL
        )
        val PUBLIC_BACKEND_URL: String = BackendEndpoint.normalize(
            BuildConfig.PUBLIC_BACKEND_URL,
            BackendEndpoint.PUBLIC_ECHOLABS_URL
        )
        const val LOCAL_BACKEND_URL = BackendEndpoint.LOCAL_DEFAULT_URL
        const val EMBEDDED_BACKEND_URL = BackendEndpoint.EMBEDDED_URL
        const val EMBEDDED_ENGINE_ECHO = "echo"
        const val EMBEDDED_ENGINE_OLLAMA = "ollama"
        const val EMBEDDED_ENGINE_LLAMA_CPP = "llamacpp"
        const val DEFAULT_OLLAMA_URL = "http://localhost:11434"
        const val DEFAULT_OLLAMA_MODEL = "llama3.2:3b"
        const val UPDATE_SOURCE_AUTO = "auto"
        const val UPDATE_SOURCE_FORGEJO = "forgejo"
        const val UPDATE_SOURCE_GITHUB = "github"

        fun normalizeUpdateSource(source: String?): String =
            when (source?.trim()?.lowercase()) {
                UPDATE_SOURCE_FORGEJO -> UPDATE_SOURCE_FORGEJO
                UPDATE_SOURCE_GITHUB -> UPDATE_SOURCE_GITHUB
                else -> UPDATE_SOURCE_AUTO
            }
    }
}
