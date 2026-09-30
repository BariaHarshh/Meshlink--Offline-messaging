package com.meshlink.app.domain.model

import java.security.MessageDigest

/**
 * Domain representation of a known MeshLink peer device.
 *
 * @property deviceId First 16 hex characters of SHA-256(publicKey).
 * @property displayName Human-readable display name.
 * @property publicKey X.509 DER encoded EC P-256 public identity key.
 * @property lastSeen Timestamp (epoch millis) when peer was last active.
 * @property firstSeen Timestamp (epoch millis) when peer was first discovered.
 * @property verificationStatus Trust status (UNKNOWN, UNVERIFIED, VERIFIED, REVOKED).
 */
data class KnownDevice(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val lastSeen: Long,
    val firstSeen: Long = lastSeen,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED
) {
    /**
     * Stable fingerprint formatted as hex bytes separated by colons (e.g. 7A:3F:91:...).
     * Derived from SHA-256 of the public identity key.
     */
    val fingerprint: String
        get() = formatFingerprint(publicKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnownDevice) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()

    companion object {
        /**
         * Formats SHA-256 of [publicKey] into user-readable fingerprint string:
         * "7A:3F:91:2B:..."
         */
        fun formatFingerprint(publicKey: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
            return digest.joinToString(":") { "%02X".format(it) }
        }
    }
}
