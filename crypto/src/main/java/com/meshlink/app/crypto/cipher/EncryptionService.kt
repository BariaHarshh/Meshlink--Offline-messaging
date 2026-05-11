package com.meshlink.app.crypto.cipher

import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless AES-256-GCM encrypt / decrypt service.
 *
 * Wire format (all bytes concatenated):
 *   ┌──────────────┬───────────────────────┬──────────────┐
 *   │  nonce[12]   │  ciphertext[variable] │  GCM-tag[16] │
 *   └──────────────┴───────────────────────┴──────────────┘
 *
 * The nonce is generated fresh (SecureRandom) for every message.
 * The GCM authentication tag is appended by the JCE cipher automatically.
 */
@Singleton
class EncryptionService @Inject constructor() {

    companion object {
        private const val ALGORITHM     = "AES/GCM/NoPadding"
        private const val NONCE_BYTES   = 12
        private const val TAG_BITS      = 128        // 16 bytes
    }

    private val secureRandom = SecureRandom()

    /**
     * Encrypts [plaintext] using [key].
     * @return nonce (12 B) + ciphertext + GCM tag (16 B)
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)     // includes GCM tag
        return nonce + ciphertext
    }

    /**
     * Decrypts [encrypted] (nonce + ciphertext + tag) using [key].
     * @return plaintext bytes, or null if the message is tampered / invalid.
     */
    fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray? {
        if (encrypted.size <= NONCE_BYTES) {
            Timber.w("decrypt: payload too short (${encrypted.size} bytes)")
            return null
        }
        return try {
            val nonce      = encrypted.copyOfRange(0, NONCE_BYTES)
            val ciphertext = encrypted.copyOfRange(NONCE_BYTES, encrypted.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            // Authentication failed — message was tampered or key is wrong
            Timber.w("decrypt: GCM authentication FAILED — message rejected (possible tampering)")
            null
        } catch (e: Exception) {
            Timber.e(e, "decrypt: unexpected error")
            null
        }
    }
}
