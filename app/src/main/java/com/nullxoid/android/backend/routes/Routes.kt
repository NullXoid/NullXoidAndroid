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
import com.nullxoid.android.data.model.StoreActionRequest
import com.nullxoid.android.backend.engine.LlmEngine
import com.nullxoid.android.backend.nullbridge.NullBridgeAdapter
import com.nullxoid.android.backend.store.EmbeddedStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.writeStringUtf8
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    nullBridgeRoutes(store)
    modelRoutes(engine)
    settingsRoutes(engine)
    storeRoutes()
    chatRoutes(store)
    chatStreamRoute(store, engine)
}

private fun Route.authRoutes(store: EmbeddedStore) {
    get("/auth/me") { call.respond(store.auth()) }

    post("/auth/login") {
        val req = call.receive<LoginRequest>()
        call.respond(store.login(req.username, req.password))
    }

    get("/auth/passkey/options") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("passkey"))
    }

    post("/auth/passkey/complete") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("passkey"))
    }

    get("/auth/passkey/credentials") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("passkey"))
    }

    get("/auth/passkey/register/options") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("passkey"))
    }

    post("/auth/passkey/register/complete") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("passkey"))
    }

    delete("/auth/passkey/credentials/{credential_id}") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("passkey"))
    }

    post("/auth/oidc/start") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("oidc_pkce"))
    }

    post("/auth/oidc/complete") {
        call.respond(HttpStatusCode.NotImplemented, nativeAuthNotConfigured("oidc_pkce"))
    }

    post("/auth/logout") {
        store.logout()
        call.respond(HttpStatusCode.OK, buildJsonObject { put("ok", true) })
    }
}

