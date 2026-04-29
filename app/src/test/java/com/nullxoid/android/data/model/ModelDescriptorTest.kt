package com.nullxoid.android.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDescriptorTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodesArrayCapabilities() {
        val decoded = json.decodeFromString<ModelListResponse>(
            """{"models":[{"id":"model-a","capabilities":["chat","streaming"]}]}"""
        )

        assertEquals(listOf("chat", "streaming"), decoded.models.single().capabilities)
    }

    @Test
    fun decodesObjectCapabilitiesFromHostedBackend() {
        val decoded = json.decodeFromString<ModelListResponse>(
            """{"models":[{"id":"model-a","capabilities":{"run_model":true,"warn_model":true,"disabled":false}}]}"""
        )

        assertEquals(listOf("run_model", "warn_model"), decoded.models.single().capabilities)
    }
}
