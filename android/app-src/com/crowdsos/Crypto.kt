package com.crowdsos

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM. Relay phones only see the routing header; this payload stays unreadable to them.
 *  DEMO ONLY: shared key. In the real product use the emergency service's public key. */
object Crypto {
    private const val HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val key = SecretKeySpec(HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), "AES")

    fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP) // iv | ciphertext | tag
    }
}
