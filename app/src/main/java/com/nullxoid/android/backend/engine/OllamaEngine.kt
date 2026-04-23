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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * [LlmEngine] that relays to an Ollama instance.
 *
 * Sends `POST {baseUrl}/api/chat` with `stream: true` and reads Ollama's
 * NDJSON response one line at a time, emitting each `message.content`
 * delta. The final frame carries `"done": true` and ends the flow.
 *
 * Ollama is expected to be reachable on the device's network — see
 * docs for host setup (default 127.0.0.1:11434 is host-only; emulator
 * uses 10.0.2.2; physical phones need OLLAMA_HOST=0.0.0.0 on the host).
 */
class OllamaEngine(
    private val baseUrl: String,
    private val model: String
) : LlmEngine {

    override val id: String = "ollama:$model"
    override val displayName: String = "$model (Ollama)"

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
            .url("${baseUrl.trimEnd('/')}/api/chat")
            .post(payload)
            .build()

        val response = client.newCall(req).execute()
        try {
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(300)
                error("Ollama HTTP ${response.code}: $detail")
            }
            val source = response.body?.source() ?: error("Ollama returned an empty body")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                    ?: continue
                val content = obj["message"]?.jsonObject?.get("content")
                    ?.jsonPrimitive?.takeIf { it.isString }?.content
                if (!content.isNullOrEmpty()) emit(content)
                val done = obj["done"]?.jsonPrimitive?.booleanOrNull ?: false
                if (done) break
            }
        } finally {
            runCatching { response.close() }
        }
    }.flowOn(Dispatchers.IO)
}
