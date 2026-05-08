package com.nullxoid.android.ui.home

import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.data.model.StoreJobSummary

internal const val HOME_ROUTE_IMAGE = "image"
internal const val HOME_ROUTE_VIDEO = "video"
internal const val HOME_ROUTE_3D = "3d"
internal const val HOME_ROUTE_GALLERY = "gallery"
internal const val HOME_ROUTE_JOBS = "jobs"
internal const val HOME_ROUTE_SETTINGS = "settings"

data class HomeTile(
    val id: String,
    val label: String,
    val description: String,
    val primary: Boolean,
    val badge: String = ""
)

enum class HomeContinueDestination {
    Jobs,
    Gallery
}

val homeTiles = listOf(
    HomeTile(
        id = HOME_ROUTE_IMAGE,
        label = "Image",
        description = "Create a private picture.",
        primary = true
    ),
    HomeTile(
        id = HOME_ROUTE_VIDEO,
        label = "Video",
        description = "Create an experimental motion clip.",
        primary = true
    ),
    HomeTile(
        id = HOME_ROUTE_3D,
        label = "3D",
        description = "Image-to-3D is available. Mesh and wrapping are experimental.",
        primary = true,
        badge = "Beta"
    ),
    HomeTile(
        id = HOME_ROUTE_GALLERY,
        label = "Gallery",
        description = "Review generated media.",
        primary = false
    ),
    HomeTile(
        id = HOME_ROUTE_JOBS,
        label = "Jobs",
        description = "Check running work.",
        primary = false
    ),
    HomeTile(
        id = HOME_ROUTE_SETTINGS,
        label = "Settings",
        description = "Account and app controls.",
        primary = false
    )
)

fun activeHomeJobCount(jobs: List<StoreJobSummary>, activeStoreJobId: String): Int {
    val activeStatuses = setOf(
        "pending_approval",
        "approved",
        "queued",
        "queued_connector",
        "running_provider",
        "uploading_artifact"
    )
    val activeFromList = jobs.count { job ->
        job.status.lowercase() in activeStatuses || job.canCancel
    }
    return maxOf(activeFromList, if (activeStoreJobId.isNotBlank()) 1 else 0)
}

fun latestReadyHomeArtifact(items: List<StoreArtifactRef>): StoreArtifactRef? =
    items.firstOrNull { item ->
        item.status.equals("ready", ignoreCase = true) ||
            item.status.equals("completed", ignoreCase = true)
    } ?: items.firstOrNull()

fun homeContinueDestination(activeJobs: Int): HomeContinueDestination =
    if (activeJobs > 0) HomeContinueDestination.Jobs else HomeContinueDestination.Gallery

fun homeContinueText(activeJobs: Int, latestArtifact: StoreArtifactRef?): String =
    when {
        activeJobs > 0 && latestArtifact != null ->
            "$activeJobs job${if (activeJobs == 1) "" else "s"} running • latest result ready"
        activeJobs > 0 ->
            "$activeJobs job${if (activeJobs == 1) "" else "s"} running"
        latestArtifact != null ->
            "Latest result ready"
        else ->
            "Start with Image, Video, or 3D"
    }
