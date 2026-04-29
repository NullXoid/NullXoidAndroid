package com.nullxoid.android.data.api

import com.nullxoid.android.data.model.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamParserTest {
    private val stream = ChatStream { "https://api.echolabs.diy/nullxoid" }

    @Test
    fun parsesHostedTokenFramesAsAssistantDeltas() {
        val event = stream.parseFrame(
            """
            event: token
            data: {"type":"token","delta":"Hello from hosted NullXoid"}
            """.trimIndent()
        )

        assertTrue(event is StreamEvent.Delta)
        assertEquals("Hello from hosted NullXoid", (event as StreamEvent.Delta).text)
    }

    @Test
    fun parsesThinkingFramesAsStatusOnlyEvents() {
        val event = stream.parseFrame(
            """
            event: thinking
            data: {"type":"thinking","tokens":24}
            """.trimIndent()
        )

        assertTrue(event is StreamEvent.Thinking)
        assertEquals(24, (event as StreamEvent.Thinking).tokens)
    }
}
