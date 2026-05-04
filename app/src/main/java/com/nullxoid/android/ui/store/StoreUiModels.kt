package com.nullxoid.android.ui.store

import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.data.model.StoreJobType

private const val IMAGE_ADDON_ID = "local-image-studio"
private const val VIDEO_ADDON_ID = "local-video-studio"
private const val MODEL3D_ADDON_ID = "local-3d-studio"

val creativeWorkflowAddonIds = setOf(IMAGE_ADDON_ID, VIDEO_ADDON_ID, MODEL3D_ADDON_ID)

data class StoreMediaOption(
    val mediaKind: String,
    val label: String,
    val addonId: String,
    val description: String,
    val selected: Boolean
)

data class StoreProfileOption(
    val id: String,
    val label: String,
    val description: String,
    val imageSize: String,
    val videoSize: String,
    val durationMs: Int,
    val format: String
)

data class FriendlyStoreStatus(
    val label: String,
    val helper: String,
    val progress: Float,
    val terminal: Boolean,
    val failed: Boolean
)

fun mediaKindForAddon(addonId: String): String =
    when (addonId) {
        VIDEO_ADDON_ID -> "video"
        MODEL3D_ADDON_ID -> "3d"
        else -> "image"
    }

fun mediaOptions(addons: List<StoreAddon>, selectedAddonId: String): List<StoreMediaOption> {
    val available = addons.associateBy { it.id }
    return listOf(
        StoreMediaOption(
            mediaKind = "image",
            label = "Image",
            addonId = IMAGE_ADDON_ID,
            description = "Generate a private picture.",
            selected = selectedAddonId == IMAGE_ADDON_ID
        ),
        StoreMediaOption(
            mediaKind = "video",
            label = "Video",
            addonId = VIDEO_ADDON_ID,
            description = "Create an experimental motion clip.",
            selected = selectedAddonId == VIDEO_ADDON_ID
        ),
        StoreMediaOption(
            mediaKind = "3d",
            label = "3D",
            addonId = MODEL3D_ADDON_ID,
            description = "Prepare a GLB/glTF model card.",
            selected = selectedAddonId == MODEL3D_ADDON_ID
        )
    ).filter { available.containsKey(it.addonId) }
}

fun profileOptions(addon: StoreAddon?): List<StoreProfileOption> {
    if (addon == null) return emptyList()
    val catalogProfiles = addon.jobTypes
        .filter { it.id.isNotBlank() }
        .map { it.toProfileOption(addon.id) }
    if (catalogProfiles.isNotEmpty()) return catalogProfiles

    return when (addon.id) {
        VIDEO_ADDON_ID -> listOf(
            StoreProfileOption(
                id = "video-short-motion",
                label = "Short motion preview",
                description = "Brief motion test with approval before provider execution.",
                imageSize = "1024x1024",
                videoSize = "1024x1024",
                durationMs = 4000,
                format = "mp4"
            ),
            StoreProfileOption(
                id = "video-standard",
                label = "Standard video",
                description = "Longer local-debug video job.",
                imageSize = "1024x1024",
                videoSize = "1024x1024",
                durationMs = 8000,
                format = "mp4"
            )
        )
        MODEL3D_ADDON_ID -> listOf(
            StoreProfileOption(
                id = "model3d-glb-draft",
                label = "GLB draft/model card",
                description = "Draft 3D artifact metadata using GLB/glTF terminology.",
                imageSize = "1024x1024",
                videoSize = "1024x1024",
                durationMs = 0,
                format = addon.defaultOutputFormat.ifBlank { "glb" }
            )
        )
        else -> listOf(
            StoreProfileOption(
                id = "image-fast-draft",
                label = "Fast draft",
                description = "Quick image proof for the approval flow.",
                imageSize = "768x768",
                videoSize = "768x768",
                durationMs = 0,
                format = "png"
            ),
            StoreProfileOption(
                id = "image-standard",
                label = "Standard image",
                description = "Balanced private image generation.",
                imageSize = "1024x1024",
                videoSize = "1024x1024",
                durationMs = 0,
                format = "png"
            ),
            StoreProfileOption(
                id = "image-widescreen-concept",
                label = "Widescreen concept",
                description = "Landscape composition for sharing or review.",
                imageSize = "1344x768",
                videoSize = "1344x768",
                durationMs = 0,
                format = "png"
            )
        )
    }
}

