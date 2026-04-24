package com.nullxoid.android.backend.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatMessage
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatSession
import java.time.Instant
import java.util.UUID

class SQLiteStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
), EmbeddedStore {
    private val tenantId = "local-tenant"
    private val userId = "local-user"

    private val defaultAuth = AuthState(
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

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE chats (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                workspace_id TEXT,
                project_id TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                chat_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(chat_id) REFERENCES chats(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_chat_position ON messages(chat_id, position)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS chats")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun auth(): AuthState = if (signedIn) defaultAuth else AuthState(authenticated = false)

    override fun login(username: String, password: String): AuthState {
        signedIn = true
        return defaultAuth.copy(username = username, displayName = username)
    }

    override fun logout() {
        signedIn = false
    }

    override fun listChats(): List<ChatRecord> {
        val db = readableDatabase
        val chats = mutableListOf<ChatRecord>()
        db.rawQuery(
            """
            SELECT id, title, workspace_id, project_id, created_at, updated_at, archived
            FROM chats
            ORDER BY updated_at DESC
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                chats += cursor.chatRecord(db)
            }
        }
        return chats
    }

    override fun createChat(
        workspaceId: String?,
        projectId: String?,
        title: String,
        messages: List<ChatMessage>
    ): ChatRecord {
        val db = writableDatabase
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            db.insertOrThrow(
                "chats",
                null,
                ContentValues().apply {
                    put("id", id)
                    put("title", title.ifBlank { "New chat" })
                    put("workspace_id", workspaceId)
                    put("project_id", projectId)
                    put("created_at", now)
                    put("updated_at", now)
                    put("archived", 0)
                }
            )
            messages.forEachIndexed { index, message ->
                db.insertMessage(id, index, message)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return readChat(id) ?: error("created chat not found")
    }

    override fun appendAssistantMessage(chatId: String, assistantText: String): ChatRecord? {
        val db = writableDatabase
        val existing = readChat(chatId) ?: return null
        val nextPosition = existing.session?.messages.orEmpty().size
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            db.insertMessage(
                chatId = chatId,
                position = nextPosition,
                message = ChatMessage(role = "assistant", content = assistantText, createdAt = now)
            )
            db.update(
                "chats",
                ContentValues().apply { put("updated_at", now) },
                "id = ?",
                arrayOf(chatId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return readChat(chatId)
    }

    override fun archive(chatId: String, archived: Boolean): ChatRecord? {
        val db = writableDatabase
        val existing = readChat(chatId) ?: return null
        val now = Instant.now().toString()
        db.update(
            "chats",
            ContentValues().apply {
                put("archived", if (archived) 1 else 0)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(chatId)
        )
        return existing.copy(archived = archived, updatedAt = now)
    }

    private fun readChat(chatId: String): ChatRecord? {
        val db = readableDatabase
        db.rawQuery(
            """
            SELECT id, title, workspace_id, project_id, created_at, updated_at, archived
            FROM chats
            WHERE id = ?
            """.trimIndent(),
            arrayOf(chatId)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.chatRecord(db) else null
        }
    }

    private fun SQLiteDatabase.insertMessage(
        chatId: String,
        position: Int,
        message: ChatMessage
    ) {
        val createdAt = message.createdAt ?: Instant.now().toString()
        insertOrThrow(
            "messages",
            null,
            ContentValues().apply {
                put("id", message.id ?: UUID.randomUUID().toString())
                put("chat_id", chatId)
                put("position", position)
                put("role", message.role)
                put("content", message.content)
                put("created_at", createdAt)
            }
        )
    }

    private fun android.database.Cursor.chatRecord(db: SQLiteDatabase): ChatRecord {
        val id = getString(0)
        return ChatRecord(
            id = id,
            title = getString(1),
            workspaceId = getStringOrNull(2),
            projectId = getStringOrNull(3),
            createdAt = getString(4),
            updatedAt = getString(5),
            archived = getInt(6) != 0,
            session = ChatSession(messages = db.readMessages(id))
        )
    }

    private fun SQLiteDatabase.readMessages(chatId: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        rawQuery(
            """
            SELECT id, role, content, created_at
            FROM messages
            WHERE chat_id = ?
            ORDER BY position ASC
            """.trimIndent(),
            arrayOf(chatId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages += ChatMessage(
                    id = cursor.getString(0),
                    role = cursor.getString(1),
                    content = cursor.getString(2),
                    createdAt = cursor.getString(3)
                )
            }
        }
        return messages
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    companion object {
        private const val DATABASE_NAME = "nullxoid_embedded.db"
        private const val DATABASE_VERSION = 1
    }
}
