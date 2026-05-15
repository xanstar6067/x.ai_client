package com.adam.xai_client.data.local.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class ApiKeyCipher {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encryptedBytes.size)
        cipher.iv.copyInto(payload)
        encryptedBytes.copyInto(payload, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > GCM_IV_SIZE_BYTES)

        val iv = payload.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val encryptedBytes = payload.copyOfRange(GCM_IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        return cipher.doFinal(encryptedBytes).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existingKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
    }
}
