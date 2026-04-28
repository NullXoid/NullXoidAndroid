package com.nullxoid.android.data.api

import com.nullxoid.android.BuildConfig
import com.nullxoid.android.data.prefs.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendEndpointTest {
    @Test
    fun releasePublicBackendUrlTargetsHostedApi() {
        assertEquals(
            "https://api.echolabs.diy/nullxoid",
            BuildConfig.PUBLIC_BACKEND_URL
        )
        assertEquals(
            BackendEndpoint.PUBLIC_ECHOLABS_URL,
            BackendEndpoint.normalize(BuildConfig.PUBLIC_BACKEND_URL)
        )
    }

    @Test
    fun resolvePreservesMountedPublicApiBase() {
        assertEquals(
            "https://api.echolabs.diy/nullxoid/auth/login",
            BackendEndpoint.resolve("https://api.echolabs.diy/nullxoid/", "/auth/login")
        )
    }

    @Test
    fun normalizesHostOnlyPublicApiToHttps() {
        assertEquals(
            "https://api.echolabs.diy/nullxoid",
            BackendEndpoint.normalize("api.echolabs.diy/nullxoid")
        )
    }

    @Test
    fun normalizesLocalAndLanHostsToHttp() {
        assertEquals("http://localhost:8090", BackendEndpoint.normalize("localhost:8090"))
        assertEquals("http://192.168.1.201", BackendEndpoint.normalize("192.168.1.201"))
        assertEquals("http://10.0.2.2:8090", BackendEndpoint.normalize("10.0.2.2:8090"))
    }

    @Test
    fun normalizesUpdateSourceSelection() {
        assertEquals(SettingsStore.UPDATE_SOURCE_AUTO, SettingsStore.normalizeUpdateSource(""))
        assertEquals(SettingsStore.UPDATE_SOURCE_FORGEJO, SettingsStore.normalizeUpdateSource("Forgejo"))
        assertEquals(SettingsStore.UPDATE_SOURCE_GITHUB, SettingsStore.normalizeUpdateSource("github"))
        assertEquals(SettingsStore.UPDATE_SOURCE_AUTO, SettingsStore.normalizeUpdateSource("unknown"))
    }
}
