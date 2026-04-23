package com.nullxoid.android.backend.engine

import kotlinx.coroutines.flow.Flow

/**
 * Backend-side inference interface. The route handler for /chat/stream
 * pulls tokens from [generate] and emits them as SSE `delta` frames,
 * preserving the client-facing contract from NullXoid Desktop.
 *
 * Implementations are free to use CPU-only text models (llama.cpp,
 * MediaPipe LLM Inference, ONNX Runtime Mobile, …). The default
 * [EchoEngine] echoes user text word-by-word so the end-to-end
 * streaming path can be exercised without a model file on device.
 */
interface LlmEngine {
    val id: String
    val displayName: String

    data class Turn(val role: String, val content: String)

    /** Streams partial text deltas for an assistant reply. */
    fun generate(messages: List<Turn>): Flow<String>
}
