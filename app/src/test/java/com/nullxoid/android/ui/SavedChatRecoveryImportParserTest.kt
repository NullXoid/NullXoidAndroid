package com.nullxoid.android.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
}
