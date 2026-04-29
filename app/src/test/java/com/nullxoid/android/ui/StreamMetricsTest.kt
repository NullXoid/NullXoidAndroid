package com.nullxoid.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamMetricsTest {
    @Test
    fun estimatesTokensFromTextChunks() {
        assertEquals(0, estimateStreamTokens(""))
        assertEquals(1, estimateStreamTokens("hi"))
        assertEquals(3, estimateStreamTokens("hello world"))
    }

    @Test
    fun formatsStreamMetricForDisplay() {
        assertEquals(
            "Done | tokens ~12 | 3.5 tok/s",
            formatStreamMetric(status = "Done", tokens = 12, tokensPerSecond = 3.456)
        )
    }

    @Test
    fun normalizesBackendStreamStatus() {
        assertEquals("Thinking", streamStatusLabel("started"))
        assertEquals("Queued", streamStatusLabel("pending"))
        assertEquals("Streaming", streamStatusLabel("running"))
        assertEquals("Done", streamStatusLabel("completed"))
    }
}
