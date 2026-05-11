package com.meshlink.app.crypto.cipher

import android.util.Base64
import com.meshlink.app.crypto.identity.KeyManager
import timber.log.Timber
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Elliptic Curve Integrated Encryption Scheme (ECIES) for multi-hop E2E messaging.
 *
 * Used when Device A wants to send to Device C without a direct connection.
 * Relay nodes (Device B) forward the opaque ciphertext without being able to read it.
 *
 * Wire format:
 *   ephemeralPubKey[91]  — X.509-encoded EC P-256 ephemeral public key
 *   nonce[12]            — random GCM nonce (from EncryptionService.encrypt)
 *   ciphertext[n]        — AES-256-GCM encrypted plaintext
 *   gcmTag[16]           — GCM authentication tag (appended by EncryptionService)
 *
 *   Total overhead per message: 91 + 12 + 16 = 119 bytes.
 *
 * Security properties:
 *   • Forward secrecy: ephemeral key is discarded after each encryption
 *   • Relay opacity: intermediate nodes see only the opaque wire bytes
 *   • Integrity: GCM tag ensures tamper detection
 *   • Replay resistance: [SessionKeyStore.isNewMessage] handles dedup at the router level
 *
 * Key derivation:
 *   sharedSecret = ECDH(ephemeralPrivate, recipientPublic)
 *   sessionKey   = HKDF-SHA256(sharedSecret, salt="meshlink-ecies", info="ecies-v1", 32 bytes)
 */
@Singleton
class EciesService @Inject constructor(
    private val keyManager: KeyManager,
    private val encryptionService: EncryptionService
) {
    companion object {
        private const val EC_ALGORITHM   = "EC"
        private const val CURVE          = "secp256r1"
        private const val ECDH_ALGORITHM = "ECDH"
        private const val HMAC_SHA256    = "HmacSHA256"

        // X.509 DER length for a P-256 public key — fixed, used for wire format parsing
        private const val PUB_KEY_BYTES  = 91
    }

    /**
     * Encrypts [plaintext] for [recipientPublicKeyBytes] using ECIES.
     *
     * @param plaintext          Raw bytes to encrypt.
     * @param recipientPublicKeyBytes  X.509-encoded EC P-256 public key of the recipient.
     * @return Wire bytes: ephemeralPubKey[91] + nonce[12] + ciphertext + tag[16].
     * @throws java.security.GeneralSecurityException if the recipient key is malformed.
     */
    fun encrypt(plaintext: ByteArray, recipientPublicKeyBytes: ByteArray): ByteArray {
        // 1. Generate a fresh ephemeral EC key pair — discarded after this call
        val kpg = KeyPairGenerator.getInstance(EC_ALGORITHM)
        kpg.initialize(ECGenParameterSpec(CURVE))
        val ephemeralKeyPair = kpg.generateKeyPair()

        // 2. ECDH: ephemeralPrivate × recipientPublic → 32-byte shared secret
        val kf = KeyFactory.getInstance(EC_ALGORITHM)
        val recipientKey = kf.generatePublic(X509EncodedKeySpec(recipientPublicKeyBytes))

        val ka = javax.crypto.KeyAgreement.getInstance(ECDH_ALGORITHM)
        ka.init(ephemeralKeyPair.private)
        ka.doPhase(recipientKey, true)
        val sharedSecret = ka.generateSecret()

        // 3. HKDF-SHA256 → 32-byte AES-256 session key
        val sessionKeyBytes = hkdf(
            inputKeyMaterial = sharedSecret,
            salt             = "meshlink-ecies".toByteArray(Charsets.UTF_8),
            info             = "ecies-v1".toByteArray(Charsets.UTF_8),
            length           = 32
        )
        sharedSecret.fill(0)  // zero ephemeral secret immediately

        val sessionKey = SecretKeySpec(sessionKeyBytes, "AES")

        // 4. AES-256-GCM encrypt — returns nonce[12] + ciphertext + tag[16]
        val encryptedPart = encryptionService.encrypt(plaintext, sessionKey)

        // 5. Wire = ephemeralPubKey[91] || encryptedPart
        val ephemeralPubKeyBytes = ephemeralKeyPair.public.encoded  // 91 bytes for P-256
        return ephemeralPubKeyBytes + encryptedPart
    }

    /**
     * Decrypts ECIES wire bytes using [keyManager]'s private key.
     *
     * @param wireBytes  Wire format: ephemeralPubKey[91] + nonce[12] + ciphertext + tag[16].
     * @return Decrypted plaintext, or null if authentication fails (tampered/wrong key).
     */
    fun decrypt(wireBytes: ByteArray): ByteArray? {
        if (wireBytes.size <= PUB_KEY_BYTES + 12 + 16) {
            Timber.w("EciesService.decrypt: wire bytes too short (${wireBytes.size})")
            return null
        }

        return try {
            // 1. Extract ephemeral public key
            val ephemeralPubKeyBytes = wireBytes.copyOfRange(0, PUB_KEY_BYTES)
            val encryptedPart        = wireBytes.copyOfRange(PUB_KEY_BYTES, wireBytes.size)

            // 2. ECDH: myPrivate × ephemeralPublic → 32-byte shared secret
            val kf = KeyFactory.getInstance(EC_ALGORITHM)
            val ephemeralPubKey = kf.generatePublic(X509EncodedKeySpec(ephemeralPubKeyBytes))

            val ka = javax.crypto.KeyAgreement.getInstance(ECDH_ALGORITHM)
            ka.init(keyManager.keyPair.private)
            ka.doPhase(ephemeralPubKey, true)
            val sharedSecret = ka.generateSecret()

            // 3. HKDF-SHA256 → same 32-byte AES key
            val sessionKeyBytes = hkdf(
                inputKeyMaterial = sharedSecret,
                salt             = "meshlink-ecies".toByteArray(Charsets.UTF_8),
                info             = "ecies-v1".toByteArray(Charsets.UTF_8),
                length           = 32
            )
            sharedSecret.fill(0)

            val sessionKey = SecretKeySpec(sessionKeyBytes, "AES")

            // 4. AES-256-GCM decrypt — returns null on auth failure (tampered or wrong key)
            encryptionService.decrypt(encryptedPart, sessionKey)
        } catch (e: Exception) {
            Timber.e(e, "EciesService.decrypt failed")
            null
        }
    }

    /**
     * Convenience: encrypt for a peer whose public key is stored in the [KnownDevice] table.
     * Returns Base64-encoded wire bytes ready to put into [MeshPacket.content].
     */
    fun encryptToBase64(plaintext: ByteArray, recipientPublicKeyBytes: ByteArray): String =
        Base64.encodeToString(encrypt(plaintext, recipientPublicKeyBytes), Base64.NO_WRAP)

    /**
     * Convenience: decrypt from Base64-encoded [MeshPacket.content].
     */
    fun decryptFromBase64(base64Content: String): ByteArray? =
        decrypt(Base64.decode(base64Content, Base64.NO_WRAP))

    // ── HKDF-SHA256 (RFC 5869) — same implementation as HandshakeManager ─────

    private fun hkdf(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        // Extract
        val prk = hmacSha256(salt, inputKeyMaterial)

        // Expand
        val output    = ByteArray(length)
        var prev      = ByteArray(0)
        var filled    = 0
        var counter   = 1
        while (filled < length) {
            val block = hmacSha256(prk, prev + info + byteArrayOf(counter.toByte()))
            val toCopy = minOf(block.size, length - filled)
            block.copyInto(output, filled, 0, toCopy)
            filled += toCopy
            prev = block
            counter++
        }
        return output
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }
}
