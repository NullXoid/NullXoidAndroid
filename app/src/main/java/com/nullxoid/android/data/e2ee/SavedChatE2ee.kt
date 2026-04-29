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
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

interface SavedChatAccountKeyProvider {
    fun accountKey(tenantId: String, userId: String, epoch: Int): ByteArray?
}

data class EncryptedBytes(
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

object SavedChatE2ee {
    private const val AAD = "saved_chats_android_v1"
    private const val WEB_AAD_PREFIX = "nullxoid:saved_chats:v1"
    private const val DEVICE_EPOCH_AAD = "nullxoid:e2ee:epoch:v1"
    private const val ANDROID_KEY_ENVELOPE = "android_keystore_aes_gcm_v1"
    private const val BROWSER_INDEXEDDB_KEY_ENVELOPE = "device_indexeddb_non_extractable_v1"
    private const val BROWSER_LOCAL_STORAGE_KEY_ENVELOPE = "device_local_storage_fallback_v1"
    private const val ACCOUNT_WRAPPED_KEY_ENVELOPE = "account_epoch_wrapped_saved_chat_key_v1"

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
        keyProvider: SavedChatKeyProvider,
        accountKeyProvider: SavedChatAccountKeyProvider? = null
    ): SavedChatPayload? {
        val savedChat = e2ee?.get("saved_chat")?.jsonObject ?: return null
        val version = savedChat["version"]?.jsonPrimitive?.intOrNull ?: return null
        if (version != 1) return null
        if (savedChat["key_envelope"]?.jsonPrimitive?.content == ACCOUNT_WRAPPED_KEY_ENVELOPE) {
            return decryptAccountWrappedPayload(
                tenantId = tenantId,
                userId = userId,
                savedChat = savedChat,
                accountKeyProvider = accountKeyProvider
            )
        }
        val aad = savedChat["aad"]?.jsonPrimitive?.content ?: AAD
        val nonce = savedChat["nonce"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null
        val ciphertext = savedChat["ciphertext"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null
        val cleartext = keyProvider.decrypt(
            tenantId = tenantId,
            userId = userId,
            aad = aad.toByteArray(Charsets.UTF_8),
            encrypted = EncryptedBytes(nonce = nonce, ciphertext = ciphertext)
        )
        return json.decodeFromString<SavedChatPayload>(cleartext.toString(Charsets.UTF_8))
    }

    private fun decryptAccountWrappedPayload(
        tenantId: String,
        userId: String,
        savedChat: JsonObject,
        accountKeyProvider: SavedChatAccountKeyProvider?
    ): SavedChatPayload? {
        val epoch = savedChat["epoch"]?.jsonPrimitive?.intOrNull ?: 1
        val accountKey = accountKeyProvider?.accountKey(tenantId, userId, epoch) ?: return null
        val wrappedKey = savedChat["wrapped_key"]?.jsonObject ?: return null
        val wrappedPlaintext = decryptAesGcm(
            keyBytes = accountKey,
            nonce = wrappedKey["nonce"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null,
            ciphertext = wrappedKey["ciphertext"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null,
            aad = accountEpochAad(tenantId, userId, epoch).toByteArray(Charsets.UTF_8)
        )
        val wrappedPayload = json.parseToJsonElement(wrappedPlaintext.toString(Charsets.UTF_8)).jsonObject
        if (wrappedPayload["purpose"]?.jsonPrimitive?.content != "saved_chat_content_key") return null
        if ((wrappedPayload["version"]?.jsonPrimitive?.intOrNull ?: 0) != 1) return null
        val contentKey = wrappedPayload["content_key"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null
        val cleartext = decryptAesGcm(
            keyBytes = contentKey,
            nonce = savedChat["nonce"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null,
            ciphertext = savedChat["ciphertext"]?.jsonPrimitive?.content?.decodeBase64Flexible() ?: return null,
            aad = accountWrappedSavedChatAad(tenantId, userId, epoch).toByteArray(Charsets.UTF_8)
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
            keyEnvelope == ACCOUNT_WRAPPED_KEY_ENVELOPE -> "account_epoch_wrapped_key"
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

    fun accountEpochAad(tenantId: String, userId: String, epoch: Int): String =
        canonicalJson(
            mapOf(
                "purpose" to DEVICE_EPOCH_AAD,
                "tenant_id" to tenantId.ifBlank { "default" },
                "user_id" to userId.ifBlank { "anonymous" },
                "epoch" to epoch
            )
        )

    fun accountWrappedSavedChatAad(tenantId: String, userId: String, epoch: Int): String =
        "$WEB_AAD_PREFIX:${tenantId.ifBlank { "default" }}:${userId.ifBlank { "anonymous" }}:account_epoch:$epoch"
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

private fun String.decodeBase64Flexible(): ByteArray =
    runCatching { Base64.getDecoder().decode(this) }
        .getOrElse { Base64.getUrlDecoder().decode(this) }

private fun decryptAesGcm(
    keyBytes: ByteArray,
    nonce: ByteArray,
    ciphertext: ByteArray,
    aad: ByteArray
): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    return cipher.doFinal(ciphertext)
}

private fun canonicalJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> Json.encodeToString(value)
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> value.keys
        .map { it.toString() }
        .sorted()
        .joinToString(prefix = "{", postfix = "}") { key ->
            "${Json.encodeToString(key)}:${canonicalJson(value[key])}"
        }
    is List<*> -> value.joinToString(prefix = "[", postfix = "]") { canonicalJson(it) }
    is JsonElement -> value.toString()
    else -> Json.encodeToString(value.toString())
}
