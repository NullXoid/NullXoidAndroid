package com.nullxoid.android.data.e2ee

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores account epoch keys only after wrapping them with a non-extractable
 * Android Keystore key. This is the native side of the zero-knowledge handoff:
 * the backend still only sees encrypted saved-chat envelopes.
 */
class AndroidSavedChatAccountKeyProvider(context: Context) : SavedChatAccountKeyStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun accountKey(tenantId: String, userId: String, epoch: Int): ByteArray? {
        val storageKey = storageKey(tenantId, userId, epoch)
        val nonce = prefs.getString("$storageKey:nonce", null)?.decodeBase64() ?: return null
        val ciphertext = prefs.getString("$storageKey:ciphertext", null)?.decodeBase64() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrapKey(wrapAlias(storageKey)), GCMParameterSpec(128, nonce))
            cipher.updateAAD(SavedChatE2ee.accountEpochAad(tenantId, userId, epoch).toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext)
        }.getOrNull()?.takeIf { it.size == ACCOUNT_KEY_BYTES }
    }

    override fun preferredAccountKey(tenantId: String, userId: String): AccountEpochKey? {
        val epoch = prefs.getInt(latestEpochKey(tenantId, userId), 1).coerceAtLeast(1)
        accountKey(tenantId, userId, epoch)?.let { return AccountEpochKey(epoch = epoch, key = it) }
        return accountKey(tenantId, userId, 1)?.let { AccountEpochKey(epoch = 1, key = it) }
    }

    override fun storeAccountKey(tenantId: String, userId: String, epoch: Int, accountKey: ByteArray) {
        require(accountKey.size == ACCOUNT_KEY_BYTES) { "Account epoch key must be 32 bytes." }
        val storageKey = storageKey(tenantId, userId, epoch)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrapKey(wrapAlias(storageKey)))
        cipher.updateAAD(SavedChatE2ee.accountEpochAad(tenantId, userId, epoch).toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(accountKey)
        prefs.edit()
            .putString("$storageKey:nonce", cipher.iv.base64())
            .putString("$storageKey:ciphertext", ciphertext.base64())
            .putInt(latestEpochKey(tenantId, userId), epoch)
            .apply()
    }

    override fun hasAccountKey(tenantId: String, userId: String, epoch: Int): Boolean =
        accountKey(tenantId, userId, epoch) != null

    fun removeAccountKey(tenantId: String, userId: String, epoch: Int) {
        val storageKey = storageKey(tenantId, userId, epoch)
        prefs.edit()
            .remove("$storageKey:nonce")
            .remove("$storageKey:ciphertext")
            .apply()
    }

    private fun latestEpochKey(tenantId: String, userId: String): String =
        "latest_epoch:${storageKey(tenantId, userId, 0).substringAfter(':')}"

    private fun getOrCreateWrapKey(alias: String): SecretKey {
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

    private fun storageKey(tenantId: String, userId: String, epoch: Int): String {
        val tenant = tenantId.ifBlank { "default" }
        val user = userId.ifBlank { "anonymous" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$tenant:$user:$epoch".toByteArray(Charsets.UTF_8))
            .take(16)
            .toByteArray()
            .base64Url()
        return "account_epoch:$digest"
    }

    private fun wrapAlias(storageKey: String): String =
        "$KEY_ALIAS_PREFIX${storageKey.substringAfter(':')}"

    private companion object {
        const val PREFS_NAME = "nullxoid_e2ee_account_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS_PREFIX = "nullxoid_account_key_wrap_"
        const val ACCOUNT_KEY_BYTES = 32
    }
}

private fun ByteArray.base64(): String =
    Base64.getEncoder().encodeToString(this)

private fun ByteArray.base64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private fun String.decodeBase64(): ByteArray =
    runCatching { Base64.getDecoder().decode(this) }
        .getOrElse { Base64.getUrlDecoder().decode(this) }
