package com.nullxoid.android.backend.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * [LlmEngine] that relays to llama.cpp's OpenAI-compatible server.
 *
 * Expected server example from Termux:
 * ./build/bin/llama-server -hf bartowski/google_gemma-4-E2B-it-GGUF:Q4_K_M --host 0.0.0.0 --port 8080
 *
 * Sends POST {baseUrl}/v1/chat/completions with stream=true and reads SSE
 * lines in the format `data: {...}` until `[DONE]`.
 */
class LlamaCppEngine(
    private val baseUrl: String,
    private val model: String
) : LlmEngine {

    override val id: String = "llamacpp:$model"
    override val displayName: String = "$model (llama.cpp)"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun generate(messages: List<LlmEngine.Turn>): Flow<String> = flow {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                messages.forEach { turn ->
                    add(buildJsonObject {
                        put("role", turn.role)
                        put("content", turn.content)
                    })
                }
            })
        }.toString().toRequestBody(jsonMedia)

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
            .post(payload)
            .build()

        val response = client.newCall(req).execute()
        try {
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(300)
                error("llama.cpp HTTP ${response.code}: $detail")
            }
            val source = response.body?.source() ?: error("llama.cpp returned an empty body")

            while (!source.exhausted()) {
                val rawLine = source.readUtf8Line() ?: break
                val line = rawLine.trim()
                if (line.isBlank() || !line.startsWith("data:")) continue

                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: continue
                val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: continue
                val delta = choice["delta"]?.jsonObject
                val content = delta?.get("content")?.jsonPrimitive?.content
                    ?: choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content

                if (!content.isNullOrEmpty()) emit(content)

                val finishReason = choice["finish_reason"]?.jsonPrimitive?.content
                val done = obj["done"]?.jsonPrimitive?.booleanOrNull ?: false
                if (done || finishReason != null) break
            }
        } finally {
            runCatching { response.close() }
        }
    }.flowOn(Dispatchers.IO)
}
