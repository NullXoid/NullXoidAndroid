package com.nullxoid.android.data.model

import com.nullxoid.android.data.api.NullXoidApiJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreAsyncJobContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun parsesAsyncStoreJobStatusSafely() {
        val payload = """
            {
              "ok": true,
              "storeJobId": "storejob-android-001",
              "jobId": "storejob-android-001",
              "requestId": "store-request-001",
              "addonId": "local-image-studio",
              "mediaKind": "image",
              "status": "queued_connector",
              "approvalRequired": true,
              "approvalSource": "active_timed_grant",
              "queueLane": "suite.media.image.generate",
              "queuePosition": 1,
              "canCancel": true,
              "cancelRequested": false,
              "pollAfterMs": 1500,
              "artifactId": "artifact-safe",
              "thumbnailUrl": "/artifacts/artifact-safe/thumb",
              "mimeType": "image/png",
              "createdAt": "2026-05-02T00:00:00Z",
              "updatedAt": "2026-05-02T00:00:01Z",
              "artifacts": [
                {
                  "artifactId": "artifact-safe",
                  "thumbnailUrl": "/artifacts/artifact-safe/thumb",
                  "mimeType": "image/png",
                  "status": "ready"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(StoreActionResponse.serializer(), payload)

        assertEquals("storejob-android-001", response.storeJobId)
        assertEquals("queued_connector", response.status)
        assertEquals(1500, response.pollAfterMs)
        assertEquals(1, response.queuePosition)
        assertTrue(response.canCancel)
        assertEquals("artifact-safe", response.artifacts.first().artifactId)
        assertFalse(payload.contains("CREATIVE_WORKER_TOKEN"))
        assertFalse(payload.contains("CREATIVE_PROVIDER_BASE_URL"))
        assertFalse(payload.contains("privatePath"))
    }

    @Test
    fun parsesSafeJobListAndEvents() {
        val payload = """
            {
              "ok": true,
              "activeOnly": false,
              "pollAfterMs": 1500,
              "jobs": [
                {
                  "storeJobId": "storejob-android-002",
                  "jobId": "storejob-android-002",
                  "requestId": "request-safe",
                  "addonId": "local-video-studio",
                  "mediaKind": "video",
                  "capability": "suite.media.video.generate",
                  "action": "media.video.generate.local",
                  "status": "running_provider",
                  "approvalSource": "active_timed_grant",
                  "queueLane": "suite.media.video.generate",
                  "queuePosition": 0,
                  "canCancel": true,
                  "cancelRequested": true,
                  "createdAt": "2026-05-02T00:00:00Z",
                  "updatedAt": "2026-05-02T00:00:05Z",
                  "startedAt": "2026-05-02T00:00:03Z",
                  "events": [
                    {"type": "created", "at": "2026-05-02T00:00:00Z", "status": "pending_approval"},
                    {"type": "cancel_requested", "at": "2026-05-02T00:00:06Z", "status": "running_provider"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(StoreJobsResponse.serializer(), payload)
        val job = response.jobs.single()

        assertFalse(response.activeOnly)
        assertEquals("storejob-android-002", job.storeJobId)
        assertEquals("video", job.mediaKind)
        assertEquals("suite.media.video.generate", job.queueLane)
        assertTrue(job.canCancel)
        assertTrue(job.cancelRequested)
        assertEquals("cancel_requested", job.events.last().type)
        listOf("CREATIVE_WORKER_TOKEN", "providerUrl", "workflowPath", "privatePath", "rawPrompt").forEach {
            assertFalse("Unexpected leak marker: $it", payload.contains(it))
        }
    }

    @Test
    fun androidStoreActionRequestsAsyncByDefault() {
        val request = StoreActionRequest(
            prompt = "android out of network image",
            capability = "suite.media.image.generate",
            jobType = "image-standard"
        )

        assertFalse(request.waitForApproval)
        assertEquals("image-standard", request.jobType)
        assertTrue(request.capability.startsWith("suite.media."))
    }

    @Test
    fun videoStoreActionCanSendSafeAudioArtifactMetadata() {
        val request = StoreActionRequest(
            prompt = "android video with voice",
            capability = "suite.media.video.generate",
            jobType = "video-short",
            audioMode = "recorded_voice",
            audioArtifactId = "artifact-safe-voice",
            audioPrompt = "Match the scene"
        )

        assertEquals("recorded_voice", request.audioMode)
        assertEquals("artifact-safe-voice", request.audioArtifactId)
        assertFalse(request.audioArtifactId.contains("://"))
        assertFalse(request.audioArtifactId.contains("\\"))
        assertFalse(request.audioArtifactId.contains("/"))
    }

    @Test
    fun model3dRequestCanSendGuidedSourceImageArtifactsWithoutPaths() {
        val request = StoreActionRequest(
            prompt = "make a usable product model",
            capability = "suite.media.model3d.generate",
            jobType = "model-standard",
            sourceImageArtifactId = "artifact-front",
            sourceImageViews = listOf(
                StoreSourceImageView(role = "front", artifactId = "artifact-front"),
                StoreSourceImageView(role = "left", artifactId = "artifact-left"),
                StoreSourceImageView(role = "back", artifactId = "artifact-back")
            ),
            model3dInputMode = "guided_multiview",
            mirrorSideView = true
        )

        val encoded = NullXoidApiJson.encodeToString(StoreActionRequest.serializer(), request)

        assertEquals("guided_multiview", request.model3dInputMode)
        assertTrue(request.mirrorSideView)
        assertEquals(3, request.sourceImageViews.size)
        assertTrue(encoded.contains("sourceImageViews"))
        assertTrue(encoded.contains("mirrorSideView"))
        assertTrue(encoded.contains("artifact-left"))
        assertFalse(encoded.contains("C:\\"))
        assertFalse(encoded.contains("/sdcard"))
        assertFalse(encoded.contains("content://"))
    }
}
