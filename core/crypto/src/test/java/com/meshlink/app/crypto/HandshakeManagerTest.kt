package com.meshlink.app.crypto

import com.meshlink.app.crypto.identity.KeyManager
import com.meshlink.app.crypto.session.HandshakeManager
import com.meshlink.app.crypto.session.HandshakeMessage
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.MeshPacket
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Security tests for [HandshakeManager] mutual authenticated ECDH handshake.
 */
class HandshakeManagerTest {

    private lateinit var localKeyManager: KeyManager
    private lateinit var sessionKeyStore: SessionKeyStore
    private lateinit var handshakeManager: HandshakeManager

    private lateinit var deviceAKp: KeyPair
    private lateinit var deviceBKp: KeyPair
    private lateinit var deviceADeviceId: String
    private lateinit var deviceBDeviceId: String

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        deviceAKp = kpg.generateKeyPair()
        deviceBKp = kpg.generateKeyPair()

        deviceADeviceId = KeyManager.computeDeviceId(deviceAKp.public.encoded)
        deviceBDeviceId = KeyManager.computeDeviceId(deviceBKp.public.encoded)

        localKeyManager = mockk(relaxed = true)
        every { localKeyManager.keyPair } returns deviceAKp
        every { localKeyManager.publicKeyBytes } returns deviceAKp.public.encoded
        every { localKeyManager.deviceId } returns deviceADeviceId

        every { localKeyManager.sign(any()) } answers {
            val data = firstArg<ByteArray>()
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initSign(deviceAKp.private)
            sig.update(data)
            sig.sign()
        }

        every { localKeyManager.verifySignature(any(), any(), any()) } answers {
            val pubKeyBytes = firstArg<ByteArray>()
            val data = secondArg<ByteArray>()
            val sigBytes = thirdArg<ByteArray>()
            try {
                val kf = KeyFactory.getInstance("EC")
                val pubKey = kf.generatePublic(X509EncodedKeySpec(pubKeyBytes))
                val sig = Signature.getInstance("SHA256withECDSA")
                sig.initVerify(pubKey)
                sig.update(data)
                sig.verify(sigBytes)
            } catch (e: Exception) {
                false
            }
        }

