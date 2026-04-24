package com.nullxoid.android.backend.routes

import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatCreateRequest
import com.nullxoid.android.data.model.ChatListResponse
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatStreamRequest
import com.nullxoid.android.data.model.HealthFeatures
import com.nullxoid.android.data.model.LoginRequest
import com.nullxoid.android.data.model.ModelDescriptor
import com.nullxoid.android.data.model.ModelListResponse
import com.nullxoid.android.data.model.RemoteSettings
import com.nullxoid.android.backend.engine.LlmEngine
import com.nullxoid.android.backend.store.EmbeddedStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.writeStringUtf8
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mirrors the path set consumed by [com.nullxoid.android.data.api.NullXoidApi]
 * and [com.nullxoid.android.data.api.ChatStream]. Keep this in sync with
 * AiAssistant/src/bridge/nullxoid_backend_bridge.cpp so the same client can
 * talk to either the desktop's remote backend or this embedded one.
 */
fun Route.nullxoidRoutes(store: EmbeddedStore, engine: LlmEngine) {
    authRoutes(store)
    healthRoutes(engine)
    aibenchieRoutes()
    modelRoutes(engine)
    settingsRoutes(engine)
    chatRoutes(store)
    chatStreamRoute(store, engine)
}

private fun Route.authRoutes(store: EmbeddedStore) {
    get("/auth/me") { call.respond(store.auth()) }

    post("/auth/login") {
        val req = call.receive<LoginRequest>()
        call.respond(store.login(req.username, req.password))
    }

    post("/auth/logout") {
        store.logout()
        call.respond(HttpStatusCode.OK, buildJsonObject { put("ok", true) })
    }
}

private fun Route.healthRoutes(engine: LlmEngine) {
    get("/health/features") {
        call.respond(
            HealthFeatures(
                backend = "nullxoid-android-embedded",
                version = "0.1.0",
                ok = true,
                features = buildJsonObject {
                    put("streaming", true)
                    put("lv7", false)
                    put("workspaces", false)
                },
                runtime = buildJsonObject {
                    put("engine", engine.id)
                    put("engine_name", engine.displayName)
                    put("device_local", true)
                }
            )
        )
    }
}

private fun Route.aibenchieRoutes() {
    get("/api/aibenchie/greeting") {
        call.respond(
            buildJsonObject {
                put("ok", true)
                put("backend", "nullxoid-android-embedded")
                put("contract", "aibenchie.frontend-backend.v1")
                put("message", "Hello from NullXoid Android embedded backend.")
            }
        )
    }
}

private fun Route.modelRoutes(engine: LlmEngine) {
    get("/models") {
        call.respond(
            ModelListResponse(
                models = listOf(
                    ModelDescriptor(
                        id = engine.id,
                        name = engine.displayName,
                        provider = if (engine.id.startsWith("ollama:")) "ollama" else "on-device",
                        family = if (engine.id.startsWith("ollama:")) "ollama" else "echo",
                        contextWindow = 4096,
                        capabilities = listOf("chat", "streaming")
                    )
                )
            )
        )
    }
}

private fun Route.settingsRoutes(engine: LlmEngine) {
    val defaults = RemoteSettings(
        defaultModel = engine.id,
        settings = buildJsonObject { put("theme", "system") },
        defaults = buildJsonObject { put("theme", "system") }
    )
    get("/api/settings") { call.respond(defaults) }
    put("/api/settings") {
        // Accept and echo the update; no persistence for v1.
        val patch = call.receive<JsonObject>()
        call.respond(defaults.copy(settings = patch))
    }
}

private fun Route.chatRoutes(store: EmbeddedStore) {
    get("/api/chats") { call.respond(ChatListResponse(chats = store.listChats())) }

    post("/api/chats") {
        val body = call.receive<ChatCreateRequest>()
        val created = store.createChat(
            workspaceId = body.workspaceId,
            projectId = body.projectId,
            title = body.title,
            messages = body.messages
        )
        call.respond(created)
    }

    post("/api/chats/{id}/archive") {
        val id = call.parameters["id"].orEmpty()
        val archived = call.request.queryParameters["archived"]?.toBooleanStrictOrNull() ?: true
        val updated = store.archive(id, archived)
        if (updated == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject { put("detail", "chat not found") })
        } else {
            call.respond(updated)
        }
    }
}

/**
 * SSE-style stream. Frames match the client parser:
 *   event: delta
 *   data: {"text":"..."}
 *
 * terminated by a single `event: completed` frame.
 */
private fun Route.chatStreamRoute(store: EmbeddedStore, engine: LlmEngine) {
    val json = Json { explicitNulls = false; encodeDefaults = true }

    post("/chat/stream") {
        val body = call.receive<ChatStreamRequest>()
        val turns = body.messages.map { LlmEngine.Turn(it.role, it.content) }
        val sseType = ContentType.parse("text/event-stream")

        call.respondBytesWriter(contentType = sseType) {
            val accum = StringBuilder()
            try {
                engine.generate(turns).collect { piece ->
                    accum.append(piece)
                    val payload = buildJsonObject { put("text", piece) }
                    writeStringUtf8("event: delta\n")
                    writeStringUtf8("data: ${json.encodeToString(JsonObject.serializer(), payload)}\n\n")
                    flush()
                }
                writeStringUtf8("event: completed\n")
                writeStringUtf8("data: {}\n\n")
                flush()
            } catch (t: Throwable) {
                val err = buildJsonObject { put("message", t.message ?: "engine failure") }
                writeStringUtf8("event: error\n")
                writeStringUtf8("data: ${json.encodeToString(JsonObject.serializer(), err)}\n\n")
                flush()
            } finally {
                body.chatId?.let { store.appendAssistantMessage(it, accum.toString()) }
            }
        }
    }
}