private fun nativeAuthNotConfigured(method: String): JsonObject =
    buildJsonObject {
        put(
            "detail",
            buildJsonObject {
                put("code", "${method}_provider_not_configured")
                put("auth_method", method)
                put("configured", false)
                put("setup_required", true)
                put("setup_route", "settings:accounts_security")
            }
        )
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

private fun Route.nullBridgeRoutes(store: EmbeddedStore, adapter: NullBridgeAdapter = NullBridgeAdapter()) {
    get("/api/nullbridge/status") {
        call.respond(adapter.status())
    }

    post("/api/nullbridge/route-check") {
        val body = call.receive<JsonObject>()
        val capability = body["capability"]?.toString()?.trim('"').orEmpty()
        val targetRole = body["targetRole"]?.toString()?.trim('"').orEmpty()
        val targetId = body["targetId"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        val payload = body["payload"] as? JsonObject ?: buildJsonObject {}
        if (capability.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("detail", "capability is required") })
            return@post
        }
        if (targetRole.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("detail", "targetRole is required") })
            return@post
        }
        val auth = store.auth()
        val actingUser = buildJsonObject {
            put("userId", auth.userId ?: "android_local_user")
            put("roles", JsonArray(auth.roles.map { JsonPrimitive(it) }))
            put("platform", "android")
        }
        call.respond(adapter.routeCheck(capability, targetRole, targetId, payload, actingUser))
    }

    post("/api/android/nullbridge/demo-route") {
        val body = call.receive<JsonObject>()
        val echo = body["echo"]?.toString()?.trim('"') ?: "m35 demo echo"
        val auth = store.auth()
        val actingUser = buildJsonObject {
            put("userId", auth.userId ?: "android_local_user")
            put("roles", JsonArray(auth.roles.map { JsonPrimitive(it) }))
            put("platform", "android")
        }
        call.respond(adapter.demoRoute(echo, actingUser))
    }

    post("/api/android/nullbridge/unsupported-route") {
        val body = call.receive<JsonObject>()
        val capability = body["capability"]?.toString()?.trim('"') ?: NullBridgeAdapter.UNSUPPORTED_CAPABILITY
        call.respond(adapter.denyUnsupported(capability))
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

private const val localImageStudioId = "local-image-studio"
private const val localImageStudioAction = "media.image.generate.local"
private const val localImageStudioCapability = "suite.media.image.generate"
private const val localVideoStudioId = "local-video-studio"
private const val localVideoStudioAction = "media.video.generate.local"
private const val localVideoStudioCapability = "suite.media.video.generate"
private const val local3dStudioId = "local-3d-studio"
private const val local3dStudioAction = "media.model3d.generate.local"
private const val local3dStudioCapability = "suite.media.model3d.generate"

private data class StoreAddonFixture(
    val id: String,
    val name: String,
    val subcategory: String,
    val description: String,
    val capability: String,
    val action: String,
    val approvalTitle: String,
    val approvalSummary: String,
    val providerKinds: List<String>,
    val artifactScope: String,
    val defaultOutputFormat: String? = null
)

private val storeAddonFixtures = listOf(
    StoreAddonFixture(
        id = localImageStudioId,
        name = "Local Image Studio",
        subcategory = "image-generation",
        description = "Generate private local images through an approval-gated backend workflow.",
        capability = localImageStudioCapability,
        action = localImageStudioAction,
        approvalTitle = "Approve image generation?",
        approvalSummary = "Local Image Studio wants to generate an image.",
        providerKinds = listOf("mock", "local-image-engine"),
        artifactScope = "private-generated-image"
    ),
    StoreAddonFixture(
        id = localVideoStudioId,
        name = "Local Video Studio",
        subcategory = "video-generation",
        description = "Generate private local videos through an approval-gated backend workflow.",
        capability = localVideoStudioCapability,
        action = localVideoStudioAction,
        approvalTitle = "Approve video generation?",
        approvalSummary = "Local Video Studio wants to generate a video.",
        providerKinds = listOf("mock-video", "local-video-engine"),
        artifactScope = "private-generated-video"
    ),
    StoreAddonFixture(
        id = local3dStudioId,
        name = "Local 3D Studio",
        subcategory = "3d-generation",
        description = "Generate private GLB/glTF model artifacts through an approval-gated backend workflow.",
        capability = local3dStudioCapability,
        action = local3dStudioAction,
        approvalTitle = "Approve 3D model generation?",
        approvalSummary = "Local 3D Studio wants to generate a 3D model.",
        providerKinds = listOf("mock-3d", "local-3d-engine"),
        artifactScope = "private-generated-3d-model",
        defaultOutputFormat = "glb"
    )
)

private fun storeCatalogJson(): JsonObject = buildJsonObject {
    put("ok", true)
    put(
        "categories",
        JsonArray(
            listOf(
                "productivity" to "Productivity",
                "creative-workflows" to "Creative Workflows",
                "automation" to "Automation",
                "developer-tools" to "Developer Tools",
                "operations" to "Operations",
                "privacy-security" to "Privacy & Security",
                "experiments" to "Experiments"
            ).map { (id, label) ->
                buildJsonObject {
                    put("id", id)
                    put("label", label)
                }
            }
        )
    )
    put("addons", JsonArray(storeAddonFixtures.map(::storeAddonJson)))
}

private fun storeAddonJson(addon: StoreAddonFixture): JsonObject = buildJsonObject {
    put("id", addon.id)
    put("name", addon.name)
    put("category", "creative-workflows")
    put("categoryLabel", "Creative Workflows")
    put("subcategory", addon.subcategory)
    put("description", addon.description)
    put("status", "alpha")
    put("enabled", true)
    put("visibility", "local-debug")
    put("platforms", JsonArray(listOf("web", "android", "windows").map(::JsonPrimitive)))
    put("capabilities", JsonArray(listOf(addon.capability).map(::JsonPrimitive)))
    put("requiresApproval", true)
    put(
        "approvalRoute",
        buildJsonObject {
            put("title", addon.approvalTitle)
            put("summary", addon.approvalSummary)
            put("risk", "medium")
            put("action", addon.action)
        }
    )
    put("providerKinds", JsonArray(addon.providerKinds.map(::JsonPrimitive)))
    addon.defaultOutputFormat?.let {
        put("defaultOutputFormat", it)
        put("outputFormats", JsonArray(listOf("glb", "gltf").map(::JsonPrimitive)))
    }
    put(
        "permissions",
        JsonArray(
            listOf(
                buildJsonObject {
                    put("kind", "approval")
                    put("scope", "nullbridge")
                },
                buildJsonObject {
                    put("kind", "artifact")
                    put("scope", addon.artifactScope)
                }
            )
        )
    )
    put(
        "routes",
        buildJsonObject {
            put("detail", "/api/store/addons/${addon.id}")
            put("action", "/api/store/addons/${addon.id}/actions/${addon.action}")
            put("gallery", "/api/store/addons/${addon.id}/gallery")
        }
    )
}

private fun storeAddonById(addonId: String): StoreAddonFixture? =
    storeAddonFixtures.firstOrNull { it.id == addonId }

private fun Route.storeRoutes() {
    get("/api/store/catalog") { call.respond(storeCatalogJson()) }

    get("/api/store/addons/{addonId}") {
        val addon = storeAddonById(call.parameters["addonId"].orEmpty())
        if (addon == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject { put("detail", "addon not found") })
        } else {
            call.respond(buildJsonObject { put("ok", true); put("addon", storeAddonJson(addon)) })
        }
    }

    post("/api/store/addons/{addonId}/enable") {
        val addon = storeAddonById(call.parameters["addonId"].orEmpty())
        if (addon == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject { put("detail", "addon not found") })
        } else {
            call.respond(buildJsonObject { put("ok", true); put("addon", storeAddonJson(addon)) })
        }
    }

    post("/api/store/addons/{addonId}/disable") {
        val addon = storeAddonById(call.parameters["addonId"].orEmpty())
        if (addon == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject { put("detail", "addon not found") })
        } else {
            call.respond(buildJsonObject { put("ok", true); put("addon", storeAddonJson(addon)) })
        }
    }

    post("/api/store/addons/{addonId}/actions/{action}") {
        val addonId = call.parameters["addonId"].orEmpty()
        val action = call.parameters["action"].orEmpty()
        val addon = storeAddonById(addonId)
        val body = call.receive<StoreActionRequest>()
        if (addon == null || action != addon.action || body.prompt.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject {
                    put("ok", false)
                    put("status", "failed")
                    put("approvalRequired", false)
                    put("errorCode", "CAPABILITY_NOT_SUPPORTED")
                }
            )
        } else {
            call.respond(
                buildJsonObject {
                    put("ok", true)
                    put("requestId", "android-store-local-demo")
                    put("status", "pending_approval")
                    put("approvalRequired", true)
                }
            )
        }
    }

    get("/api/store/addons/{addonId}/gallery") {
        val addonId = call.parameters["addonId"].orEmpty()
        if (storeAddonById(addonId) == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject { put("detail", "addon not found") })
        } else {
            call.respond(
                buildJsonObject {
                    put("ok", true)
                    put("addonId", addonId)
                    put("items", JsonArray(emptyList()))
                }
            )
        }
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
