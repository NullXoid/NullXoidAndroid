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

    val backendUrl: Flow<String> = context.settingsDataStore.data.map {
        it[backendUrlKey] ?: DEFAULT_BACKEND_URL
    }

    val selectedModel: Flow<String?> = context.settingsDataStore.data.map {
        it[selectedModelKey]
    }

    val embeddedEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[embeddedEnabledKey] ?: false
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

    companion object {
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8090"
        const val EMBEDDED_BACKEND_URL = "http://127.0.0.1:8090"
    }
}
