package com.nullxoid.android.ui.store

import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.data.model.StoreJobType
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
}
