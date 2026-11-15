package com.yogeshpaliyal.deepr.caldav

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CalDavPasswordCipher {
    private const val TAG = "CalDavPasswordCipher"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "deepr_caldav_password_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PREFIX_ENCRYPTED = "v1:"
    private const val PREFIX_PLAIN = "plain:"

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            "$PREFIX_ENCRYPTED$iv:$encrypted"
        } catch (e: Exception) {
            Log.w(TAG, "Keystore encryption failed, using fallback encoding", e)
            val encoded = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$PREFIX_PLAIN$encoded"
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            when {
                cipherText.startsWith(PREFIX_ENCRYPTED) -> {
                    val payload = cipherText.removePrefix(PREFIX_ENCRYPTED)
                    val parts = payload.split(":", limit = 2)
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val secretKey = getOrCreateSecretKey()
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
                }
                cipherText.startsWith(PREFIX_PLAIN) -> {
                    val encoded = cipherText.removePrefix(PREFIX_PLAIN)
                    String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> cipherText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt CalDAV password", e)
            ""
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
