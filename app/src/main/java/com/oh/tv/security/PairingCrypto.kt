package com.oh.tv.security

import android.util.Base64
import com.oh.tv.data.remote.AccountInput
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class PairingCrypto private constructor(private val keyPair: KeyPair) {
    private val publicKey = keyPair.public as RSAPublicKey
    val modulusBase64: String = Base64.encodeToString(publicKey.modulus.toByteArray().dropLeadingZero(), Base64.NO_WRAP)
    val exponent: String = publicKey.publicExponent.toString()

    fun decrypt(chunks: List<String>): AccountInput {
        require(chunks.isNotEmpty() && chunks.size <= 16)
        val plaintext = chunks.flatMap { encoded ->
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size == 256)
            Cipher.getInstance("RSA/ECB/PKCS1Padding").apply {
                init(Cipher.DECRYPT_MODE, keyPair.private)
            }.doFinal(encrypted).asIterable()
        }.toByteArray()
        try {
            val json = JSONObject(plaintext.toString(Charsets.UTF_8))
            return AccountInput(
                displayName = json.optString("displayName", "Living Room TV"),
                serverUrl = json.getString("serverUrl"),
                username = json.getString("username"),
                password = json.getString("password"),
            )
        } finally {
            plaintext.fill(0)
        }
    }

    companion object {
        fun create(): PairingCrypto {
            val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            return PairingCrypto(generator.generateKeyPair())
        }
    }
}

private fun ByteArray.dropLeadingZero(): ByteArray = if (size > 1 && first() == 0.toByte()) copyOfRange(1, size) else this
