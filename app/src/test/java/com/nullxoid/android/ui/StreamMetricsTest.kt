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
}
