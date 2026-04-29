package com.nullxoid.android.ui

import com.nullxoid.android.ui.chat.formatMessageTiming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTimingTest {
    @Test
    fun assistantTimingIncludesLatencyFromPreviousUserMessage() {
        val timing = formatMessageTiming(
            createdAt = "2026-04-29T13:00:02Z",
            previousUserCreatedAt = "2026-04-29T13:00:00Z"
        ).orEmpty()

        assertTrue(timing.contains("+2.0s"))
    }

    @Test
    fun messageTimingFallsBackToTimestampWithoutPreviousUserMessage() {
        val timing = formatMessageTiming(
            createdAt = "2026-04-29T13:00:02Z",
            previousUserCreatedAt = null
        ).orEmpty()

        assertFalse(timing.contains("+"))
    }
}
