package com.nullxoid.android.data.api

import com.nullxoid.android.data.model.ChatStreamRequest
import com.nullxoid.android.data.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Consumes POST /chat/stream as Server-Sent Events, emitting a [StreamEvent]
 * per SSE frame. Mirrors the frame parser in
 * AiAssistant/src/bridge/nullxoid_backend_bridge.cpp (see the `event:` /
 * `data:` loop). Frames are delimited by a blank line; each frame is
 * decoded into a specific event variant based on its `event:` field.
 *
 * Event contract (from the desktop consumer):
 *   - `delta/token` — incremental assistant text at `data.text` or `data.delta`
 *   - `job`         — job lifecycle; raw JSON is surfaced as-is
 *   - `nullxoid`    — controller metadata (policy_rationale etc)
 *   - `completed`   — end of stream
 *   - `error`       — terminal error; message at `data.message` or `data.detail`
 *   - (unnamed)     — implicit `delta` for backends that omit `event:`
 */
class ChatStream(
    private val baseUrlProvider: () -> String
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun stream(request: ChatStreamRequest): Flow<StreamEvent> = callbackFlow {
        val body = json.encodeToString(ChatStreamRequest.serializer(), request)
            .toRequestBody(jsonMedia)
        val httpReq = Request.Builder()
            .url(BackendEndpoint.resolve(baseUrlProvider(), "/chat/stream"))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body)
            .build()

        val call = HttpClient.okHttp.newCall(httpReq)
        val response = runCatching { call.execute() }.getOrElse {
            trySend(StreamEvent.Error(it.message ?: "network failure"))
            close()
            return@callbackFlow
        }

        if (!response.isSuccessful) {
            val detail = response.body?.string().orEmpty().take(500)
            trySend(StreamEvent.Error("HTTP ${response.code}: $detail"))
            response.close()
            close()
            return@callbackFlow
        }

        val source = response.body?.source()
        if (source == null) {
            trySend(StreamEvent.Error("empty response body"))
            response.close()
            close()
            return@callbackFlow
        }

        val frameBuilder = StringBuilder()
        try {
            var terminalEventSeen = false
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    val frame = frameBuilder.toString()
                    frameBuilder.clear()
                    if (frame.isBlank()) continue
                    val event = parseFrame(frame)
                    if (event != null) trySend(event)
                    if (event is StreamEvent.Completed || event is StreamEvent.Error) {
                        terminalEventSeen = true
                        break
                    }
                } else {
                    frameBuilder.append(line).append('\n')
                }
            }
            if (!terminalEventSeen) trySend(StreamEvent.Completed)
        } catch (t: Throwable) {
            trySend(StreamEvent.Error(t.message ?: "stream interrupted"))
        } finally {
            response.close()
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    internal fun parseFrame(frame: String): StreamEvent? {
        var eventName = "delta"
        val dataBuf = StringBuilder()
        frame.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isEmpty() || line.startsWith(":") -> Unit
                line.startsWith("event:") ->
                    eventName = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                    dataBuf.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        val payload = dataBuf.toString()
        if (payload.isEmpty()) return null
        val obj: JsonObject? = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()

        return when (eventName.lowercase()) {
            "delta", "message", "chunk", "token" -> {
                val text = obj?.get("text")?.jsonPrimitive?.contentOrNullSafe()
                    ?: obj?.get("delta")?.jsonPrimitive?.contentOrNullSafe()
                    ?: obj?.get("content")?.jsonPrimitive?.contentOrNullSafe()
                    ?: payload
                StreamEvent.Delta(text)
            }
            "thinking", "reasoning" -> {
                val tokens = obj?.get("tokens")?.jsonPrimitive?.contentOrNullSafe()?.toIntOrNull()
                    ?: 0
                StreamEvent.Thinking(tokens)
            }
            "job" -> {
                val id = obj?.get("id")?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                val status = obj?.get("status")?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                StreamEvent.Job(id, status, obj ?: return null)
            }
            "nullxoid", "meta", "signal" -> StreamEvent.Meta(obj ?: return null)
            "completed", "done", "end" -> StreamEvent.Completed
            "error" -> {
                val msg = obj?.get("message")?.jsonPrimitive?.contentOrNullSafe()
                    ?: obj?.get("detail")?.jsonPrimitive?.contentOrNullSafe()
                    ?: payload
                StreamEvent.Error(msg)
            }
            else -> null
        }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()
