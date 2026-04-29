package com.nullxoid.android.data.e2ee

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.nullxoid.android.data.model.ChatMessage
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface SavedChatKeyProvider {
    fun keyId(tenantId: String, userId: String): String
    fun encrypt(tenantId: String, userId: String, aad: ByteArray, plaintext: ByteArray): EncryptedBytes
    fun decrypt(tenantId: String, userId: String, aad: ByteArray, encrypted: EncryptedBytes): ByteArray
}

data class EncryptedBytes(
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

object SavedChatE2ee {
    private const val AAD = "saved_chats_android_v1"
    private const val ANDROID_KEY_ENVELOPE = "android_keystore_aes_gcm_v1"
    private const val BROWSER_INDEXEDDB_KEY_ENVELOPE = "device_indexeddb_non_extractable_v1"
    private const val BROWSER_LOCAL_STORAGE_KEY_ENVELOPE = "device_local_storage_fallback_v1"

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun envelope(
        tenantId: String,
        userId: String,
        title: String,
        messages: List<ChatMessage>,
        keyProvider: SavedChatKeyProvider
    ): JsonObject {
        val cleartext = json.encodeToString(SavedChatPayload(title = title, messages = messages))
            .toByteArray(Charsets.UTF_8)
        val encrypted = keyProvider.encrypt(
            tenantId = tenantId,
            userId = userId,
            aad = AAD.toByteArray(Charsets.UTF_8),
            plaintext = cleartext
        )
        return buildJsonObject {
            put(
                "saved_chat",
                buildJsonObject {
                    put("version", 1)
                    put("algorithm", "AES-GCM-256")
                    put("boundary", "client_or_device")
                    put("key_scope", "android_keystore_non_extractable")
                    put("key_envelope", ANDROID_KEY_ENVELOPE)
                    put("key_storage", "android_keystore")
                    put("key_extractable", "false")
                    put("key_id", keyProvider.keyId(tenantId, userId))
                    put("aad", AAD)
                    put("nonce", encrypted.nonce.base64())
                    put("ciphertext", encrypted.ciphertext.base64())
                }
            )
        }
    }

    fun decryptPayload(
        tenantId: String,
        userId: String,
        e2ee: JsonObject?,
        keyProvider: SavedChatKeyProvider
    ): SavedChatPayload? {
        val savedChat = e2ee?.get("saved_chat")?.jsonObject ?: return null
        val version = savedChat["version"]?.jsonPrimitive?.intOrNull ?: return null
        if (version != 1) return null
        val aad = savedChat["aad"]?.jsonPrimitive?.content ?: AAD
        val nonce = savedChat["nonce"]?.jsonPrimitive?.content?.base64Decode() ?: return null
        val ciphertext = savedChat["ciphertext"]?.jsonPrimitive?.content?.base64Decode() ?: return null
        val cleartext = keyProvider.decrypt(
            tenantId = tenantId,
            userId = userId,
            aad = aad.toByteArray(Charsets.UTF_8),
            encrypted = EncryptedBytes(nonce = nonce, ciphertext = ciphertext)
        )
        return json.decodeFromString<SavedChatPayload>(cleartext.toString(Charsets.UTF_8))
    }

    fun inspectEnvelope(e2ee: JsonObject?, localKeyId: String?): SavedChatEnvelopeInfo {
        val savedChat = e2ee?.get("saved_chat")?.jsonObject ?: return SavedChatEnvelopeInfo(status = "none")
        val version = savedChat["version"]?.jsonPrimitive?.intOrNull ?: 0
        val keyEnvelope = savedChat["key_envelope"]?.jsonPrimitive?.content.orEmpty()
        val keyId = savedChat["key_id"]?.jsonPrimitive?.content.orEmpty()
        val status = when {
            version != 1 -> "unsupported_version"
            keyEnvelope == ANDROID_KEY_ENVELOPE && keyId == localKeyId -> "android_local_key"
            keyEnvelope == ANDROID_KEY_ENVELOPE -> "android_other_install"
            keyEnvelope == BROWSER_INDEXEDDB_KEY_ENVELOPE -> "browser_indexeddb_key"
            keyEnvelope == BROWSER_LOCAL_STORAGE_KEY_ENVELOPE -> "browser_local_storage_key"
            keyEnvelope.isBlank() -> "missing_key_envelope"
            else -> "unsupported_key_envelope"
        }
        return SavedChatEnvelopeInfo(
            status = status,
            version = version,
            keyEnvelope = keyEnvelope,
            keyId = keyId,
            aad = savedChat["aad"]?.jsonPrimitive?.content.orEmpty()
        )
    }
}

class AndroidSavedChatKeyProvider : SavedChatKeyProvider {
    override fun keyId(tenantId: String, userId: String): String =
        keyAlias(tenantId, userId).removePrefix(KEY_ALIAS_PREFIX)

    override fun encrypt(
        tenantId: String,
        userId: String,
        aad: ByteArray,
        plaintext: ByteArray
    ): EncryptedBytes {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(keyAlias(tenantId, userId)))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedBytes(nonce = cipher.iv, ciphertext = ciphertext)
    }

    override fun decrypt(
        tenantId: String,
        userId: String,
        aad: ByteArray,
        encrypted: EncryptedBytes
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(keyAlias(tenantId, userId)), GCMParameterSpec(128, encrypted.nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(encrypted.ciphertext)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyAlias(tenantId: String, userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$tenantId:$userId".toByteArray(Charsets.UTF_8))
            .take(16)
            .toByteArray()
            .base64Url()
        return "$KEY_ALIAS_PREFIX$digest"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "nullxoid_saved_chat_"
    }
}

@Serializable
data class SavedChatPayload(
    val title: String,
    val messages: List<ChatMessage>
)

data class SavedChatEnvelopeInfo(
    val status: String,
    val version: Int = 0,
    val keyEnvelope: String = "",
    val keyId: String = "",
    val aad: String = ""
)

private fun ByteArray.base64(): String =
    Base64.getEncoder().encodeToString(this)

private fun ByteArray.base64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private fun String.base64Decode(): ByteArray =
    Base64.getDecoder().decode(this)
