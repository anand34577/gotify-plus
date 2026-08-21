package com.gotify.client.data.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CredentialCipher {
    private const val KEY_ALIAS = "gotify_plus_credentials_v1"
    private const val PREFIX = "enc:v1:"

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty() || isEncrypted(value)) return value
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(value: String?): String? {
        if (value.isNullOrEmpty() || !isEncrypted(value)) return value
        return runCatching {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(payload.size > 12) { "Invalid encrypted credential" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
        }.onFailure {
            Log.w("CredentialCipher", "Unable to decrypt a stored credential", it)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
