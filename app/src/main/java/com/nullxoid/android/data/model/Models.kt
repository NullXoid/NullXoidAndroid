package com.nullxoid.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTOs mirrored from the NullXoid desktop bridge
 * (see AiAssistant/src/core/types.h and src/bridge/nullxoid_backend_bridge.cpp).
 *
 * Fields default to nullable / empty to tolerate backend drift — the desktop
 * bridge is equally lenient on missing optional keys.
 */

@Serializable
data class AuthState(
    val authenticated: Boolean = false,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("tenant_id") val tenantId: String? = null,
    val username: String? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("access_level") val accessLevel: String? = null,
    val roles: List<String> = emptyList()
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class ModelDescriptor(
    val id: String,
    val name: String? = null,
    val provider: String? = null,
    val family: String? = null,
    @SerialName("context_window") val contextWindow: Int? = null,
    val capabilities: List<String> = emptyList()
)

@Serializable
data class ModelListResponse(
    val models: List<ModelDescriptor> = emptyList()
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val id: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatSession(
    val messages: List<ChatMessage> = emptyList(),
    val metadata: JsonObject? = null
)

@Serializable
data class ChatRecord(
    val id: String,
    val title: String = "",
    @SerialName("workspace_id") val workspaceId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val archived: Boolean = false,
    val session: ChatSession? = null
)

@Serializable
data class ChatListResponse(
    val chats: List<ChatRecord> = emptyList()
)

@Serializable
data class ChatCreateRequest(
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("workspace_id") val workspaceId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    val title: String,
    val messages: List<ChatMessage>
)

@Serializable
data class HealthFeatures(
    val backend: String? = null,
    val version: String? = null,
    val features: JsonObject? = null,
    val runtime: JsonObject? = null,
    val ok: Boolean = true
)

@Serializable
data class RemoteSettings(
    val settings: JsonObject? = null,
    val defaults: JsonObject? = null,
    val meta: JsonObject? = null,
    @SerialName("default_model") val defaultModel: String? = null
)

/** Body for POST /chat/stream — matches the desktop ChatRequest shape. */
@Serializable
data class ChatStreamRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("chat_id") val chatId: String? = null,
    @SerialName("workspace_id") val workspaceId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val stream: Boolean = true,
    val parameters: JsonObject? = null
)

/**
 * One SSE frame from /chat/stream. The backend multiplexes several event
 * types on the same stream; the desktop bridge dispatches on `event`.
 */
sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    data class Job(val id: String, val status: String, val raw: JsonElement) : StreamEvent()
    data class Meta(val raw: JsonElement) : StreamEvent()
    data object Completed : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
