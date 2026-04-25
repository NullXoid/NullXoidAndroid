package com.nullxoid.android.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun setBackendUrl(url: String) {
        context.settingsDataStore.edit { it[backendUrlKey] = url }
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

    companion object {
        const val DEFAULT_BACKEND_URL = "http://localhost:8090"
        const val EMBEDDED_BACKEND_URL = "http://127.0.0.1:8090"
        const val EMBEDDED_ENGINE_ECHO = "echo"
        const val EMBEDDED_ENGINE_OLLAMA = "ollama"
        const val EMBEDDED_ENGINE_LLAMA_CPP = "llamacpp"
        const val DEFAULT_OLLAMA_URL = "http://localhost:11434"
        const val DEFAULT_OLLAMA_MODEL = "llama3.2:3b"
    }
}
