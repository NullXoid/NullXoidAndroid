package com.nullxoid.android.ui.store

import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.data.model.StoreGalleryResponse
import com.nullxoid.android.data.model.StoreJobType
import com.nullxoid.android.data.api.NullXoidApiJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorePrereleasePolishTest {
    @Test
    fun mediaSelectorShowsImageVideoAnd3dWithSelectedState() {
        val addons = listOf(
            StoreAddon(id = "local-image-studio", category = "creative-workflows"),
            StoreAddon(id = "local-video-studio", category = "creative-workflows"),
            StoreAddon(id = "local-3d-studio", category = "creative-workflows")
        )

        val options = mediaOptions(addons, selectedAddonId = "local-video-studio")

        assertEquals(listOf("Image", "Video", "3D"), options.map { it.label })
        assertTrue(options.single { it.label == "Video" }.selected)
        assertFalse(options.single { it.label == "Image" }.selected)
        assertFalse(options.single { it.label == "3D" }.selected)
    }

    @Test
    fun profileSelectorUsesSafeCatalogValuesWhenPresent() {
        val addon = StoreAddon(
            id = "local-image-studio",
            jobTypes = listOf(
                StoreJobType(
                    id = "image-standard",
                    label = "Standard image",
                    description = "Balanced private image generation.",
                    imageSize = "1024x1024",
                    format = "png"
                )
            )
        )

        val profiles = profileOptions(addon)

        assertEquals("image-standard", profiles.single().id)
        assertEquals("Standard image", profiles.single().label)
        assertEquals("1024x1024", profiles.single().imageSize)
        assertFalse(profiles.joinToString(" ").contains("\\"))
        assertFalse(profiles.joinToString(" ").contains("http://"))
    }

    @Test
    fun completedPollingStatusBeatsStalePendingApproval() {
        val pending = friendlyStoreStatus("pending_approval")
        val completed = friendlyStoreStatus("completed")

        assertEquals("Waiting for approval", pending.label)
        assertEquals("Ready", completed.label)
        assertTrue(completed.terminal)
        assertFalse(completed.failed)
    }

    @Test
    fun galleryCardTitlesHideRawArtifactIdUntilDetails() {
        val item = StoreArtifactRef(
            artifactId = "artifact-secret-looking-id",
            mimeType = "image/png",
            status = "ready"
        )

        val title = safeArtifactTitle(item, 0)

        assertEquals("Image 1", title)
        assertFalse(title.contains(item.artifactId))
        assertEquals("Image", artifactTypeLabel(item))
    }

    @Test
    fun statusLabelsHideBackendCodesByDefault() {
        val failed = friendlyStoreStatus(status = "failed", errorCode = "PROVIDER_EXECUTION_FAILED")

        assertEquals("Failed", failed.label)
        assertTrue(failed.terminal)
        assertTrue(failed.failed)
        assertFalse(failed.helper.contains("PROVIDER_EXECUTION_FAILED"))
    }

    @Test
    fun galleryResponseCoercesNullPreviewUrlsToSafeDefaults() {
        val payload = """
            {
              "ok": true,
              "addonId": "local-image-studio",
              "items": [
                {
                  "artifactId": "artifact-safe-id",
                  "thumbnailUrl": "/artifacts/artifact-safe-id/thumb",
                  "previewUrl": null,
                  "modelPreviewUrl": null,
                  "mimeType": "image/png",
                  "status": "ready"
                }
              ]
            }
        """.trimIndent()

        val gallery = NullXoidApiJson.decodeFromString<StoreGalleryResponse>(payload)
        val item = gallery.items.single()

        assertEquals("", item.previewUrl)
        assertEquals("", item.modelPreviewUrl)
        assertEquals("/artifacts/artifact-safe-id/thumb", item.thumbnailUrl)
        assertFalse(item.toString().contains("workflow"))
        assertFalse(item.toString().contains("token"))
    }

    @Test
    fun pendingOrBlankArtifactsAreNotLatestResults() {
        val pendingPlaceholder = StoreArtifactRef(
            artifactId = "",
            mimeType = "",
            status = "pending_approval"
        )
        val completed = StoreArtifactRef(
            artifactId = "artifact-ready",
            mimeType = "image/png",
            status = "ready"
        )

        assertFalse(isRenderableLatestResult(pendingPlaceholder))
        assertEquals(completed, latestRenderableArtifact(listOf(pendingPlaceholder, completed)))
    }

    @Test
    fun previewLoadingRequiresSafePreviewPathAndArtifactIdentity() {
        val blankPath = StoreArtifactRef(
            artifactId = "artifact-ready",
            mimeType = "image/png",
            status = "ready"
        )
        val blankArtifact = blankPath.copy(
            artifactId = "",
            thumbnailUrl = "/artifacts/artifact-ready/thumb"
        )
        val safe = blankPath.copy(thumbnailUrl = "/artifacts/artifact-ready/thumb")

        assertFalse(previewLoadAllowed(blankPath))
        assertFalse(previewLoadAllowed(blankArtifact))
        assertTrue(previewLoadAllowed(safe))
    }

    @Test
    fun blankArtifactOrMimeDisablesArtifactActions() {
        assertFalse(isActionableArtifact(StoreArtifactRef(artifactId = "", mimeType = "image/png")))
        assertFalse(isActionableArtifact(StoreArtifactRef(artifactId = "artifact-ready", mimeType = "")))
        assertTrue(isActionableArtifact(StoreArtifactRef(artifactId = "artifact-ready", mimeType = "image/png")))
    }

    @Test
    fun artifactListKeysRemainUniqueForBlankOrDuplicateIds() {
        val first = StoreArtifactRef(artifactId = "artifact-duplicate", mimeType = "image/png")
        val second = StoreArtifactRef(artifactId = "artifact-duplicate", mimeType = "image/png")
        val blankA = StoreArtifactRef(createdAt = "2026-05-04T12:00:00Z")
        val blankB = StoreArtifactRef(createdAt = "2026-05-04T12:00:00Z")

        val keys = listOf(first, second, blankA, blankB)
            .mapIndexed { index, item -> stableArtifactListKey(item, index, "gallery") }

        assertEquals(keys.size, keys.toSet().size)
    }
}
