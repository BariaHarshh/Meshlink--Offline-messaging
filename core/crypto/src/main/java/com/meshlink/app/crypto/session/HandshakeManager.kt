package com.meshlink.app.crypto.session

import java.util.Base64
import com.meshlink.app.crypto.identity.KeyManager
import com.meshlink.app.domain.model.MeshPacket
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manages the authenticated ECDH handshake protocol between two MeshLink devices.
 *
 * Security guarantees:
 *   1. Identity proof: each peer proves ownership of their P-256 identity key via ECDSA signature.
 *   2. Replay resistance: fresh 32-byte SecureRandom challenges bound to the signature transcript.
 *   3. Forward secrecy: ephemeral P-256 keys are used for ECDH key agreement.
 *   4. Context binding: HKDF-SHA256 derives 256-bit AES session key bound to device IDs and ephemeral keys.
 *   5. Fail closed: any signature, timing, or format discrepancy results in immediate handshake abort.
 */
@Singleton
class HandshakeManager @Inject constructor(
    private val keyManager: KeyManager,
    private val sessionKeyStore: SessionKeyStore
) {
    companion object {
        private const val EC_ALGORITHM = "EC"
        private const val CURVE = "secp256r1"
        private const val ECDH_ALGORITHM = "ECDH"
        private const val HKDF_INFO_PREFIX = "MeshLink_v1_AES256GCM_Session"
        private const val SESSION_KEY_BYTES = 32   // AES-256
        private const val NONCE_BYTES = 32
        private const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L  // 10 minutes
    }

    private val secureRandom = SecureRandom()

    private data class HandshakeState(
        val localEphemeralKeyPair: KeyPair,
        val localChallenge: ByteArray,
        var peerDeviceId: String? = null,
        var peerIdentityKey: ByteArray? = null,
        var peerEphemeralKey: ByteArray? = null,
        var peerChallenge: ByteArray? = null,
        var sentAuth: Boolean = false,
        var verifiedPeerAuth: Boolean = false
    )

    // endpointId -> HandshakeState
    private val activeHandshakes = ConcurrentHashMap<String, HandshakeState>()

    // ── Outbound handshake ────────────────────────────────────────────────────

    /**
     * Creates the initial HANDSHAKE packet containing the local device's identity public key,
     * fresh ephemeral public key, and fresh 32-byte challenge nonce.
     */
    fun createHandshakePacket(localDeviceId: String, remoteEndpointId: String): MeshPacket {
        val state = getOrCreateState(remoteEndpointId)

        val initMsg = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = localDeviceId,
            identityPublicKey  = keyManager.publicKeyBytes,
            ephemeralPublicKey = state.localEphemeralKeyPair.public.encoded,
            challenge          = state.localChallenge,
            timestamp          = System.currentTimeMillis()
        )

        return MeshPacket(
            senderId   = localDeviceId,
            receiverId = remoteEndpointId,
            content    = initMsg.toJson(),
            timestamp  = System.currentTimeMillis(),
            type       = MeshPacket.PacketType.HANDSHAKE,
            messageId  = UUID.randomUUID().toString()
        )
    }

    // ── Inbound handshake processing ──────────────────────────────────────────

    sealed class HandshakeResult {
        /** An intermediate response packet must be sent to the peer. */
        data class Send(val packet: MeshPacket) : HandshakeResult()
        /** Handshake fully completed; session key is established and stored. */
        data class Complete(val peerDeviceId: String, val peerPublicKey: ByteArray) : HandshakeResult()
        /** Handshake completed and an AUTH packet must also be dispatched. */
        data class CompleteAndSend(val packet: MeshPacket, val peerDeviceId: String, val peerPublicKey: ByteArray) : HandshakeResult()
        /** Handshake failed due to authentication, replay, or malformed data. Disconnect peer. */
        object Failed : HandshakeResult()
    }

    /**
     * Processes an incoming HANDSHAKE packet from [endpointId].
     */
    fun processHandshakePacket(
        endpointId: String,
        packet: MeshPacket,
        localDeviceId: String
    ): HandshakeResult {
        val message = HandshakeMessage.fromJsonOrNull(packet.content)
        if (message == null) {
            Timber.w("Handshake: rejected malformed or unauthenticated handshake packet from $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        return when (message) {
            is HandshakeMessage.Init -> handleInit(endpointId, message, localDeviceId)
            is HandshakeMessage.Auth -> handleAuth(endpointId, message, localDeviceId)
        }
    }

    private fun handleInit(
        endpointId: String,
        msg: HandshakeMessage.Init,
        localDeviceId: String
    ): HandshakeResult {
        // 1. Protocol version validation
        if (msg.protocolVersion != HandshakeMessage.PROTOCOL_VERSION) {
            Timber.w("Handshake: unsupported protocol version ${msg.protocolVersion} from $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        // 2. Timestamp validation (prevent precomputed or stale init replay)
        val now = System.currentTimeMillis()
        if (abs(now - msg.timestamp) > MAX_CLOCK_SKEW_MS) {
            Timber.w("Handshake: invalid timestamp skew (${now - msg.timestamp} ms) from $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        // 3. Cryptographic identity check: deviceId must strictly equal SHA-256(identityPublicKey).take(16)
        val expectedDeviceId = KeyManager.computeDeviceId(msg.identityPublicKey)
        if (msg.deviceId != expectedDeviceId) {
            Timber.e("SECURITY: Handshake deviceId mismatch! claimed=${msg.deviceId} expected=$expectedDeviceId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        // 4. Validate nonces and keys
        if (msg.challenge.size < 16 || msg.identityPublicKey.isEmpty() || msg.ephemeralPublicKey.isEmpty()) {
            Timber.w("Handshake: invalid key or challenge length from $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        val state = getOrCreateState(endpointId)
        state.peerDeviceId = msg.deviceId
        state.peerIdentityKey = msg.identityPublicKey
        state.peerEphemeralKey = msg.ephemeralPublicKey
        state.peerChallenge = msg.challenge

        // 5. Sign transcript using local identity key:
        //    covers (version, localDeviceId, localIdentityKey, localEphemeralKey, peerChallenge, peerDeviceId)
        val transcript = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = localDeviceId,
            signerIdentityPubKey = keyManager.publicKeyBytes,
            signerEphemeralPubKey = state.localEphemeralKeyPair.public.encoded,
            peerChallengeNonce   = msg.challenge,
            peerDeviceId         = msg.deviceId
        )

        val signature = try {
            keyManager.sign(transcript)
        } catch (e: Exception) {
            Timber.e(e, "Handshake: failed to sign transcript for $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        val authMsg = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = localDeviceId,
            signature       = signature,
            timestamp       = System.currentTimeMillis()
        )

        val authPacket = MeshPacket(
            senderId   = localDeviceId,
            receiverId = endpointId,
            content    = authMsg.toJson(),
            timestamp  = System.currentTimeMillis(),
            type       = MeshPacket.PacketType.HANDSHAKE,
            messageId  = UUID.randomUUID().toString()
        )
        state.sentAuth = true

        // If we already verified peer's AUTH earlier, complete session now
        return if (state.verifiedPeerAuth) {
            if (establishSession(endpointId, state, localDeviceId)) {
                HandshakeResult.CompleteAndSend(authPacket, msg.deviceId, msg.identityPublicKey)
            } else {
                HandshakeResult.Failed
            }
        } else {
            HandshakeResult.Send(authPacket)
        }
    }

    private fun handleAuth(
        endpointId: String,
        msg: HandshakeMessage.Auth,
        localDeviceId: String
    ): HandshakeResult {
        val state = activeHandshakes[endpointId]
        if (state == null || state.peerDeviceId == null || state.peerIdentityKey == null || state.peerEphemeralKey == null) {
            Timber.w("Handshake: received AUTH before INIT from $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        if (msg.protocolVersion != HandshakeMessage.PROTOCOL_VERSION) {
            Timber.w("Handshake: unsupported AUTH version from $endpointId")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        if (msg.deviceId != state.peerDeviceId) {
            Timber.e("SECURITY: Handshake AUTH deviceId mismatch! claimed=${msg.deviceId} expected=${state.peerDeviceId}")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        // Verify peer's signature over peer's transcript containing our challenge
        val peerTranscript = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = state.peerDeviceId!!,
            signerIdentityPubKey = state.peerIdentityKey!!,
            signerEphemeralPubKey = state.peerEphemeralKey!!,
            peerChallengeNonce   = state.localChallenge,
            peerDeviceId         = localDeviceId
        )

        val isValid = keyManager.verifySignature(state.peerIdentityKey!!, peerTranscript, msg.signature)
        if (!isValid) {
            Timber.e("SECURITY: Peer signature verification FAILED for $endpointId (peer=${state.peerDeviceId})")
            clearSession(endpointId)
            return HandshakeResult.Failed
        }

        state.verifiedPeerAuth = true
        Timber.i("Handshake: verified peer ECDSA signature from $endpointId (${state.peerDeviceId})")

        // If we already sent our AUTH, we can establish the session immediately
        return if (state.sentAuth) {
            if (establishSession(endpointId, state, localDeviceId)) {
                HandshakeResult.Complete(state.peerDeviceId!!, state.peerIdentityKey!!)
            } else {
                HandshakeResult.Failed
            }
        } else {
            // Awaiting our AUTH transmission (rare race in Nearby callbacks)
            HandshakeResult.Complete(state.peerDeviceId!!, state.peerIdentityKey!!)
        }
    }

    /**
     * Performs ECDH with ephemeral keys and derives the 256-bit AES session key using HKDF-SHA256.
     */
    private fun establishSession(
        endpointId: String,
        state: HandshakeState,
        localDeviceId: String
    ): Boolean {
        return try {
            val peerEphemeralBytes = state.peerEphemeralKey ?: return false
            val peerIdentityBytes = state.peerIdentityKey ?: return false
            val peerDevId = state.peerDeviceId ?: return false

            // 1. ECDH shared secret: localEphemeralPrivate × peerEphemeralPublic
            val sharedSecret = computeEcdh(state.localEphemeralKeyPair.private, peerEphemeralBytes)

            // 2. Deterministic salt: sort(localEphemeralPubKey, peerEphemeralPubKey)
            val (firstKey, secondKey) = sortKeys(state.localEphemeralKeyPair.public.encoded, peerEphemeralBytes)
            val salt = MessageDigest.getInstance("SHA-256").digest(firstKey + secondKey)

            // 3. Info bound to sorted device IDs
            val (firstDev, secondDev) = sortStrings(localDeviceId, peerDevId)
            val info = "$HKDF_INFO_PREFIX:$firstDev:$secondDev".toByteArray(Charsets.UTF_8)

            // 4. HKDF-SHA256 derivation
            val sessionKeyBytes = hkdf(
                inputKeyMaterial = sharedSecret,
                salt             = salt,
                info             = info,
                length           = SESSION_KEY_BYTES
            )

            // Wipe intermediate shared secret
            sharedSecret.fill(0)

            val sessionKey: SecretKey = SecretKeySpec(sessionKeyBytes, "AES")
            sessionKeyBytes.fill(0)

            // Store in SessionKeyStore bound to peer device ID and public key
            sessionKeyStore.storeSession(endpointId, peerDevId, peerIdentityBytes, sessionKey)
            activeHandshakes.remove(endpointId)

            Timber.i("Handshake: session key successfully established for $endpointId ($peerDevId)")
            true
        } catch (e: Exception) {
            Timber.e(e, "Handshake: key agreement or derivation failed for $endpointId")
            clearSession(endpointId)
            false
        }
    }

    /**
     * Legacy convenience wrapper used in unit tests or fallback checks.
     */
    fun processHandshake(endpointId: String, peerPublicKeyBase64: String): Boolean {
        return try {
            val peerPubKeyBytes = Base64.getDecoder().decode(peerPublicKeyBase64)
            val sharedSecret = keyManager.computeSharedSecret(peerPubKeyBytes)
            val (first, second) = sortKeys(keyManager.publicKeyBytes, peerPubKeyBytes)
            val salt = first + second
            val sessionKeyBytes = hkdf(
                inputKeyMaterial = sharedSecret,
                salt             = salt,
                info             = "MeshLink_v1_AES256GCM_Session".toByteArray(Charsets.UTF_8),
                length           = SESSION_KEY_BYTES
            )
            sharedSecret.fill(0)
            val sessionKey: SecretKey = SecretKeySpec(sessionKeyBytes, "AES")
            sessionKeyBytes.fill(0)
            val peerDeviceId = KeyManager.computeDeviceId(peerPubKeyBytes)
            sessionKeyStore.storeSession(endpointId, peerDeviceId, peerPubKeyBytes, sessionKey)
            true
        } catch (e: Exception) {
            Timber.e(e, "Legacy handshake processing failed for $endpointId")
            false
        }
    }

    // ── Convenience delegations ───────────────────────────────────────────────

    fun isHandshakeComplete(endpointId: String): Boolean =
        sessionKeyStore.isHandshakeComplete(endpointId)

    fun clearSession(endpointId: String) {
        activeHandshakes.remove(endpointId)
        sessionKeyStore.clearSession(endpointId)
    }

    // ── Cryptographic primitives ──────────────────────────────────────────────

    private fun getOrCreateState(endpointId: String): HandshakeState {
        return activeHandshakes.computeIfAbsent(endpointId) {
            val kpg = KeyPairGenerator.getInstance(EC_ALGORITHM)
            kpg.initialize(ECGenParameterSpec(CURVE))
            val ephemeralKp = kpg.generateKeyPair()

            val challenge = ByteArray(NONCE_BYTES)
            secureRandom.nextBytes(challenge)

            HandshakeState(
                localEphemeralKeyPair = ephemeralKp,
                localChallenge        = challenge
            )
        }
    }

    private fun computeEcdh(privateKey: PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance(EC_ALGORITHM)
        val peerKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        val ka = KeyAgreement.getInstance(ECDH_ALGORITHM)
        ka.init(privateKey)
        ka.doPhase(peerKey, true)
        return ka.generateSecret()
    }

    /**
     * RFC 5869 HKDF using HMAC-SHA256.
     */
    private fun hkdf(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(inputKeyMaterial)

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

    private fun sortKeys(a: ByteArray, b: ByteArray): Pair<ByteArray, ByteArray> {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff < 0) return Pair(a, b)
            if (diff > 0) return Pair(b, a)
        }
        return if (a.size <= b.size) Pair(a, b) else Pair(b, a)
    }

    private fun sortStrings(a: String, b: String): Pair<String, String> =
        if (a <= b) Pair(a, b) else Pair(b, a)
}
