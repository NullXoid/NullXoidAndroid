package com.nullxoid.android.backend.store

import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime store used by the embedded backend. Good enough to
 * prove the protocol; swap for DataStore/Room if persistence matters.
 */
class InMemoryStore : EmbeddedStore {
    private val tenantId = "local-tenant"
    private val userId = "local-user"

    val defaultAuth = AuthState(
        authenticated = true,
        userId = userId,
        tenantId = tenantId,
        username = "local",
        displayName = "Local User",
        accessLevel = "standard",
        roles = listOf("user")
    )

    @Volatile
    private var signedIn: Boolean = true

    private val chats = ConcurrentHashMap<String, ChatRecord>()

    override fun auth(): AuthState = if (signedIn) defaultAuth else AuthState(authenticated = false)

    override fun login(username: String, password: String): AuthState {
        signedIn = true
        return defaultAuth.copy(username = username, displayName = username)
    }

    override fun logout() { signedIn = false }

    override fun listChats(): List<ChatRecord> = chats.values
        .sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }

    override fun createChat(
        workspaceId: String?,
        projectId: String?,
        title: String,
        messages: List<ChatMessage>
    ): ChatRecord {
        val id = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()
        val record = ChatRecord(
            id = id,
            title = title.ifBlank { "New chat" },
            workspaceId = workspaceId,
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
            archived = false,
            session = ChatSession(messages = messages)
        )
        chats[id] = record
        return record
    }

    override fun appendAssistantMessage(chatId: String, assistantText: String): ChatRecord? {
        val existing = chats[chatId] ?: return null
        val now = java.time.Instant.now().toString()
        val updated = existing.copy(
            updatedAt = now,
            session = ChatSession(
                messages = (existing.session?.messages.orEmpty()) +
                    ChatMessage(role = "assistant", content = assistantText)
            )
        )
        chats[chatId] = updated
        return updated
    }

    override fun archive(chatId: String, archived: Boolean): ChatRecord? {
        val existing = chats[chatId] ?: return null
        val updated = existing.copy(archived = archived)
        chats[chatId] = updated
        return updated
    }
}