        sessionKeyStore = SessionKeyStore()
        handshakeManager = HandshakeManager(localKeyManager, sessionKeyStore)
    }

    private fun signAsDeviceB(data: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(deviceBKp.private)
        sig.update(data)
        return sig.sign()
    }

    // ── 1. Valid handshake succeeds ──────────────────────────────────────────

    @Test
    fun `valid handshake completes and establishes session key`() {
        val endpointId = "endpoint-B"

        // Device A creates initial handshake packet for Device B (contains local challenge & ephemeral key)
        val initPacketFromA = handshakeManager.createHandshakePacket(deviceADeviceId, endpointId)
        val initMsgFromA = HandshakeMessage.fromJsonOrNull(initPacketFromA.content) as HandshakeMessage.Init

        // Simulate Device B generating its own ephemeral key and challenge
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val deviceBEphemeral = kpg.generateKeyPair()
        val deviceBChallenge = ByteArray(32) { (it + 5).toByte() }

        // Device B sends its Init to Device A
        val initMsgFromB = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = deviceBDeviceId,
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = deviceBEphemeral.public.encoded,
            challenge          = deviceBChallenge,
            timestamp          = System.currentTimeMillis()
        )
        val initPacketFromB = MeshPacket(
            senderId   = deviceBDeviceId,
            receiverId = endpointId,
            content    = initMsgFromB.toJson(),
            timestamp  = System.currentTimeMillis(),
            type       = MeshPacket.PacketType.HANDSHAKE
        )

        // Device A processes Init from B -> returns HandshakeResult.Send (containing A's AUTH)
        val result1 = handshakeManager.processHandshakePacket(endpointId, initPacketFromB, deviceADeviceId)
        assertTrue("Device A must send its Auth packet", result1 is HandshakeManager.HandshakeResult.Send)

        // Device B builds transcript over A's challenge and signs with Device B's identity key
        val bTranscript = HandshakeMessage.buildTranscript(
            protocolVersion       = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId        = deviceBDeviceId,
            signerIdentityPubKey  = deviceBKp.public.encoded,
            signerEphemeralPubKey = deviceBEphemeral.public.encoded,
            peerChallengeNonce    = initMsgFromA.challenge,
            peerDeviceId          = deviceADeviceId
        )
        val bSignature = signAsDeviceB(bTranscript)

        val authMsgFromB = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = deviceBDeviceId,
            signature       = bSignature,
            timestamp       = System.currentTimeMillis()
        )
        val authPacketFromB = MeshPacket(
            senderId   = deviceBDeviceId,
            receiverId = endpointId,
            content    = authMsgFromB.toJson(),
            timestamp  = System.currentTimeMillis(),
            type       = MeshPacket.PacketType.HANDSHAKE
        )

        // Device A processes Auth from B -> completes handshake!
        val result2 = handshakeManager.processHandshakePacket(endpointId, authPacketFromB, deviceADeviceId)
        assertTrue("Handshake must complete successfully", result2 is HandshakeManager.HandshakeResult.Complete)

        val complete = result2 as HandshakeManager.HandshakeResult.Complete
        assertEquals(deviceBDeviceId, complete.peerDeviceId)
        assertTrue(sessionKeyStore.isHandshakeComplete(endpointId))
        assertNotNull(sessionKeyStore.getSessionKey(endpointId))
        assertEquals(deviceBDeviceId, sessionKeyStore.getAuthenticatedPeerDeviceId(endpointId))
    }

    // ── 2. Invalid ECDSA signature fails ─────────────────────────────────────

    @Test
    fun `handshake fails when peer sends invalid ECDSA signature in Auth`() {
        val endpointId = "endpoint-B"
        handshakeManager.createHandshakePacket(deviceADeviceId, endpointId)

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val deviceBEphemeral = kpg.generateKeyPair()

        val initMsgFromB = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = deviceBDeviceId,
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = deviceBEphemeral.public.encoded,
            challenge          = ByteArray(32) { it.toByte() },
            timestamp          = System.currentTimeMillis()
        )
        handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, initMsgFromB.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        // Send corrupted signature
        val corruptedSig = ByteArray(72) { 0xFF.toByte() }
        val authMsgFromB = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = deviceBDeviceId,
            signature       = corruptedSig,
            timestamp       = System.currentTimeMillis()
        )

        val result = handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, authMsgFromB.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        assertEquals(HandshakeManager.HandshakeResult.Failed, result)
        assertFalse(sessionKeyStore.isHandshakeComplete(endpointId))
    }

    // ── 3. Modified transcript fails ──────────────────────────────────────────

    @Test
    fun `handshake fails when signature covers modified transcript`() {
        val endpointId = "endpoint-B"
        val initPacketA = handshakeManager.createHandshakePacket(deviceADeviceId, endpointId)
        val initMsgA = HandshakeMessage.fromJsonOrNull(initPacketA.content) as HandshakeMessage.Init

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val deviceBEphemeral = kpg.generateKeyPair()

        val initMsgFromB = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = deviceBDeviceId,
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = deviceBEphemeral.public.encoded,
            challenge          = ByteArray(32) { it.toByte() },
            timestamp          = System.currentTimeMillis()
        )
        handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, initMsgFromB.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        // Sign over altered peerDeviceId in transcript (MITM or bug)
        val tamperedTranscript = HandshakeMessage.buildTranscript(
            protocolVersion       = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId        = deviceBDeviceId,
            signerIdentityPubKey  = deviceBKp.public.encoded,
            signerEphemeralPubKey = deviceBEphemeral.public.encoded,
            peerChallengeNonce    = initMsgA.challenge,
            peerDeviceId          = "different-device-id" // Tampered!
        )
        val sig = signAsDeviceB(tamperedTranscript)

        val authMsgFromB = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = deviceBDeviceId,
            signature       = sig,
            timestamp       = System.currentTimeMillis()
        )

        val result = handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, authMsgFromB.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        assertEquals(HandshakeManager.HandshakeResult.Failed, result)
    }

    // ── 4. Modified ephemeral public key fails ────────────────────────────────

    @Test
    fun `handshake fails when signature covers different ephemeral key than announced`() {
        val endpointId = "endpoint-B"
        val initPacketA = handshakeManager.createHandshakePacket(deviceADeviceId, endpointId)
        val initMsgA = HandshakeMessage.fromJsonOrNull(initPacketA.content) as HandshakeMessage.Init

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val deviceBEphemeral1 = kpg.generateKeyPair()
        val deviceBEphemeral2 = kpg.generateKeyPair()

        // Init announces deviceBEphemeral1
        val initMsgFromB = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = deviceBDeviceId,
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = deviceBEphemeral1.public.encoded,
            challenge          = ByteArray(32) { it.toByte() },
            timestamp          = System.currentTimeMillis()
        )
        handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, initMsgFromB.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        // Auth transcript signs with deviceBEphemeral2
        val transcriptWithDifferentEphemeral = HandshakeMessage.buildTranscript(
            protocolVersion       = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId        = deviceBDeviceId,
            signerIdentityPubKey  = deviceBKp.public.encoded,
            signerEphemeralPubKey = deviceBEphemeral2.public.encoded, // mismatch!
            peerChallengeNonce    = initMsgA.challenge,
            peerDeviceId          = deviceADeviceId
        )
        val sig = signAsDeviceB(transcriptWithDifferentEphemeral)

        val authMsg = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = deviceBDeviceId,
            signature       = sig,
            timestamp       = System.currentTimeMillis()
        )

        val result = handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, authMsg.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        assertEquals(HandshakeManager.HandshakeResult.Failed, result)
    }

    // ── 5. Wrong device ID / public key binding fails ─────────────────────────

    @Test
    fun `handshake fails when claimed deviceId does not match identity public key hash`() {
        val endpointId = "endpoint-B"
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val ephemeral = kpg.generateKeyPair()

        // Spoofed deviceId not matching deviceBKp public key hash
        val initMsgWithSpoofedId = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = "spoofed-device-id", // claimed != SHA256(identityKey)
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = ephemeral.public.encoded,
            challenge          = ByteArray(32) { it.toByte() },
            timestamp          = System.currentTimeMillis()
        )

        val result = handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket("spoofed-device-id", endpointId, initMsgWithSpoofedId.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        assertEquals(HandshakeManager.HandshakeResult.Failed, result)
    }

    // ── 6. Replayed / invalid challenge fails ─────────────────────────────────

    @Test
    fun `handshake fails when peer signs with wrong or replayed challenge nonce`() {
        val endpointId = "endpoint-B"
        handshakeManager.createHandshakePacket(deviceADeviceId, endpointId)

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val deviceBEphemeral = kpg.generateKeyPair()

        val initMsgFromB = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = deviceBDeviceId,
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = deviceBEphemeral.public.encoded,
            challenge          = ByteArray(32) { it.toByte() },
            timestamp          = System.currentTimeMillis()
        )
        handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, initMsgFromB.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        // Peer signs with an arbitrary / old challenge nonce instead of localChallenge
        val wrongChallenge = ByteArray(32) { 0xAA.toByte() }
        val transcript = HandshakeMessage.buildTranscript(
            protocolVersion       = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId        = deviceBDeviceId,
            signerIdentityPubKey  = deviceBKp.public.encoded,
            signerEphemeralPubKey = deviceBEphemeral.public.encoded,
            peerChallengeNonce    = wrongChallenge, // wrong challenge
            peerDeviceId          = deviceADeviceId
        )
        val sig = signAsDeviceB(transcript)

        val authMsg = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = deviceBDeviceId,
            signature       = sig,
            timestamp       = System.currentTimeMillis()
        )

        val result = handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, authMsg.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        assertEquals(HandshakeManager.HandshakeResult.Failed, result)
    }

    // ── 7. Timestamp clock skew failure ──────────────────────────────────────

    @Test
    fun `handshake fails when Init timestamp has excessive clock skew`() {
        val endpointId = "endpoint-B"
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val ephemeral = kpg.generateKeyPair()

        // Timestamp 2 hours in the future
        val excessiveFutureTimestamp = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        val initMsgSkewed = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = deviceBDeviceId,
            identityPublicKey  = deviceBKp.public.encoded,
            ephemeralPublicKey = ephemeral.public.encoded,
            challenge          = ByteArray(32) { it.toByte() },
            timestamp          = excessiveFutureTimestamp
        )

        val result = handshakeManager.processHandshakePacket(
            endpointId,
            MeshPacket(deviceBDeviceId, endpointId, initMsgSkewed.toJson(), System.currentTimeMillis(), MeshPacket.PacketType.HANDSHAKE),
            deviceADeviceId
        )

        assertEquals(HandshakeManager.HandshakeResult.Failed, result)
    }
}
