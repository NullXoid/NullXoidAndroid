package com.nullxoid.android.backend.store

import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord

interface EmbeddedStore {
    fun auth(): AuthState
    fun login(username: String, password: String): AuthState
    fun logout()
    fun listChats(): List<ChatRecord>
    fun createChat(
        workspaceId: String?,
        projectId: String?,
        title: String,
        messages: List<ChatMessage>
    ): ChatRecord
    fun appendAssistantMessage(chatId: String, assistantText: String): ChatRecord?
    fun archive(chatId: String, archived: Boolean): ChatRecord?
}
