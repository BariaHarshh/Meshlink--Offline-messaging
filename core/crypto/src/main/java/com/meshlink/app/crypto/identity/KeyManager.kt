package com.meshlink.app.crypto.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the device's EC P-256 identity key pair.
 *
 * • Private key: stored encrypted in EncryptedSharedPreferences, where the
 *   wrapping master key lives inside Android Keystore (AES-256-GCM).
 *   The raw private-key bytes never touch disk unencrypted.
 * • Public key: stored alongside the private key (also encrypted at rest).
 * • deviceId: first 16 hex chars of SHA-256(publicKey) — stable across
 *   reinstalls as long as the EncryptedSharedPreferences file survives.
 * • Digital signatures: ECDSA using SHA256withECDSA for authenticating handshakes.
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE     = "meshlink_identity_v1"
        private const val KEY_PRIVATE    = "ec_private_key"
        private const val KEY_PUBLIC     = "ec_public_key"
        private const val EC_ALGORITHM   = "EC"
        private const val CURVE          = "secp256r1"          // P-256
        private const val ECDH_ALGORITHM = "ECDH"
        private const val SIG_ALGORITHM  = "SHA256withECDSA"

        /**
         * Deterministically derives the 16-character hex deviceId from [publicKeyBytes].
         */
        fun computeDeviceId(publicKeyBytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(publicKeyBytes)
                .joinToString("") { "%02x".format(it) }
                .take(16)

        /**
         * Formats SHA-256(publicKeyBytes) as a colon-separated hex fingerprint (e.g. 7A:3F:91:...).
         */
        fun computeFingerprint(publicKeyBytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(publicKeyBytes)
                .joinToString(":") { "%02X".format(it) }
    }

    private val encryptedPrefs: SharedPreferences by lazy { createEncryptedPrefs() }

    // Cached in-process — never written to disk again once loaded
    @Volatile
    private var cachedKeyPair: KeyPair? = null

    // ── Public surface ───────────────────────────────────────────────────────

    val keyPair: KeyPair
        get() = cachedKeyPair ?: loadOrGenerate().also { cachedKeyPair = it }

    val publicKeyBytes: ByteArray
        get() = keyPair.public.encoded   // X.509 / SubjectPublicKeyInfo format

    /**
     * Stable device identifier: first 8 bytes (16 hex chars) of SHA-256(publicKey).
     * Replaces ANDROID_ID for cryptographic authenticity.
     */
    val deviceId: String
        get() = computeDeviceId(publicKeyBytes)

    /**
     * Stable user-readable identity fingerprint derived from public key.
     */
    val fingerprint: String
        get() = computeFingerprint(publicKeyBytes)

    /**
     * Signs [data] using the local private identity key with SHA256withECDSA.
     * @return DER-encoded ECDSA signature bytes.
     */
    fun sign(data: ByteArray): ByteArray {
        val signature = Signature.getInstance(SIG_ALGORITHM)
        signature.initSign(keyPair.private)
        signature.update(data)
        return signature.sign()
    }

    /**
     * Verifies that [signatureBytes] is a valid SHA256withECDSA signature over [data]
     * produced by the private key corresponding to [publicKeyBytes].
     * Fails closed: returns false on invalid key, bad format, or verification failure.
     */
    fun verifySignature(publicKeyBytes: ByteArray, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val signature = Signature.getInstance(SIG_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Timber.w(e, "verifySignature: verification failed or invalid signature/key")
            false
        }
    }

    /**
     * Performs ECDH with [peerPublicKeyBytes] using the local private key.
     * Returns the raw shared secret (32 bytes for P-256).
     * Throws [java.security.GeneralSecurityException] on invalid peer key.
     */
    fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM)
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadOrGenerate(): KeyPair {
        val storedPriv = encryptedPrefs.getString(KEY_PRIVATE, null)
        val storedPub  = encryptedPrefs.getString(KEY_PUBLIC,  null)

        return if (storedPriv != null && storedPub != null) {
            try {
                val kf = KeyFactory.getInstance(EC_ALGORITHM)
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(storedPriv, Base64.NO_WRAP)))
                val pub  = kf.generatePublic(X509EncodedKeySpec(Base64.decode(storedPub, Base64.NO_WRAP)))
                Timber.d("KeyManager: loaded existing identity key")
                KeyPair(pub, priv)
            } catch (e: Exception) {
                Timber.e(e, "KeyManager: failed to load stored keys, generating fresh pair")
                generateAndStore()
            }
        } else {
            generateAndStore()
        }
    }

    private fun generateAndStore(): KeyPair {
        val generator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        generator.initialize(ECGenParameterSpec(CURVE))
        val kp = generator.generateKeyPair()

        encryptedPrefs.edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUBLIC,  Base64.encodeToString(kp.public.encoded,  Base64.NO_WRAP))
            .apply()

        Timber.d("KeyManager: generated new identity key")
        return kp
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Some devices have a broken Keystore; wipe the corrupted file and retry once
            Timber.e(e, "KeyManager: EncryptedSharedPreferences failed, clearing and retrying")
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
