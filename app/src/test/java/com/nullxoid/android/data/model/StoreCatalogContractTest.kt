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
    fun parsesCreativeWorkflowsSafeCatalog() {
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
                },
                {
                  "id": "local-video-studio",
                  "name": "Local Video Studio",
                  "category": "creative-workflows",
                  "categoryLabel": "Creative Workflows",
                  "subcategory": "video-generation",
                  "description": "Generate private local videos through an approval-gated backend workflow.",
                  "status": "alpha",
                  "enabled": true,
                  "visibility": "local-debug",
                  "platforms": ["web", "android", "windows"],
                  "capabilities": ["suite.media.video.generate"],
                  "requiresApproval": true,
                  "approvalRoute": {
                    "title": "Approve video generation?",
                    "summary": "Local Video Studio wants to generate a video.",
                    "risk": "medium",
                    "action": "media.video.generate.local"
                  },
                  "providerKinds": ["mock-video", "local-video-engine"],
                  "permissions": [],
                  "routes": {
                    "detail": "/api/store/addons/local-video-studio",
                    "action": "/api/store/addons/local-video-studio/actions/media.video.generate.local",
                    "gallery": "/api/store/addons/local-video-studio/gallery"
                  }
                },
                {
                  "id": "local-3d-studio",
                  "name": "Local 3D Studio",
                  "category": "creative-workflows",
                  "categoryLabel": "Creative Workflows",
                  "subcategory": "3d-generation",
                  "description": "Generate private GLB/glTF model artifacts through an approval-gated backend workflow.",
                  "status": "alpha",
                  "enabled": true,
                  "visibility": "local-debug",
                  "platforms": ["web", "android", "windows"],
                  "capabilities": ["suite.media.model3d.generate"],
                  "requiresApproval": true,
                  "approvalRoute": {
                    "title": "Approve 3D model generation?",
                    "summary": "Local 3D Studio wants to generate a 3D model.",
                    "risk": "medium",
                    "action": "media.model3d.generate.local"
                  },
                  "providerKinds": ["mock-3d", "local-3d-engine"],
                  "defaultOutputFormat": "glb",
                  "outputFormats": ["glb", "gltf"],
                  "permissions": [],
                  "routes": {
                    "detail": "/api/store/addons/local-3d-studio",
                    "action": "/api/store/addons/local-3d-studio/actions/media.model3d.generate.local",
                    "gallery": "/api/store/addons/local-3d-studio/gallery"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val addon = decoded.addons.first { it.id == "local-image-studio" }
        val video = decoded.addons.first { it.id == "local-video-studio" }
        val model3d = decoded.addons.first { it.id == "local-3d-studio" }
        assertEquals("Creative Workflows", decoded.categories.single().label)
        assertEquals(3, decoded.addons.size)
        assertEquals("local-image-studio", addon.id)
        assertEquals("Local Image Studio", addon.name)
        assertEquals("suite.media.image.generate", addon.capabilities.single())
        assertEquals("media.image.generate.local", addon.approvalRoute?.action)
        assertTrue(addon.requiresApproval)
        assertTrue(addon.platforms.contains("android"))
        assertEquals("suite.media.video.generate", video.capabilities.single())
        assertEquals("media.video.generate.local", video.approvalRoute?.action)
        assertEquals("suite.media.model3d.generate", model3d.capabilities.single())
        assertEquals("media.model3d.generate.local", model3d.approvalRoute?.action)
        assertEquals("glb", model3d.defaultOutputFormat)
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
