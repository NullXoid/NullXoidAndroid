package com.nullxoid.android.ui

import com.nullxoid.android.data.model.ClientManifest
import com.nullxoid.android.data.prefs.SettingsStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientManifestUiTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun updateSourcesComeFromBackendManifest() {
        val manifest = json.decodeFromString<ClientManifest>(
            """
            {
              "schema_version": 1,
              "platform": "android",
              "e2ee": {
                "status": "partial",
                "summary": "Saved data paths are encrypted.",
                "next": ["cross-device key handoff in native app"]
              },
              "updates": {
                "channels": ["auto", "forgejo"],
                "recommended_channel": "auto"
              }
            }
            """.trimIndent()
        )

        assertEquals("partial", manifest.e2ee?.status)
        assertEquals(listOf("cross-device key handoff in native app"), manifest.e2ee?.next)
        val state = AppUiState(
            updateSource = SettingsStore.UPDATE_SOURCE_GITHUB,
            clientManifest = manifest
        )

        assertEquals(
            listOf(SettingsStore.UPDATE_SOURCE_AUTO, SettingsStore.UPDATE_SOURCE_FORGEJO),
            availableUpdateSources(state)
        )
        assertEquals(SettingsStore.UPDATE_SOURCE_AUTO, updateSourceAllowedByManifest(state))
    }

    @Test
    fun missingManifestKeepsCurrentSelectedSourceOnly() {
        val state = AppUiState(updateSource = SettingsStore.UPDATE_SOURCE_FORGEJO)

        assertEquals(listOf(SettingsStore.UPDATE_SOURCE_FORGEJO), availableUpdateSources(state))
        assertEquals(SettingsStore.UPDATE_SOURCE_FORGEJO, updateSourceAllowedByManifest(state))
    }
}
