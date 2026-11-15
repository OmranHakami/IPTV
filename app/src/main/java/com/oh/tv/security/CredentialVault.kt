package com.oh.tv.security

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

data class XtreamCredentials(val username: String, val password: String)

class CredentialVault(context: Context) {
    private val preferences = context.getSharedPreferences("oh_secure_credentials", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun save(alias: String, credentials: XtreamCredentials) {
        val plaintext = "${credentials.username}\u0000${credentials.password}".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(plaintext)
        preferences.edit()
            .putString("$alias.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("$alias.value", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        plaintext.fill(0)
    }

    fun load(alias: String): XtreamCredentials? = runCatching {
        val iv = Base64.decode(preferences.getString("$alias.iv", null), Base64.NO_WRAP)
        val ciphertext = Base64.decode(preferences.getString("$alias.value", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        val parts = cipher.doFinal(ciphertext).toString(Charsets.UTF_8).split('\u0000', limit = 2)
        XtreamCredentials(parts[0], parts[1])
    }.getOrNull()

    fun remove(alias: String) {
        preferences.edit().remove("$alias.iv").remove("$alias.value").apply()
    }

    private fun key(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "oh_xtream_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

