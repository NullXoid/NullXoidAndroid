package com.nullxoid.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUiTextTest {
    @Test
    fun rateLimitErrorsUseConsistentFriendlyCopy() {
        assertEquals(
            "NullXoid is receiving too many assistant requests right now. Wait a moment, then try again.",
            friendlyChatError("HTTP 429: rate limit exceeded")
        )
    }
}
