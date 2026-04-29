package com.nullxoid.android.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
