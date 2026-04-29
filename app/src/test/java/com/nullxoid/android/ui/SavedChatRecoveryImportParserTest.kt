package com.nullxoid.android.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.nullxoid.android.data.e2ee.SavedChatE2ee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SavedChatRecoveryImportParserTest {
    @Test
    fun compactQrImportExpandsToAndroidRecoveryBundle() {
        val parsed = parseSavedChatRecoveryImport(
            Json,
            """
                {
                  "v":1,
                  "p":"nx.aik1",
                  "t":"default",
                  "u":"user-123",
                  "e":7,
                  "r":{"s":"salt-value","n":"nonce-value","c":"cipher-value"},
                  "k":"secret-value"
                }
            """.trimIndent()
        )

        assertEquals("compact_qr_android_import", parsed["kit_type"]!!.jsonPrimitive.content)
        assertEquals("false", parsed["requires_separate_recovery_secret"]!!.jsonPrimitive.content)
        assertEquals("default", parsed["tenant_id"]!!.jsonPrimitive.content)
        assertEquals("user-123", parsed["user_id"]!!.jsonPrimitive.content)
        assertEquals("7", parsed["epoch"]!!.jsonPrimitive.content)
        assertEquals("secret-value", parsed["recovery_secret"]!!.jsonPrimitive.content)
        assertEquals("salt-value", parsed["recovery_envelope"]!!.jsonObject["salt"]!!.jsonPrimitive.content)
        assertEquals("nonce-value", parsed["recovery_envelope"]!!.jsonObject["nonce"]!!.jsonPrimitive.content)
        assertEquals("cipher-value", parsed["recovery_envelope"]!!.jsonObject["ciphertext"]!!.jsonPrimitive.content)
    }

    @Test
    fun embeddedImportSecretWinsOverStaleTypedSecret() {
        val parsed = parseSavedChatRecoveryImport(
            Json,
            """
                {"v":1,"p":"nx.aik1","t":"default","u":"user-123","e":7,"r":{"s":"salt","n":"nonce","c":"cipher"},"k":"fresh-embedded-secret"}
            """.trimIndent()
        )

        assertEquals("fresh-embedded-secret", resolveSavedChatRecoverySecret("old typed secret", parsed))
    }

    @Test
    fun compactQrImportRecoversBrowserGeneratedEnvelope() {
        val parsed = parseSavedChatRecoveryImport(
            Json,
            """
                {"v":1,"p":"nx.aik1","t":"default","u":"user-admin-1776910271","e":1,"r":{"s":"msMCoTMJZd40rLQW_ZQ95A","n":"dvgR5tGbUysksl5p","c":"9rHcv7a0K-bc4N4jGoTpMPmeiiHIAf3jbU8Vj-U4-XnEh6B2_wn7lLTdxpwSqvlF"},"k":"test-secret-ascii-123"}
            """.trimIndent()
        )
        assertEquals(
            "{\"purpose\":\"nullxoid:e2ee:recovery:v1\",\"tenant_id\":\"default\",\"user_id\":\"user-admin-1776910271\"}",
            SavedChatE2ee.recoveryAad("default", "user-admin-1776910271")
        )
        val accountKey = SavedChatE2ee.recoverAccountKeyFromRecoveryEnvelope(
            tenantId = "default",
            userId = "user-admin-1776910271",
            recoverySecret = resolveSavedChatRecoverySecret("", parsed),
            recoveryEnvelope = parsed["recovery_envelope"]!!.jsonObject
        )

        assertArrayEquals(ByteArray(32) { (it + 1).toByte() }, accountKey)
    }
}
