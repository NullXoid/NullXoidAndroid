package com.nullxoid.android.backend.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic placeholder engine. Streams back a structured reply
 * derived from the last user turn, one token per ~40ms so the SSE
 * pipeline visibly streams in the UI.
 *
 * Swap this for a real inference engine by binding a different
 * [LlmEngine] in [com.nullxoid.android.backend.EmbeddedServer].
 */
class EchoEngine : LlmEngine {
    override val id: String = "echo:nullxoid-local"
    override val displayName: String = "Echo (on-device)"

    override fun generate(messages: List<LlmEngine.Turn>): Flow<String> = flow {
        val userText = messages.lastOrNull { it.role.equals("user", true) }?.content
            ?: "(no user message)"

        val opening = "You said: "
        for (ch in opening) {
            emit(ch.toString())
            delay(10)
        }
        val words = userText.trim().split(Regex("\\s+"))
        for ((i, w) in words.withIndex()) {
            emit(if (i == 0) w else " $w")
            delay(40)
        }
        emit("\n\n(Local echo backend — replace EchoEngine with an LLM to get real replies.)")
    }
}
