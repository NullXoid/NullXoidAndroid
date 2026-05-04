package com.nullxoid.android.data.model

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
        assertEquals("artifact-safe", response.artifacts.first().artifactId)
        assertFalse(payload.contains("CREATIVE_WORKER_TOKEN"))
        assertFalse(payload.contains("CREATIVE_PROVIDER_BASE_URL"))
        assertFalse(payload.contains("privatePath"))
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
}
