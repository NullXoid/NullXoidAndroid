package com.nullxoid.android.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PkcePair(
    val verifier: String,
    val challenge: String,
    val method: String = Pkce.CHALLENGE_METHOD
)

object Pkce {
    const val CHALLENGE_METHOD = "S256"
    private val secureRandom = SecureRandom()

    fun generatePair(byteCount: Int = 32): PkcePair {
        val verifier = generateVerifier(byteCount)
        return PkcePair(verifier = verifier, challenge = challenge(verifier))
    }

    fun generateVerifier(byteCount: Int = 32): String {
        require(byteCount in 32..96) { "PKCE verifier entropy must be 32..96 bytes" }
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return base64Url(bytes)
    }

    fun challenge(verifier: String): String {
        require(verifier.length >= 43) { "PKCE verifier is too short" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
