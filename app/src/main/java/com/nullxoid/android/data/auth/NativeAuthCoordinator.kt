package com.nullxoid.android.data.auth

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.nullxoid.android.data.api.NullXoidApi
import com.nullxoid.android.data.model.AuthState
import com.nullxoid.android.data.model.OidcStartRequest
import com.nullxoid.android.data.model.PasskeyCompleteRequest
import com.nullxoid.android.data.model.PasskeyCredentialsResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class OidcLaunch(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String
)

class NativeAuthCoordinator(
    private val api: NullXoidApi,
    private val credentialManagerFactory: (Context) -> CredentialManager = { CredentialManager.create(it) }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun signInWithPasskey(context: Context): AuthState {
        val options = api.passkeyOptions()
        val requestJson = options.requestJson
            ?: options.publicKeySnake?.let(::encodePublicKey)
            ?: options.publicKey?.let(::encodePublicKey)
            ?: error("Passkey options response did not include request_json or public_key")
        val credentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(GetPublicKeyCredentialOption(requestJson = requestJson))
            .build()
        val response = credentialManagerFactory(context).getCredential(
            context = context,
            request = credentialRequest
        )
        val credential = response.credential as? PublicKeyCredential
            ?: error("Passkey provider returned ${response.credential::class.java.simpleName}")
        return api.completePasskey(
            PasskeyCompleteRequest(
                requestId = options.requestId,
                credentialJson = credential.authenticationResponseJson
            )
        )
    }

    suspend fun registerPasskey(context: Context): PasskeyCredentialsResponse {
        val options = api.passkeyRegistrationOptions()
        val requestJson = options.requestJson
            ?: options.publicKeySnake?.let(::encodePublicKey)
            ?: options.publicKey?.let(::encodePublicKey)
            ?: error("Passkey registration response did not include request_json or public_key")
        val response = credentialManagerFactory(context).createCredential(
            context = context,
            request = CreatePublicKeyCredentialRequest(requestJson = requestJson)
        )
        val credential = response as? CreatePublicKeyCredentialResponse
            ?: error("Passkey provider returned ${response::class.java.simpleName}")
        return api.completePasskeyRegistration(
            PasskeyCompleteRequest(
                requestId = options.requestId,
                credentialJson = credential.registrationResponseJson
            )
        )
    }

    suspend fun startOidcSignIn(redirectUri: String): OidcLaunch {
        val pkce = Pkce.generatePair()
        val response = api.startOidc(
            OidcStartRequest(
                redirectUri = redirectUri,
                codeChallenge = pkce.challenge,
                codeChallengeMethod = pkce.method
            )
        )
        return OidcLaunch(
            authorizationUrl = response.authorizationUrl,
            state = response.state,
            codeVerifier = pkce.verifier,
            redirectUri = redirectUri
        )
    }

    private fun encodePublicKey(publicKey: JsonObject): String =
        json.encodeToString(publicKey)
}
