package com.nullxoid.android.data.e2ee

import com.nullxoid.android.data.model.ChatMessage
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
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

    @Test
    fun savedChatEnvelopeUsesAccountWrappedKeyWhenAccountEpochKeyIsAvailable() {
        val tenantId = "default"
        val userId = "shared-user"
        val accountKey = ByteArray(32) { (it + 9).toByte() }
        val envelope = SavedChatE2ee.envelope(
            tenantId = tenantId,
            userId = userId,
            title = "shared mobile chat",
            messages = listOf(ChatMessage(role = "user", content = "private mobile prompt")),
            keyProvider = FakeKeyProvider(),
            accountKeyProvider = StaticAccountKeyProvider(accountKey, epoch = 2)
        )

        val savedChat = envelope["saved_chat"]!!.jsonObject
        assertEquals("account_epoch_wrapped_saved_chat_key_v1", savedChat["key_envelope"]!!.jsonPrimitive.content)
        assertEquals("zero_knowledge_device_setup", savedChat["key_storage"]!!.jsonPrimitive.content)
        assertEquals(2, savedChat["epoch"]!!.jsonPrimitive.content.toInt())
        assertTrue(savedChat["wrapped_key"]!!.jsonObject["ciphertext"]!!.jsonPrimitive.content.isNotBlank())
        assertFalse(savedChat.toString().contains("private mobile prompt"))

        val payload = SavedChatE2ee.decryptPayload(
            tenantId = tenantId,
            userId = userId,
            e2ee = envelope,
            keyProvider = FakeKeyProvider(),
            accountKeyProvider = StaticAccountKeyProvider(accountKey, epoch = 2)
        )
        assertEquals("shared mobile chat", payload?.title)
        assertEquals("private mobile prompt", payload?.messages?.single()?.content)
    }

    @Test
    fun syncedSavedChatEnvelopeRequiresAccountWrappedKeyWhenRequested() {
        try {
            SavedChatE2ee.envelope(
                tenantId = "default",
                userId = "shared-user",
                title = "shared mobile chat",
                messages = listOf(ChatMessage(role = "user", content = "private mobile prompt")),
                keyProvider = FakeKeyProvider(),
                accountKeyProvider = null,
                requireAccountWrapped = true
            )
            throw AssertionError("Expected shared saved-chat key requirement")
        } catch (err: IllegalArgumentException) {
            assertTrue(err.message.orEmpty().contains("shared saved-chat E2EE key"))
        }
    }

    @Test
    fun savedChatEnvelopeInspectionClassifiesLocalAndroidKey() {
        val envelope = SavedChatE2ee.envelope(
            tenantId = "default",
            userId = "user-1",
            title = "hi",
            messages = listOf(ChatMessage(role = "user", content = "private prompt")),
            keyProvider = FakeKeyProvider()
        )

        val info = SavedChatE2ee.inspectEnvelope(envelope, localKeyId = "test-key")

        assertEquals("android_local_key", info.status)
        assertEquals("android_keystore_aes_gcm_v1", info.keyEnvelope)
    }

    @Test
    fun savedChatEnvelopeInspectionClassifiesBrowserKeyAsLocked() {
        val envelope = buildJsonObject {
            put(
                "saved_chat",
                buildJsonObject {
                    put("version", 1)
                    put("algorithm", "AES-GCM")
                    put("boundary", "client_or_device")
                    put("key_envelope", "device_indexeddb_non_extractable_v1")
                    put("key_id", "saved-chat-key:browser")
                    put("aad", "saved_chats_scope_v1")
                    put("nonce", "AAAAAAAAAAAAAAAA")
                    put("ciphertext", "ciphertext")
                }
            )
        }

        val info = SavedChatE2ee.inspectEnvelope(envelope, localKeyId = "test-key")

        assertEquals("browser_indexeddb_key", info.status)
    }

    @Test
    fun savedChatEnvelopeInspectionClassifiesAccountWrappedKeyAsSharedHandoffTarget() {
        val envelope = buildJsonObject {
            put(
                "saved_chat",
                buildJsonObject {
                    put("version", 1)
                    put("algorithm", "AES-GCM")
                    put("boundary", "client_or_device")
                    put("key_envelope", "account_epoch_wrapped_saved_chat_key_v1")
                    put("key_id", "account-root-key:shared")
                    put("epoch", 2)
                    put("aad", "saved_chats_scope_v1")
                    put("nonce", "AAAAAAAAAAAAAAAA")
                    put("ciphertext", "ciphertext")
                    put(
                        "wrapped_key",
                        buildJsonObject {
                            put("plaintext_storage", "forbidden")
                        }
                    )
                }
            )
        }

        val info = SavedChatE2ee.inspectEnvelope(envelope, localKeyId = "test-key")

        assertEquals("account_epoch_wrapped_key", info.status)
    }

    @Test
    fun accountWrappedSavedChatEnvelopeDecryptsWithAccountEpochKey() {
        val tenantId = "default"
        val userId = "shared-user"
        val epoch = 3
        val accountKey = ByteArray(32) { (it + 1).toByte() }
        val contentKey = ByteArray(32) { (it + 41).toByte() }
        val savedPayload = """{"title":"Shared private title","messages":[{"role":"user","content":"private prompt"}]}"""
        val wrappedPayload = """{"version":1,"purpose":"saved_chat_content_key","content_key":"${contentKey.base64Url()}"}"""

        val wrappedEncrypted = encryptAesGcm(
            key = accountKey,
            nonce = ByteArray(12) { (it + 7).toByte() },
            aad = SavedChatE2ee.accountEpochAad(tenantId, userId, epoch),
            plaintext = wrappedPayload
        )
        val savedEncrypted = encryptAesGcm(
            key = contentKey,
            nonce = ByteArray(12) { (it + 19).toByte() },
            aad = SavedChatE2ee.accountWrappedSavedChatAad(tenantId, userId, epoch),
            plaintext = savedPayload
        )
        val envelope = buildJsonObject {
            put(
                "saved_chat",
                buildJsonObject {
                    put("version", 1)
                    put("algorithm", "AES-GCM")
                    put("boundary", "client_or_device")
                    put("key_scope", "account_epoch_wrapped_saved_chat_key")
                    put("key_envelope", "account_epoch_wrapped_saved_chat_key_v1")
                    put("key_id", "account-root-key:shared")
                    put("epoch", epoch)
                    put("aad", "saved_chats_scope_v1")
                    put("nonce", savedEncrypted.nonce.base64Url())
                    put("ciphertext", savedEncrypted.ciphertext.base64Url())
                    put(
                        "wrapped_key",
                        buildJsonObject {
                            put("version", 1)
                            put("algorithm", "AES-GCM")
                            put("boundary", "account_epoch_key")
                            put("key_scope", "active_non_revoked_devices")
                            put("epoch", epoch)
                            put("plaintext_storage", "forbidden")
                            put("nonce", wrappedEncrypted.nonce.base64Url())
                            put("ciphertext", wrappedEncrypted.ciphertext.base64Url())
                        }
                    )
                }
            )
        }

        val payload = SavedChatE2ee.decryptPayload(
            tenantId = tenantId,
            userId = userId,
            e2ee = envelope,
            keyProvider = FakeKeyProvider(),
            accountKeyProvider = StaticAccountKeyProvider(accountKey, epoch = epoch)
        )

        assertEquals("Shared private title", payload?.title)
        assertEquals("private prompt", payload?.messages?.single()?.content)
    }

    @Test
    fun accountWrappedSavedChatEnvelopeStaysLockedWithoutAccountEpochKey() {
        val tenantId = "default"
        val userId = "shared-user"
        val epoch = 3
        val accountKey = ByteArray(32) { (it + 1).toByte() }
        val contentKey = ByteArray(32) { (it + 41).toByte() }
        val wrappedEncrypted = encryptAesGcm(
            key = accountKey,
            nonce = ByteArray(12) { (it + 7).toByte() },
            aad = SavedChatE2ee.accountEpochAad(tenantId, userId, epoch),
            plaintext = """{"version":1,"purpose":"saved_chat_content_key","content_key":"${contentKey.base64Url()}"}"""
        )
        val savedEncrypted = encryptAesGcm(
            key = contentKey,
            nonce = ByteArray(12) { (it + 19).toByte() },
            aad = SavedChatE2ee.accountWrappedSavedChatAad(tenantId, userId, epoch),
            plaintext = """{"title":"Shared private title","messages":[]}"""
        )
        val envelope = buildJsonObject {
            put(
                "saved_chat",
                buildJsonObject {
                    put("version", 1)
                    put("algorithm", "AES-GCM")
                    put("boundary", "client_or_device")
                    put("key_envelope", "account_epoch_wrapped_saved_chat_key_v1")
                    put("key_id", "account-root-key:shared")
                    put("epoch", epoch)
                    put("nonce", savedEncrypted.nonce.base64Url())
                    put("ciphertext", savedEncrypted.ciphertext.base64Url())
                    put(
                        "wrapped_key",
                        buildJsonObject {
                            put("version", 1)
                            put("algorithm", "AES-GCM")
                            put("epoch", epoch)
                            put("plaintext_storage", "forbidden")
                            put("nonce", wrappedEncrypted.nonce.base64Url())
                            put("ciphertext", wrappedEncrypted.ciphertext.base64Url())
                        }
                    )
                }
            )
        }

        val payload = SavedChatE2ee.decryptPayload(
            tenantId = tenantId,
            userId = userId,
            e2ee = envelope,
            keyProvider = FakeKeyProvider()
        )

        assertEquals(null, payload)
        assertEquals("account_epoch_wrapped_key", SavedChatE2ee.inspectEnvelope(envelope, localKeyId = null).status)
    }

    @Test
    fun recoveryEnvelopeRestoresAccountEpochKey() {
        val tenantId = "default"
        val userId = "shared-user"
        val recoverySecret = "correct horse battery staple"
        val accountKey = ByteArray(32) { (it + 5).toByte() }
        val salt = ByteArray(16) { (it + 17).toByte() }
        val recoveryKey = deriveRecoveryKey(recoverySecret, salt)
        val encrypted = encryptAesGcm(
            key = recoveryKey,
            nonce = ByteArray(12) { (it + 29).toByte() },
            aad = SavedChatE2ee.recoveryAad(tenantId, userId),
            plaintext = accountKey
        )
        val recoveryEnvelope = buildJsonObject {
            put("version", 1)
            put("algorithm", "AES-GCM-256")
            put("kdf", "PBKDF2-SHA256")
            put("iterations", 210_000)
            put("plaintext_storage", "forbidden")
            put("salt", salt.base64Url())
            put("nonce", encrypted.nonce.base64Url())
            put("ciphertext", encrypted.ciphertext.base64Url())
        }

        val recovered = SavedChatE2ee.recoverAccountKeyFromRecoveryEnvelope(
            tenantId = tenantId,
            userId = userId,
            recoverySecret = recoverySecret,
            recoveryEnvelope = recoveryEnvelope
        )

        assertArrayEquals(accountKey, recovered)
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

    private class StaticAccountKeyProvider(private val key: ByteArray, private val epoch: Int = 1) : SavedChatAccountKeyProvider {
        override fun accountKey(tenantId: String, userId: String, epoch: Int): ByteArray? =
            if (epoch == this.epoch) key else null

        override fun preferredAccountKey(tenantId: String, userId: String): AccountEpochKey =
            AccountEpochKey(epoch = epoch, key = key)
    }

    private data class TestEncrypted(val nonce: ByteArray, val ciphertext: ByteArray)

    private fun encryptAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        aad: String,
        plaintext: String
    ): TestEncrypted {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return TestEncrypted(nonce = nonce, ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    private fun encryptAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        aad: String,
        plaintext: ByteArray
    ): TestEncrypted {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return TestEncrypted(nonce = nonce, ciphertext = cipher.doFinal(plaintext))
    }

    private fun deriveRecoveryKey(recoverySecret: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(recoverySecret.toCharArray(), salt, 210_000, 256))
            .encoded

    private fun ByteArray.base64Url(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(this)
}
