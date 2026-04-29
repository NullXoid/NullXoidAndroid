package com.nullxoid.android.data.model

import com.nullxoid.android.data.e2ee.EncryptedBytes
import com.nullxoid.android.data.e2ee.SavedChatE2ee
import com.nullxoid.android.data.e2ee.SavedChatKeyProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodesHostedWorkspaceAndProjectContracts() {
        val workspaces = json.decodeFromString<WorkspaceListResponse>(
            """
            {
              "active_workspace_id": "ws-123",
              "workspaces": [
                {"workspace_id": "ws-123", "name": "Lobby", "slug": "lobby", "is_system": true}
              ]
            }
            """.trimIndent()
        )
        val projects = json.decodeFromString<ProjectListResponse>(
            """
            {
              "projects": [
                {"id": "proj-123", "project_id": "proj-123", "workspace_id": "ws-123", "slug": "general"}
              ]
            }
            """.trimIndent()
        )

        assertEquals("ws-123", workspaces.workspaces.single().workspaceId)
        assertEquals("proj-123", projects.projects.single().resolvedProjectId)
    }

    @Test
    fun decodesHostedChatCreateEnvelope() {
        val response = json.decodeFromString<ChatCreateResponse>(
            """
            {
              "chat": {
                "id": "chat-123",
                "title": "hi",
                "workspace_id": "ws-123",
                "project_id": "proj-123"
              }
            }
            """.trimIndent()
        )

        assertEquals("chat-123", response.chat.id)
        assertEquals("ws-123", response.chat.workspaceId)
        assertEquals("proj-123", response.chat.projectId)
    }

    @Test
    fun serializesSavedChatWithoutPlaintextMessages() {
        val envelope = SavedChatE2ee.envelope(
            tenantId = "default",
            userId = "user-1",
            title = "private title",
            messages = listOf(ChatMessage(role = "user", content = "do not leak this")),
            keyProvider = FakeKeyProvider()
        )
        val encoded = json.encodeToString(
            ChatUpdateRequest.serializer(),
            ChatUpdateRequest(
                tenantId = "default",
                userId = "user-1",
                workspaceId = "ws-123",
                projectId = "proj-123",
                title = "private title",
                messages = emptyList(),
                e2ee = envelope
            )
        )

        assertTrue(encoded.contains("\"messages\":[]"))
        assertTrue(encoded.contains("\"saved_chat\""))
        assertTrue(encoded.contains("\"android_keystore_aes_gcm_v1\""))
        assertFalse(encoded.contains("do not leak this"))
    }

    private class FakeKeyProvider : SavedChatKeyProvider {
        override fun keyId(tenantId: String, userId: String): String = "test-key"

        override fun encrypt(
            tenantId: String,
            userId: String,
            aad: ByteArray,
            plaintext: ByteArray
        ): EncryptedBytes =
            EncryptedBytes(
                nonce = byteArrayOf(1, 2, 3, 4),
                ciphertext = plaintext.reversedArray()
            )

        override fun decrypt(
            tenantId: String,
            userId: String,
            aad: ByteArray,
            encrypted: EncryptedBytes
        ): ByteArray = encrypted.ciphertext.reversedArray()
    }
}
