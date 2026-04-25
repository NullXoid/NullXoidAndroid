package com.nullxoid.android.backend.nullbridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Backend-side NullBridge adapter for the Android embedded backend.
 *
 * The Compose UI talks to this embedded backend only. Service credentials stay
 * in backend configuration and are never returned by status or route responses.
 */
class NullBridgeAdapter(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    private val backendId: String = config("NULLBRIDGE_ANDROID_BACKEND_ID", "android_backend")
    private val audience: String = config("NULLBRIDGE_SERVICE_JWT_AUDIENCE", "nullbridge")
    private val baseUrl: String = config("NULLBRIDGE_URL", "").trimEnd('/')
    private val serviceSecret: String = config("NULLBRIDGE_ANDROID_SERVICE_JWT_SECRET", "")

    fun status(): JsonObject = buildJsonObject {
        put("ok", true)
        put("configured", isConfigured())
        put("backend_id", backendId)
        put("auth", "signed_service_jwt")
        put("credentials_exposed", false)
    }

    fun isConfigured(): Boolean = baseUrl.isNotBlank() && serviceSecret.length >= 24

    suspend fun routeCheck(
        capability: String,
        targetRole: String,
        targetId: String?,
        payload: JsonObject,
        actingUser: JsonObject
    ): JsonObject = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext buildJsonObject {
                put("ok", false)
                put("configured", false)
                put("errorCode", "nullbridge.not_configured")
                put("error", "NullBridge URL or Android service JWT secret is not configured")
            }
        }
        val requestId = UUID.randomUUID().toString()
        val requestBody = buildJsonObject {
            put("requestId", requestId)
            put("capability", capability)
            put("targetRole", targetRole)
            targetId?.takeIf { it.isNotBlank() }?.let { put("targetId", it) }
            put("actingUser", actingUser)
            put("payload", payload)
        }
        val httpRequest = Request.Builder()
            .url("$baseUrl/bridge/requests")
            .header("X-NullBridge-Service", backendId)
            .header("Authorization", "Bearer ${mintServiceJwt(capability, targetRole, requestId)}")
            .post(json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody(JSON))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val responseJson = runCatching { json.parseToJsonElement(bodyText).jsonObject }.getOrElse {
                buildJsonObject { put("error", bodyText.take(200)) }
            }
            if (!response.isSuccessful) {
                return@withContext buildJsonObject {
                    put("ok", false)
                    put("status_code", response.code)
                    put("requestId", requestId)
                    put("errorCode", responseJson["errorCode"]?.toString()?.trim('"') ?: "nullbridge.request_failed")
                    put("error", responseJson["error"]?.toString()?.trim('"') ?: "NullBridge request failed")
                }
            }
            val target = responseJson["target"]?.jsonObject
            buildJsonObject {
                put("ok", true)
                put("accepted", responseJson["accepted"]?.toString()?.toBooleanStrictOrNull() ?: true)
                put("requestId", responseJson["requestId"]?.toString()?.trim('"') ?: requestId)
                put("capability", responseJson["capability"]?.toString()?.trim('"') ?: capability)
                put("decision", responseJson["decision"]?.toString()?.trim('"') ?: "allowed")
                put(
                    "target",
                    buildJsonObject {
                        put("id", target?.get("id")?.toString()?.trim('"') ?: "")
                        put("role", target?.get("role")?.toString()?.trim('"') ?: "")
                        put("platform", target?.get("platform")?.toString()?.trim('"') ?: "")
                        put("trustClass", target?.get("trustClass")?.toString()?.trim('"') ?: "")
                    }
                )
            }
        }
    }

    private fun mintServiceJwt(capability: String, targetRole: String, requestId: String): String {
        val now = Instant.now().epochSecond
        val header = buildJsonObject {
            put("alg", "HS256")
            put("typ", "JWT")
        }
        val claims = buildJsonObject {
            put("iss", backendId)
            put("sub", backendId)
            put("aud", audience)
            put("iat", now)
            put("nbf", now - 5)
            put("exp", now + 60)
            put("jti", requestId)
            put("capability", capability)
            put("targetRole", targetRole)
        }
        val signingInput = listOf(
            b64(json.encodeToString(JsonObject.serializer(), header).toByteArray(Charsets.UTF_8)),
            b64(json.encodeToString(JsonObject.serializer(), claims).toByteArray(Charsets.UTF_8))
        ).joinToString(".")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(serviceSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return "$signingInput.${b64(mac.doFinal(signingInput.toByteArray(Charsets.US_ASCII)))}"
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun config(name: String, default: String): String =
        System.getProperty(name)?.takeIf { it.isNotBlank() }
            ?: System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: default

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}
