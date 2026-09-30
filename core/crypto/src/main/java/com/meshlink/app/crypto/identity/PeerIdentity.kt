package com.meshlink.app.crypto.identity

import com.meshlink.app.domain.model.VerificationStatus
import java.security.MessageDigest

/**
 * Cryptographic identity model for a MeshLink peer.
 *
 * The public key is the single cryptographic source of truth.
 * [deviceId] is deterministically derived as the first 16 hex characters of SHA-256(publicKey).
 * Arbitrary senderId strings from untrusted packet JSON must NOT be accepted unless they
 * match this deterministic hash.
 */
data class PeerIdentity(
    val deviceId: String,
    val publicKey: ByteArray,
    val displayName: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
) {
    /**
     * User-readable identity fingerprint (e.g., 7A:3F:91:2B:...).
     */
    val fingerprint: String
        get() = computeFingerprint(publicKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerIdentity) return false
        return deviceId == other.deviceId && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = 31 * deviceId.hashCode() + publicKey.contentHashCode()

    companion object {
        /**
         * Deterministically derives the 16-character hex deviceId from [publicKeyBytes].
         */
        fun computeDeviceId(publicKeyBytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(publicKeyBytes)
                .joinToString("") { "%02x".format(it) }
                .take(16)

        /**
         * Formats SHA-256(publicKeyBytes) as a colon-separated hex fingerprint.
         */
        fun computeFingerprint(publicKeyBytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(publicKeyBytes)
                .joinToString(":") { "%02X".format(it) }

        /**
         * Creates a [PeerIdentity] from raw public key bytes and metadata,
         * verifying that deviceId is cryptographically bound to the public key.
         */
        fun fromPublicKey(
            publicKeyBytes: ByteArray,
            displayName: String = "",
            verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
            firstSeen: Long = System.currentTimeMillis(),
            lastSeen: Long = System.currentTimeMillis()
        ): PeerIdentity {
            val deviceId = computeDeviceId(publicKeyBytes)
            return PeerIdentity(
                deviceId = deviceId,
                publicKey = publicKeyBytes,
                displayName = displayName,
                verificationStatus = verificationStatus,
                firstSeen = firstSeen,
                lastSeen = lastSeen
            )
        }
    }
}
