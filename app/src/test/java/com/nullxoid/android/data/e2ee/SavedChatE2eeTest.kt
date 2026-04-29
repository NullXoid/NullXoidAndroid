package com.nullxoid.android.data.e2ee

import com.nullxoid.android.data.model.ChatMessage
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedChatE2eeTest {
    @Test
    fun savedChatEnvelopeMarksAndroidClientBoundary() {
        val envelope = SavedChatE2ee.envelope(
            tenantId = "default",
            userId = "user-1",
            title = "hi",
            messages = listOf(ChatMessage(role = "user", content = "private prompt")),
            keyProvider = FakeKeyProvider()
        )

        val savedChat = envelope["saved_chat"]!!.jsonObject
        assertEquals("client_or_device", savedChat["boundary"]!!.jsonPrimitive.content)
        assertEquals("android_keystore_aes_gcm_v1", savedChat["key_envelope"]!!.jsonPrimitive.content)
        assertEquals("false", savedChat["key_extractable"]!!.jsonPrimitive.content)
        assertEquals("saved_chats_android_v1", savedChat["aad"]!!.jsonPrimitive.content)
        assertTrue(savedChat["nonce"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(savedChat["ciphertext"]!!.jsonPrimitive.content.isNotBlank())
        assertFalse(savedChat.toString().contains("private prompt"))
    }

    @Test
    fun savedChatEnvelopeDecryptsWithSameDeviceKeyProvider() {
        val keyProvider = FakeKeyProvider()
        val envelope = SavedChatE2ee.envelope(
            tenantId = "default",
            userId = "user-1",
            title = "hi",
            messages = listOf(ChatMessage(role = "assistant", content = "private answer")),
            keyProvider = keyProvider
        )

        val payload = SavedChatE2ee.decryptPayload(
            tenantId = "default",
            userId = "user-1",
            e2ee = envelope,
            keyProvider = keyProvider
        )

        assertEquals("hi", payload?.title)
        assertEquals("private answer", payload?.messages?.single()?.content)
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