private fun StoreJobType.toProfileOption(addonId: String): StoreProfileOption {
    val fallback = profileOptions(StoreAddon(id = addonId)).firstOrNull()
    return StoreProfileOption(
        id = id,
        label = label.ifBlank { id.replace('-', ' ').replaceFirstChar { it.uppercase() } },
        description = description,
        imageSize = imageSize.ifBlank { fallback?.imageSize ?: "1024x1024" },
        videoSize = videoSize.ifBlank { imageSize.ifBlank { fallback?.videoSize ?: "1024x1024" } },
        durationMs = if (durationMs > 0) durationMs else fallback?.durationMs ?: 0,
        format = format.ifBlank { fallback?.format ?: "png" }
    )
}

fun friendlyStoreStatus(status: String?, errorCode: String? = null): FriendlyStoreStatus {
    val clean = status.orEmpty().lowercase()
    if (!errorCode.isNullOrBlank()) {
        return FriendlyStoreStatus(
            label = "Failed",
            helper = "Open Details for the backend code.",
            progress = 1f,
            terminal = true,
            failed = true
        )
    }
    return when (clean) {
        "", "ready" -> FriendlyStoreStatus("Ready to generate", "Choose a profile, then request approval.", 0f, false, false)
        "pending_approval" -> FriendlyStoreStatus("Waiting for approval", "Open NullBridge to approve. We'll keep checking automatically.", 0.25f, false, false)
        "approved" -> FriendlyStoreStatus("Approved", "Approval is recorded. The job is preparing to run.", 0.45f, false, false)
        "queued_connector" -> FriendlyStoreStatus("Queued", "The creative worker is readying the job.", 0.6f, false, false)
        "running_provider" -> FriendlyStoreStatus("Generating", "The provider is creating your media.", 0.78f, false, false)
        "uploading_artifact" -> FriendlyStoreStatus("Saving to private gallery", "The result is being stored safely.", 0.9f, false, false)
        "completed" -> FriendlyStoreStatus("Ready", "Generation completed. Open it from the gallery below.", 1f, true, false)
        "denied" -> FriendlyStoreStatus("Denied", "The approval request was denied.", 1f, true, true)
        "expired" -> FriendlyStoreStatus("Expired", "The approval window expired. Submit again when ready.", 1f, true, true)
        "cancelled" -> FriendlyStoreStatus("Cancelled", "The job was cancelled.", 1f, true, true)
        "failed" -> FriendlyStoreStatus("Failed", "The job did not complete. Open Details for the sanitized code.", 1f, true, true)
        else -> FriendlyStoreStatus(clean.replace('_', ' ').replaceFirstChar { it.uppercase() }, "Checking job state.", 0.4f, false, false)
    }
}

fun safeArtifactTitle(item: StoreArtifactRef, index: Int): String {
    val kind = when {
        item.mimeType.startsWith("video/") -> "Video"
        item.mimeType.startsWith("model/") || item.format in setOf("glb", "gltf") -> "3D model"
        else -> "Image"
    }
    return "$kind ${index + 1}"
}

fun artifactTypeLabel(item: StoreArtifactRef): String =
    when {
        item.mimeType.startsWith("video/") -> "Video"
        item.mimeType.startsWith("model/") || item.format in setOf("glb", "gltf") ->
            "3D ${item.format.ifBlank { "GLB" }.uppercase()}"
        item.mimeType.startsWith("image/") -> "Image"
        else -> item.mimeType.ifBlank { "Media" }
    }

fun safePreviewPath(item: StoreArtifactRef): String =
    listOf(item.thumbnailUrl, item.posterUrl, item.previewUrl, item.modelPreviewUrl)
        .firstOrNull { it.startsWith("/") }
        .orEmpty()

fun isActionableArtifact(item: StoreArtifactRef): Boolean =
    item.artifactId.isNotBlank() && item.mimeType.isNotBlank()

fun previewLoadAllowed(item: StoreArtifactRef): Boolean =
    isActionableArtifact(item) && safePreviewPath(item).isNotBlank()

fun isRenderableLatestResult(item: StoreArtifactRef): Boolean {
    if (!isActionableArtifact(item)) return false
    val status = item.status.lowercase()
    return status.isBlank() || status in setOf("ready", "completed", "complete", "stored")
}

fun latestRenderableArtifact(items: List<StoreArtifactRef>): StoreArtifactRef? =
    items.firstOrNull(::isRenderableLatestResult)

fun stableArtifactListKey(item: StoreArtifactRef, index: Int, prefix: String): String {
    val id = item.artifactId.ifBlank {
        listOf(item.thumbnailUrl, item.posterUrl, item.previewUrl, item.modelPreviewUrl, item.createdAt)
            .firstOrNull { it.isNotBlank() }
            ?: "blank"
    }
    return "$prefix-$index-$id"
}
