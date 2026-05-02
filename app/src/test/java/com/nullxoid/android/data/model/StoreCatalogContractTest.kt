package com.nullxoid.android.data.model

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCatalogContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesLocalImageStudioSafeCatalog() {
        val decoded = json.decodeFromString<StoreCatalogResponse>(
            """
            {
              "ok": true,
              "categories": [
                {"id": "creative-workflows", "label": "Creative Workflows"}
              ],
              "addons": [
                {
                  "id": "local-image-studio",
                  "name": "Local Image Studio",
                  "category": "creative-workflows",
                  "categoryLabel": "Creative Workflows",
                  "subcategory": "image-generation",
                  "description": "Generate private local images through an approval-gated backend workflow.",
                  "status": "alpha",
                  "enabled": true,
                  "visibility": "local-debug",
                  "platforms": ["web", "android", "windows"],
                  "capabilities": ["suite.media.image.generate"],
                  "requiresApproval": true,
                  "approvalRoute": {
                    "title": "Approve image generation?",
                    "summary": "Local Image Studio wants to generate an image.",
                    "risk": "medium",
                    "action": "media.image.generate.local"
                  },
                  "providerKinds": ["mock", "local-image-engine", "local-video-engine"],
                  "permissions": [],
                  "routes": {
                    "detail": "/api/store/addons/local-image-studio",
                    "action": "/api/store/addons/local-image-studio/actions/media.image.generate.local",
                    "gallery": "/api/store/addons/local-image-studio/gallery"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val addon = decoded.addons.single()
        assertEquals("Creative Workflows", decoded.categories.single().label)
        assertEquals("local-image-studio", addon.id)
        assertEquals("Local Image Studio", addon.name)
        assertEquals("suite.media.image.generate", addon.capabilities.single())
        assertEquals("media.image.generate.local", addon.approvalRoute?.action)
        assertTrue(addon.requiresApproval)
        assertTrue(addon.platforms.contains("android"))
    }

    @Test
    fun androidSourcesDoNotEmbedProviderSecretsOrPrivatePaths() {
        val root = File("src/main")
        val forbidden = listOf(
            "CREATIVE_PROVIDER_BASE_URL",
            "CREATIVE_OUTPUT_DIR",
            "NULLBRIDGE_SERVICE_TOKEN",
            "provider-token",
            "service-token",
            "raw workflow",
            "rawWorkflow",
            "private artifact path",
            "comfyui",
            "ltx"
        )
        val haystack = root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filterNot { it.invariantSeparatorsPath.contains("/backend/nullbridge/") }
            .joinToString("\n") { it.readText() }
            .lowercase()

        forbidden.forEach { marker ->
            assertFalse("Android source leaked marker $marker", haystack.contains(marker.lowercase()))
        }
    }
}
