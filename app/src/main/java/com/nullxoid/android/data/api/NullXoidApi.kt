package com.nullxoid.android.data.api

import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.ChatCreateRequest
import com.nullxoid.android.data.model.ChatCreateResponse
import com.nullxoid.android.data.model.ChatListResponse
import com.nullxoid.android.data.model.ChatRecord
import com.nullxoid.android.data.model.ChatUpdateRequest
import com.nullxoid.android.data.model.ClientManifest
import com.nullxoid.android.data.model.HealthFeatures
import com.nullxoid.android.data.model.LoginRequest
import com.nullxoid.android.data.model.ModelListResponse
import com.nullxoid.android.data.model.OidcCompleteRequest
import com.nullxoid.android.data.model.OidcStartRequest
import com.nullxoid.android.data.model.OidcStartResponse
import com.nullxoid.android.data.model.PasskeyCompleteRequest
import com.nullxoid.android.data.model.PasskeyCredentialsResponse
import com.nullxoid.android.data.model.PasskeyOptionsResponse
import com.nullxoid.android.data.model.ProjectCreateRequest
import com.nullxoid.android.data.model.ProjectCreateResponse
import com.nullxoid.android.data.model.ProjectListResponse
import com.nullxoid.android.data.model.ProjectSummary
import com.nullxoid.android.data.model.RemoteSettings
import com.nullxoid.android.data.model.WorkspaceListResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin typed wrapper over [HttpClient] implementing the endpoints the desktop
 * bridge uses. Paths are kept verbatim so the app works against the same
 * backend that NullXoid Desktop RC3 targets.
 */
class NullXoidApi(
    private val baseUrlProvider: () -> String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String): String {
        return BackendEndpoint.resolve(baseUrlProvider(), path)
    }

    private suspend inline fun <reified T> getJson(path: String): T =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url(path)).get().build()
            HttpClient.okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, body)
                json.decodeFromString<T>(body)
            }
        }

    private suspend inline fun <reified B, reified T> sendJson(
        method: String,
        path: String,
        body: B
    ): T {
        val payloadStr = json.encodeToString(serializer<B>(), body)
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url(path))
                .method(method, payloadStr.toRequestBody(jsonMedia))
                .build()
            HttpClient.okHttp.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, raw)
                json.decodeFromString<T>(raw)
            }
        }
    }

    private suspend fun sendNoBody(method: String, path: String) =
        withContext(Dispatchers.IO) {
            val empty = "".toRequestBody(jsonMedia)
            val req = Request.Builder().url(url(path)).method(method, empty).build()
            HttpClient.okHttp.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, raw)
            }
        }

    // ---- Auth -----------------------------------------------------------

    suspend fun fetchAuthState(): AuthState = getJson("/auth/me")

    suspend fun login(username: String, password: String): AuthState =
        sendJson("POST", "/auth/login", LoginRequest(username, password))

    suspend fun passkeyOptions(): PasskeyOptionsResponse = getJson("/auth/passkey/options")

    suspend fun completePasskey(request: PasskeyCompleteRequest): AuthState =
        sendJson("POST", "/auth/passkey/complete", request)

    suspend fun passkeyCredentials(): PasskeyCredentialsResponse =
        getJson("/auth/passkey/credentials")

    suspend fun passkeyRegistrationOptions(): PasskeyOptionsResponse =
        getJson("/auth/passkey/register/options")

    suspend fun completePasskeyRegistration(request: PasskeyCompleteRequest): PasskeyCredentialsResponse =
        sendJson("POST", "/auth/passkey/register/complete", request)

    suspend fun revokePasskey(credentialId: String) {
        sendNoBody("DELETE", "/auth/passkey/credentials/$credentialId")
    }

    suspend fun startOidc(request: OidcStartRequest): OidcStartResponse =
        sendJson("POST", "/auth/oidc/start", request)

    suspend fun completeOidc(request: OidcCompleteRequest): AuthState =
        sendJson("POST", "/auth/oidc/complete", request)

    suspend fun logout() {
        sendNoBody("POST", "/auth/logout")
        HttpClient.cookieJar.clear()
    }

    // ---- Health ---------------------------------------------------------

    suspend fun healthFeatures(): HealthFeatures = getJson("/health/features")

    suspend fun clientManifest(platform: String = "android"): ClientManifest =
        getJson("/api/client-manifest?platform=${urlEncode(platform)}")

    // ---- Settings / Models ---------------------------------------------

    suspend fun settings(): RemoteSettings = getJson("/api/settings")

    suspend fun updateSettings(updates: JsonObject): RemoteSettings =
        sendJson("PUT", "/api/settings", updates)

    suspend fun models(): ModelListResponse = getJson("/models")

    // ---- Workspaces / Projects -----------------------------------------

    suspend fun workspaces(): WorkspaceListResponse = getJson("/api/workspaces")

    suspend fun projects(workspaceId: String): ProjectListResponse {
        val encoded = URLEncoder.encode(workspaceId, StandardCharsets.UTF_8.name())
        return getJson("/api/projects?workspace_id=$encoded")
    }

    suspend fun createProject(req: ProjectCreateRequest): ProjectSummary =
        sendJson<ProjectCreateRequest, ProjectCreateResponse>("POST", "/api/projects", req).project

    // ---- Chats ----------------------------------------------------------

    suspend fun chats(
        tenantId: String,
        userId: String,
        workspaceId: String? = null,
        archived: Boolean = false
    ): ChatListResponse {
        val params = mutableListOf(
            "tenant_id=${urlEncode(tenantId)}",
            "user_id=${urlEncode(userId)}"
        )
        if (!workspaceId.isNullOrBlank()) {
            params += "workspace_id=${urlEncode(workspaceId)}"
        }
        if (archived) {
            params += "archived=true"
        }
        return getJson("/api/chats?${params.joinToString("&")}")
    }

    suspend fun createChat(req: ChatCreateRequest): ChatRecord {
        val payloadStr = json.encodeToString(ChatCreateRequest.serializer(), req)
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url("/api/chats"))
                .post(payloadStr.toRequestBody(jsonMedia))
                .build()
            HttpClient.okHttp.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, raw)
                runCatching { json.decodeFromString(ChatCreateResponse.serializer(), raw).chat }
                    .getOrElse { json.decodeFromString(ChatRecord.serializer(), raw) }
            }
        }
    }

    suspend fun updateChat(chatId: String, req: ChatUpdateRequest): ChatRecord {
        val payloadStr = json.encodeToString(ChatUpdateRequest.serializer(), req)
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url("/api/chats/${urlEncode(chatId)}"))
                .put(payloadStr.toRequestBody(jsonMedia))
                .build()
            HttpClient.okHttp.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, raw)
                runCatching { json.decodeFromString(ChatCreateResponse.serializer(), raw).chat }
                    .getOrElse { json.decodeFromString(ChatRecord.serializer(), raw) }
            }
        }
    }

    suspend fun archiveChat(chatId: String, archived: Boolean) {
        sendNoBody("POST", "/api/chats/$chatId/archive?archived=$archived")
    }
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

class ApiException(val code: Int, val body: String) :
    RuntimeException("HTTP $code: ${body.take(200)}")
