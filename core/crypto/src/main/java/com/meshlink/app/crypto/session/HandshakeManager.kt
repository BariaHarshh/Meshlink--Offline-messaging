package com.meshlink.app.crypto.session

import android.util.Base64
import com.meshlink.app.crypto.identity.KeyManager
import com.meshlink.app.domain.model.MeshPacket
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Manages the ECDH handshake protocol between two devices.
 *
 * Protocol (both sides run simultaneously after Nearby connection is established):
 *
 *   1. [createHandshakePacket] — wraps own public key in a HANDSHAKE MeshPacket.
 *   2. [processHandshake]      — on receiving peer's HANDSHAKE packet:
 *        a. Decode peer public key from packet.content
 *        b. ECDH → shared secret (32 bytes, P-256)
 *        c. HKDF-SHA256 → 32-byte AES-256 session key
 *           (salt = min(A,B) || max(A,B) so both sides derive the same key)
 *        d. Store in [SessionKeyStore] under the peer's endpointId
 *        e. Mark session as CONNECTED-ready
 *
 * Session keys are never persisted — see [SessionKeyStore].
 */
@Singleton
class HandshakeManager @Inject constructor(
    private val keyManager: KeyManager,
    private val sessionKeyStore: SessionKeyStore
) {
    companion object {
        private const val HKDF_INFO = "MeshLink_v1_AES256GCM_Session"
        private const val SESSION_KEY_BYTES = 32   // AES-256
    }

    // ── Outbound handshake ────────────────────────────────────────────────────

    /**
     * Creates the HANDSHAKE packet this device should send immediately after
     * a Nearby Connections session is established.
     */
    fun createHandshakePacket(localDeviceId: String, remoteEndpointId: String): MeshPacket =
        MeshPacket(
            senderId   = localDeviceId,
            receiverId = remoteEndpointId,
            content    = Base64.encodeToString(keyManager.publicKeyBytes, Base64.NO_WRAP),
            timestamp  = System.currentTimeMillis(),
            type       = MeshPacket.PacketType.HANDSHAKE,
            messageId  = UUID.randomUUID().toString()
        )

    // ── Inbound handshake ─────────────────────────────────────────────────────

    /**
     * Processes a HANDSHAKE packet received from [endpointId].
     * [peerPublicKeyBase64] is the Base64-encoded X.509 public key from the packet.
     *
     * @return true if the session key was successfully derived and stored.
     */
    fun processHandshake(endpointId: String, peerPublicKeyBase64: String): Boolean {
        return try {
            val peerPubKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)

            // 1. ECDH shared secret
            val sharedSecret = keyManager.computeSharedSecret(peerPubKeyBytes)

            // 2. HKDF-SHA256 — deterministic salt = smaller_key || larger_key
            //    so both A and B always compute the same HKDF output regardless
            //    of which side initiated.
            val (first, second) = sortKeys(keyManager.publicKeyBytes, peerPubKeyBytes)
            val salt = first + second

            val sessionKeyBytes = hkdf(
                inputKeyMaterial = sharedSecret,
                salt             = salt,
                info             = HKDF_INFO.toByteArray(Charsets.UTF_8),
                length           = SESSION_KEY_BYTES
            )

            // 3. Wipe intermediate secret from memory (best-effort on JVM)
            sharedSecret.fill(0)

            val sessionKey: SecretKey = SecretKeySpec(sessionKeyBytes, "AES")
            sessionKeyBytes.fill(0)   // wipe derived key bytes after wrapping

            sessionKeyStore.storeSessionKey(endpointId, sessionKey)
            Timber.i("Handshake complete for $endpointId — AES-256 session key ready")
            true
        } catch (e: Exception) {
            Timber.e(e, "Handshake failed for $endpointId")
            false
        }
    }

    // ── Convenience delegations ───────────────────────────────────────────────

    fun isHandshakeComplete(endpointId: String): Boolean =
        sessionKeyStore.isHandshakeComplete(endpointId)

    fun clearSession(endpointId: String) = sessionKeyStore.clearSession(endpointId)

    // ── HKDF-SHA256 ───────────────────────────────────────────────────────────

    /**
     * RFC 5869 HKDF using HMAC-SHA256.
     * No external library required — uses only javax.crypto.Mac.
     */
    private fun hkdf(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        // Extract: PRK = HMAC-SHA256(salt, IKM)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(inputKeyMaterial)

        // Expand: T(1) = HMAC-SHA256(PRK, "" || info || 0x01)
        //         T(2) = HMAC-SHA256(PRK, T(1) || info || 0x02)  …etc.
        val output = ByteArrayOutputStream()
        var prev = ByteArray(0)
        var counter = 1
        while (output.size() < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(prev)
            mac.update(info)
            mac.update(counter.toByte())
            prev = mac.doFinal()
            output.write(prev)
            counter++
        }
        return output.toByteArray().copyOfRange(0, length)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lexicographic sort: ensures HKDF salt is identical on both devices,
     * regardless of which side is "initiator" vs "responder".
     */
    private fun sortKeys(a: ByteArray, b: ByteArray): Pair<ByteArray, ByteArray> {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff < 0) return Pair(a, b)
            if (diff > 0) return Pair(b, a)
        }
        return if (a.size <= b.size) Pair(a, b) else Pair(b, a)
    }
}
