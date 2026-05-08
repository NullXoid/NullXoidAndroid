package com.nullxoid.android.ui.home

import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.data.model.StoreJobSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiModelsTest {
    @Test
    fun homeTilesExposePrimaryWorkflowsAndSafe3dBetaCopy() {
        assertEquals(
            listOf("Image", "Video", "3D", "Gallery", "Jobs", "Settings"),
            homeTiles.map { it.label }
        )

        val model3d = homeTiles.single { it.id == HOME_ROUTE_3D }
        assertEquals("Beta", model3d.badge)
        assertTrue(model3d.description.contains("Mesh and wrapping are experimental"))
        assertEquals(3, homeTiles.count { it.primary })
    }

    @Test
    fun continueCardRoutesToJobsWhenWorkIsActive() {
        val jobs = listOf(
            StoreJobSummary(storeJobId = "job-1", status = "queued_connector", canCancel = true)
        )

        val activeCount = activeHomeJobCount(jobs, activeStoreJobId = "")

        assertEquals(1, activeCount)
        assertEquals(HomeContinueDestination.Jobs, homeContinueDestination(activeCount))
        assertEquals("1 job running", homeContinueText(activeCount, latestArtifact = null))
    }

    @Test
    fun continueCardRoutesToGalleryWhenOnlyResultIsReady() {
        val latest = StoreArtifactRef(
            artifactId = "artifact-safe-id",
            mimeType = "image/png",
            status = "ready"
        )

        assertEquals(latest, latestReadyHomeArtifact(listOf(latest)))
        assertEquals(HomeContinueDestination.Gallery, homeContinueDestination(activeJobs = 0))
        assertEquals("Latest result ready", homeContinueText(activeJobs = 0, latestArtifact = latest))
    }
}
