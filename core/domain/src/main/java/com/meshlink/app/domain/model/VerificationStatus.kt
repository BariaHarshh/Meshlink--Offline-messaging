package com.meshlink.app.domain.model

/**
 * Trust and verification state for a MeshLink peer.
 *
 * UNKNOWN    - Peer discovered or encountered in routing, but no direct handshake yet.
 * UNVERIFIED - Handshake completed successfully, identity key verified via ECDSA,
 *              but user has not yet manually confirmed the identity fingerprint.
 * VERIFIED   - User has manually verified peer fingerprint or previously trusted.
 * REVOKED    - Peer was explicitly marked as revoked or untrusted by the user.
 */
enum class VerificationStatus {
    UNKNOWN,
    UNVERIFIED,
    VERIFIED,
    REVOKED
}
