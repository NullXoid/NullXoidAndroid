package com.nullxoid.android.data.e2ee

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.nullxoid.android.data.model.ChatMessage
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
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
    fun preferredAccountKey(tenantId: String, userId: String): AccountEpochKey? =
        accountKey(tenantId, userId, 1)?.let { AccountEpochKey(epoch = 1, key = it) }
}

interface SavedChatAccountKeyStore : SavedChatAccountKeyProvider {
    fun storeAccountKey(tenantId: String, userId: String, epoch: Int, accountKey: ByteArray)
    fun hasAccountKey(tenantId: String, userId: String, epoch: Int): Boolean
}

data class EncryptedBytes(
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

data class AccountEpochKey(
    val epoch: Int,
    val key: ByteArray
)

object SavedChatE2ee {
    private const val AAD = "saved_chats_android_v1"
    private const val WEB_AAD_PREFIX = "nullxoid:saved_chats:v1"
    private const val DEVICE_RECOVERY_AAD = "nullxoid:e2ee:recovery:v1"
    private const val DEVICE_EPOCH_AAD = "nullxoid:e2ee:epoch:v1"
    private const val ANDROID_KEY_ENVELOPE = "android_keystore_aes_gcm_v1"
    private const val BROWSER_INDEXEDDB_KEY_ENVELOPE = "device_indexeddb_non_extractable_v1"
    private const val BROWSER_LOCAL_STORAGE_KEY_ENVELOPE = "device_local_storage_fallback_v1"
    private const val ACCOUNT_WRAPPED_KEY_ENVELOPE = "account_epoch_wrapped_saved_chat_key_v1"
    private const val RECOVERY_ITERATIONS = 210_000
    private const val ACCOUNT_KEY_BYTES = 32
    private const val CHAT_E2EE_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun envelope(
        tenantId: String,
        userId: String,
        title: String,
        messages: List<ChatMessage>,
        keyProvider: SavedChatKeyProvider,
        accountKeyProvider: SavedChatAccountKeyProvider? = null,
        requireAccountWrapped: Boolean = false
    ): JsonObject {
        val cleartext = json.encodeToString(SavedChatPayload(title = title, messages = messages))
            .toByteArray(Charsets.UTF_8)
        accountKeyProvider?.preferredAccountKey(tenantId, userId)?.let { accountKey ->
            return accountWrappedEnvelope(
                tenantId = tenantId,
                userId = userId,
                epoch = accountKey.epoch,
                accountKey = accountKey.key,
                cleartext = cleartext
            )
        }
        require(!requireAccountWrapped) {
            "Import the shared saved-chat E2EE key before syncing encrypted chats across devices."
        }
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

    private fun accountWrappedEnvelope(
        tenantId: String,
        userId: String,
        epoch: Int,
        accountKey: ByteArray,
        cleartext: ByteArray
    ): JsonObject {
        require(accountKey.size == ACCOUNT_KEY_BYTES) { "Account epoch key must be $ACCOUNT_KEY_BYTES bytes." }
        val contentKey = randomBytes(ACCOUNT_KEY_BYTES)
        val encrypted = encryptAesGcm(
            keyBytes = contentKey,
            plaintext = cleartext,
            aad = accountWrappedSavedChatAad(tenantId, userId, epoch).toByteArray(Charsets.UTF_8)
        )
        val wrappedKeyPayload = buildJsonObject {
            put("version", CHAT_E2EE_VERSION)
            put("purpose", "saved_chat_content_key")
            put("content_key", contentKey.base64Url())
        }.toString().toByteArray(Charsets.UTF_8)
        val wrappedKey = encryptAesGcm(
            keyBytes = accountKey,
            plaintext = wrappedKeyPayload,
            aad = accountEpochAad(tenantId, userId, epoch).toByteArray(Charsets.UTF_8)
        )
        val accountKeyId = accountRootKeyId(accountKey)
        return buildJsonObject {
            put(
                "saved_chat",
                buildJsonObject {
                    put("version", CHAT_E2EE_VERSION)
                    put("algorithm", "AES-GCM")
                    put("boundary", "client_or_device")
                    put("key_scope", "account_epoch_wrapped_saved_chat_key")
                    put("key_envelope", ACCOUNT_WRAPPED_KEY_ENVELOPE)
                    put("key_storage", "zero_knowledge_device_setup")
                    put("key_extractable", "wrapped_only")
                    put("key_id", accountKeyId)
                    put("epoch", epoch)
                    put("aad", "saved_chats_scope_v1")
                    put("nonce", encrypted.nonce.base64Url())
                    put("ciphertext", encrypted.ciphertext.base64Url())
                    put(
                        "wrapped_key",
                        buildJsonObject {
                            put("version", CHAT_E2EE_VERSION)
                            put("algorithm", "AES-GCM")
                            put("boundary", "account_epoch_key")
                            put("key_scope", "active_non_revoked_devices")
                            put("epoch", epoch)
                            put("key_id", accountKeyId)
                            put("plaintext_storage", "forbidden")
                            put("nonce", wrappedKey.nonce.base64Url())
                            put("ciphertext", wrappedKey.ciphertext.base64Url())
                        }
                    )
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

    fun recoveryAad(tenantId: String, userId: String): String =
        canonicalJson(
            mapOf(
                "purpose" to DEVICE_RECOVERY_AAD,
                "tenant_id" to tenantId.ifBlank { "default" },
                "user_id" to userId.ifBlank { "anonymous" }
            )
        )

    fun accountWrappedSavedChatAad(tenantId: String, userId: String, epoch: Int): String =
        "$WEB_AAD_PREFIX:${tenantId.ifBlank { "default" }}:${userId.ifBlank { "anonymous" }}:account_epoch:$epoch"

    fun recoverAccountKeyFromRecoveryEnvelope(
        tenantId: String,
        userId: String,
        recoverySecret: String,
        recoveryEnvelope: JsonObject
    ): ByteArray {
        require(recoverySecret.isNotBlank()) { "Recovery secret is required." }
        val salt = recoveryEnvelope["salt"]?.jsonPrimitive?.content?.decodeBase64Flexible()
            ?: error("Recovery envelope is missing salt.")
        val nonce = recoveryEnvelope["nonce"]?.jsonPrimitive?.content?.decodeBase64Flexible()
            ?: error("Recovery envelope is missing nonce.")
        val ciphertext = recoveryEnvelope["ciphertext"]?.jsonPrimitive?.content?.decodeBase64Flexible()
            ?: error("Recovery envelope is missing ciphertext.")
        val recoveryKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(recoverySecret.toCharArray(), salt, RECOVERY_ITERATIONS, 256))
            .encoded
        val accountKey = decryptAesGcm(
            keyBytes = recoveryKey,
            nonce = nonce,
            ciphertext = ciphertext,
            aad = recoveryAad(tenantId, userId).toByteArray(Charsets.UTF_8)
        )
        require(accountKey.size == ACCOUNT_KEY_BYTES) {
            "Recovered account key had ${accountKey.size} bytes; expected $ACCOUNT_KEY_BYTES."
        }
        return accountKey
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

private fun encryptAesGcm(
    keyBytes: ByteArray,
    plaintext: ByteArray,
    aad: ByteArray
): EncryptedBytes {
    val nonce = ByteArray(12).also { SavedChatE2eeRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    return EncryptedBytes(nonce = nonce, ciphertext = cipher.doFinal(plaintext))
}

private fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { SavedChatE2eeRandom.nextBytes(it) }

private object SavedChatE2eeRandom {
    private val secureRandom = SecureRandom()

    fun nextBytes(bytes: ByteArray) = secureRandom.nextBytes(bytes)
}

private fun accountRootKeyId(accountKey: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(accountKey)
        .base64Url()
        .take(22)
    return "account-root-key:$digest"
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
