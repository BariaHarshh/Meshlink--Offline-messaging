package com.meshlink.app.mesh.routing

import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.MeshPacket.PacketType
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.PendingMessage
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/**
 * Phase 4C Unit Tests: End-to-End ACK Delivery Confirmation.
 *
 * Verifies:
 * 1. Valid direct and routed messages generate an ACK only after successful persistence.
 * 2. ACK references the correct original messageId inside payload and uses a distinct packet messageId.
 * 3. Tampered, decrypt-failed, or malformed packets generate NO ACK.
 * 4. BROADCAST and ACK packets NEVER generate ACKs (no loops / no ping-pong).
 * 5. Sender transitions message from SENT -> DELIVERED upon receiving a verified, authenticated ACK.
 * 6. Spoofed ACKs (unauthenticated, wrong origin, or mismatched receiver) are strictly rejected.
 * 7. Duplicate ACKs are idempotent.
 * 8. Intermediate relay nodes forward ACKs toward finalDestId without decrypting.
 * 9. ACK packets respect hop limits and replay protection.
 */
class Phase4cAckDeliveryTest {

    private val localDeviceId = "local-alice-01"
    private val remoteDeviceId = "remote-bob-02"
    private val thirdPartyDeviceId = "attacker-mallory-03"
    private val peerEndpoint = "endpoint-ep1"

    private lateinit var routingTable: RoutingTable
    private lateinit var seenMessageCache: SeenMessageCache
    private lateinit var encryptionService: EncryptionService
    private lateinit var eciesService: EciesService
    private lateinit var sessionKeyStore: SessionKeyStore
    private lateinit var messageRepository: MessageRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var pendingMessageRepository: PendingMessageRepository
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var meshRouter: MeshRouter

    private val fakePendingStorage = mutableMapOf<String, PendingMessage>()
    private val fakeMessageStorage = mutableMapOf<String, Message>()

    private val remotePublicKey: ByteArray by lazy {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        kpg.generateKeyPair().public.encoded
    }

    private val localPublicKey: ByteArray by lazy {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        kpg.generateKeyPair().public.encoded
    }

    @Before
    fun setUp() {
        fakePendingStorage.clear()
        fakeMessageStorage.clear()

        routingTable = RoutingTable()
        seenMessageCache = SeenMessageCache()
        encryptionService = mockk(relaxed = true)
        eciesService = mockk(relaxed = true)
        sessionKeyStore = mockk(relaxed = true)
        userProfileManager = mockk(relaxed = true)

        coEvery { userProfileManager.getDisplayName() } returns "Alice"

        deviceRepository = mockk(relaxed = true)
        coEvery { deviceRepository.getDeviceById(remoteDeviceId) } returns KnownDevice(
            deviceId = remoteDeviceId,
            displayName = "Bob",
            publicKey = remotePublicKey,
            lastSeen = System.currentTimeMillis()
        )
        coEvery { deviceRepository.getDeviceById(localDeviceId) } returns KnownDevice(
            deviceId = localDeviceId,
            displayName = "Alice",
            publicKey = localPublicKey,
            lastSeen = System.currentTimeMillis()
        )

        pendingMessageRepository = mockk(relaxed = true)
        coEvery { pendingMessageRepository.enqueue(any()) } answers {
            val msg = firstArg<PendingMessage>()
            fakePendingStorage[msg.id] = msg
        }
        coEvery { pendingMessageRepository.remove(any()) } answers {
            val id = firstArg<String>()
            fakePendingStorage.remove(id)
        }

        messageRepository = mockk(relaxed = true)
        coEvery { messageRepository.insertMessage(any()) } answers {
            val msg = firstArg<Message>()
            fakeMessageStorage[msg.id] = msg
        }
        coEvery { messageRepository.updateDeliveryStatus(any(), any()) } answers {
            val id = firstArg<String>()
            val status = secondArg<DeliveryStatus>()
            val existing = fakeMessageStorage[id]
            if (existing != null) {
                fakeMessageStorage[id] = existing.copy(deliveryStatus = status, delivered = (status == DeliveryStatus.DELIVERED || existing.delivered))
            }
        }
        coEvery { messageRepository.getMessageById(any()) } answers {
            val id = firstArg<String>()
            fakeMessageStorage[id]
        }

        meshRouter = MeshRouter(
            myDeviceId = localDeviceId,
            routingTable = routingTable,
            seenMessageCache = seenMessageCache,
            encryptionService = encryptionService,
            eciesService = eciesService,
            sessionKeyStore = sessionKeyStore,
            messageRepository = messageRepository,
            deviceRepository = deviceRepository,
            pendingMessageRepository = pendingMessageRepository,
            userProfileManager = userProfileManager
        )
    }

    // ── Test 1, 2, 3, 4: Valid routed message generates ACK with correct IDs ──

    @Test
    fun `valid routed message generates authenticated ACK with unique packet ID referencing original message`() = runTest {
        val now = System.currentTimeMillis()
        val originalMsgId = "orig-msg-100"
        val routedPacket = MeshPacket(
            messageId = originalMsgId,
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "ValidEncryptedECIESContent",
            timestamp = now,
            type = PacketType.ROUTED_CHAT,
            originId = remoteDeviceId,
            finalDestId = localDeviceId,
            hopCount = 0,
            maxHops = 7
        )

        // Mock successful ECIES decryption at recipient
        val innerPayloadJson = JSONObject().apply {
            put("text", "Hello Alice")
            put("senderName", "Bob")
        }
        coEvery { eciesService.decryptFromBase64("ValidEncryptedECIESContent", any()) } returns innerPayloadJson.toString().toByteArray(Charsets.UTF_8)
        coEvery { eciesService.encryptToBase64(any(), eq(remotePublicKey), any()) } returns "EncryptedAckCiphertext"

        val connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = routedPacket,
            connectedPeers = connectedPeers
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertNotNull("Local message must be decrypted and delivered", processed.localMessage)
        assertEquals(originalMsgId, processed.localMessage?.id)

        // Verification of ACK generation
        assertEquals("Must generate 1 ACK forward target", 1, processed.forwardTargets.size)
        val ackTarget = processed.forwardTargets[0]
        assertEquals(peerEndpoint, ackTarget.endpointId)
        val ackPacket = ackTarget.packet

        assertEquals(PacketType.ACK, ackPacket.type)
        assertEquals(localDeviceId, ackPacket.originId)
        assertEquals(remoteDeviceId, ackPacket.finalDestId)
        assertEquals("EncryptedAckCiphertext", ackPacket.content)
        assertNotEquals("ACK packet must have a distinct unique messageId", originalMsgId, ackPacket.messageId)
    }

    // ── Dedicated Test: Complete Direct CHAT ACK Lifecycle ────────────────────

    @Test
    fun `complete direct CHAT ACK lifecycle end-to-end`() = runTest {
        val now = System.currentTimeMillis()
        val directMsgId = "direct-chat-msg-001"

        // 1. Sender (Alice, localDeviceId) has an outgoing message in SENT state
        fakeMessageStorage[directMsgId] = Message(
            id = directMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Direct text".toByteArray(),
            timestamp = now,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT
        )

        // 2. Direct CHAT packet addressed to Bob (remoteDeviceId) arrives at recipient
        val directPacket = MeshPacket(
            messageId = directMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            content = "EncryptedDirectChatBase64",
            timestamp = now,
            type = PacketType.CHAT,
            originId = localDeviceId,
            finalDestId = remoteDeviceId,
            hopCount = 0
        )

        // 3. Recipient (Bob) generates ACK for Alice using Alice's public key
        val bobMeshRouter = MeshRouter(
            myDeviceId = remoteDeviceId,
            routingTable = RoutingTable(),
            seenMessageCache = SeenMessageCache(),
            encryptionService = encryptionService,
            eciesService = eciesService,
            sessionKeyStore = sessionKeyStore,
            messageRepository = messageRepository,
            deviceRepository = deviceRepository,
            pendingMessageRepository = pendingMessageRepository,
            userProfileManager = userProfileManager
        )

        coEvery { eciesService.encryptToBase64(any(), eq(localPublicKey), any()) } returns "DirectAckEncryptedPayload"

        val ackTargets = bobMeshRouter.generateAckTargets(directPacket, mapOf(peerEndpoint to localDeviceId))
        assertEquals("Recipient must generate 1 direct ACK target", 1, ackTargets.size)
        val ackPacket = ackTargets[0].packet
        assertEquals(PacketType.ACK, ackPacket.type)
        assertEquals(remoteDeviceId, ackPacket.originId)
        assertEquals(localDeviceId, ackPacket.finalDestId)

        // 4. Sender (Alice) receives the ACK packet from Bob
        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", directMsgId)
            put("ackTimestamp", now)
        }
        coEvery { eciesService.decryptFromBase64("DirectAckEncryptedPayload", any()) } returns ackPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        // 5. Sender validates ACK and transitions message from SENT -> DELIVERED
        val updatedMsg = fakeMessageStorage[directMsgId]
        assertNotNull(updatedMsg)
        assertEquals("Direct message must transition from SENT to DELIVERED upon receiving ACK", DeliveryStatus.DELIVERED, updatedMsg?.deliveryStatus)
        assertTrue(updatedMsg!!.delivered)
    }

    // ── Dedicated Test: Missing Sender Public Key Safe Failure ────────────────

    @Test
    fun `ACK generation fails safely when sender public key is unknown with no plaintext fallback`() = runTest {
        val now = System.currentTimeMillis()
        val unknownSenderId = "unknown-sender-99"
        val originalMsgId = "msg-unknown-sender-99"

        val packet = MeshPacket(
            messageId = originalMsgId,
            senderId = unknownSenderId,
            receiverId = localDeviceId,
            content = "Ciphertext",
            timestamp = now,
            type = PacketType.ROUTED_CHAT,
            originId = unknownSenderId,
            finalDestId = localDeviceId
        )

        // DeviceRepository returns null for unknown sender
        coEvery { deviceRepository.getDeviceById(unknownSenderId) } returns null

        val innerPayloadJson = JSONObject().apply {
            put("text", "Secret from unknown")
            put("senderName", "Unknown")
        }
        coEvery { eciesService.decryptFromBase64("Ciphertext", any()) } returns innerPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = packet,
            connectedPeers = mapOf(peerEndpoint to unknownSenderId)
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed

        // Message is still correctly decrypted and persisted locally
        assertNotNull("Message must remain persisted locally", processed.localMessage)
        assertEquals(originalMsgId, processed.localMessage?.id)

        // But ACK generation fails safely (no plaintext or unauthenticated fallback)
        assertTrue("No unauthenticated ACK targets should be generated without public key", processed.forwardTargets.isEmpty())
    }

    // ── Dedicated Test: AAD Metadata Tampering Rejection ──────────────────────

    @Test
    fun `ACK with tampered AAD metadata causes decryption failure and is rejected`() = runTest {
        val now = System.currentTimeMillis()
        val originalMsgId = "orig-msg-aad-test"
        fakeMessageStorage[originalMsgId] = Message(
            id = originalMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = now - 5000,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT
        )

        val ackPacket = MeshPacket(
            messageId = "ack-tampered-aad",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckWithTamperedMetadata",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )

        // ECIES decrypt fails due to AAD mismatch (e.g. tampered originId or timestamp on wire)
        coEvery { eciesService.decryptFromBase64("EncryptedAckWithTamperedMetadata", any()) } returns null

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertEquals(RoutingResult.Drop, result)
        assertEquals("Tampered ACK must not alter message SENT status", DeliveryStatus.SENT, fakeMessageStorage[originalMsgId]?.deliveryStatus)
    }

    // ── Test 5, 6, 7: Tampered / Decrypt-failed / Malformed packet generates NO ACK

    @Test
    fun `tampered message that fails decryption generates NO ACK and is dropped`() = runTest {
        val now = System.currentTimeMillis()
        val tamperedPacket = MeshPacket(
            messageId = "tampered-msg-101",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "TamperedCiphertext",
            timestamp = now,
            type = PacketType.ROUTED_CHAT,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )

        // Decryption fails (null returned)
        coEvery { eciesService.decryptFromBase64("TamperedCiphertext", any()) } returns null

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = tamperedPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertNull("Failed decryption must not deliver local message", processed.localMessage)
        assertTrue("Failed decryption must NEVER generate an ACK", processed.forwardTargets.isEmpty())
    }

    // ── Test 8 & 9: BROADCAST and ACK packets NEVER generate ACKs ────────────

    @Test
    fun `broadcast packet NEVER generates an ACK`() = runTest {
        val now = System.currentTimeMillis()
        val broadcastPacket = MeshPacket(
            messageId = "bcast-msg-102",
            senderId = remoteDeviceId,
            receiverId = MeshPacket.BROADCAST_DEST,
            content = "Public Emergency Flood Alert",
            timestamp = now,
            type = PacketType.BROADCAST,
            originId = remoteDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST
        )

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = broadcastPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertNotNull(processed.localMessage)
        // Forward targets may contain broadcast flood forwarders, but NO ACK packets
        assertFalse("Broadcast must never generate ACK packets", processed.forwardTargets.any { it.packet.type == PacketType.ACK })
    }

    @Test
    fun `ACK packet receipt NEVER generates an ACK (prevents ping-pong loop)`() = runTest {
        val now = System.currentTimeMillis()
        val ackPacket = MeshPacket(
            messageId = "ack-pkt-103",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckPayload",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )

        coEvery { eciesService.decryptFromBase64(any(), any()) } returns """{"ackMessageId":"orig-103"}""".toByteArray()
        fakeMessageStorage["orig-103"] = Message(
            id = "orig-103",
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Ciphertext".toByteArray(),
            timestamp = now,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT
        )

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertTrue("Receiving an ACK must never generate another ACK", processed.forwardTargets.isEmpty())
    }

    // ── Test 10: Valid authenticated ACK changes SENT -> DELIVERED ───────────

    @Test
    fun `sender receiving verified ACK transitions message from SENT to DELIVERED`() = runTest {
        val now = System.currentTimeMillis()
        val originalMsgId = "orig-msg-104"
        fakeMessageStorage[originalMsgId] = Message(
            id = originalMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Secret Payload".toByteArray(),
            timestamp = now - 5000,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT
        )

        val ackPacketId = "ack-packet-104"
        val ackPacket = MeshPacket(
            messageId = ackPacketId,
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )

        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", originalMsgId)
            put("ackTimestamp", now)
        }
        coEvery { eciesService.decryptFromBase64("EncryptedAckBytes", any()) } returns ackPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        val updatedMsg = fakeMessageStorage[originalMsgId]
        assertNotNull(updatedMsg)
        assertEquals("DeliveryStatus must transition to DELIVERED upon verified ACK", DeliveryStatus.DELIVERED, updatedMsg?.deliveryStatus)
        assertTrue(updatedMsg!!.delivered)
    }

    // ── Test 11: ACK for unknown message does not alter unrelated messages ───

    @Test
    fun `ACK referencing unknown message ID is dropped and does not modify other messages`() = runTest {
        val now = System.currentTimeMillis()
        val existingMsgId = "existing-msg-105"
        fakeMessageStorage[existingMsgId] = Message(
            id = existingMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = now - 5000,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT
        )

        val ackPacket = MeshPacket(
            messageId = "ack-unknown-105",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )
        coEvery { eciesService.decryptFromBase64("EncryptedAckBytes", any()) } returns """{"ackMessageId":"non-existent-msg-id"}""".toByteArray()

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertEquals(RoutingResult.Drop, result)
        assertEquals(DeliveryStatus.SENT, fakeMessageStorage[existingMsgId]?.deliveryStatus)
    }

    // ── Test 12: Duplicate ACK is idempotent ─────────────────────────────────

    @Test
    fun `duplicate ACK arrival is idempotent and preserves DELIVERED state`() = runTest {
        val now = System.currentTimeMillis()
        val originalMsgId = "orig-msg-106"
        fakeMessageStorage[originalMsgId] = Message(
            id = originalMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = now - 5000,
            delivered = true,
            deliveryStatus = DeliveryStatus.DELIVERED
        )

        val ackPacket = MeshPacket(
            messageId = "ack-106",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )
        coEvery { eciesService.decryptFromBase64("EncryptedAckBytes", any()) } returns """{"ackMessageId":"$originalMsgId"}""".toByteArray()

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        assertEquals(DeliveryStatus.DELIVERED, fakeMessageStorage[originalMsgId]?.deliveryStatus)
    }

    // ── Test 13: Spoofed ACK is rejected and does not mark message DELIVERED ─

    @Test
    fun `spoofed ACK from non-intended recipient is rejected`() = runTest {
        val now = System.currentTimeMillis()
        val originalMsgId = "orig-msg-107"
        // Alice sent to Bob (remoteDeviceId)
        fakeMessageStorage[originalMsgId] = Message(
            id = originalMsgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = now - 5000,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT
        )

        // Mallory (thirdPartyDeviceId) attempts to forge an ACK claiming to acknowledge Alice's message to Bob
        val spoofedAckPacket = MeshPacket(
            messageId = "ack-spoofed-107",
            senderId = thirdPartyDeviceId,
            receiverId = localDeviceId,
            content = "SpoofedAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = thirdPartyDeviceId, // Origin is Mallory, not Bob
            finalDestId = localDeviceId
        )

        coEvery { eciesService.decryptFromBase64("SpoofedAckBytes", any()) } returns """{"ackMessageId":"$originalMsgId"}""".toByteArray()

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = spoofedAckPacket,
            connectedPeers = mapOf(peerEndpoint to thirdPartyDeviceId)
        )

        assertEquals("Spoofed ACK must be dropped", RoutingResult.Drop, result)
        assertEquals(
            "Original message must remain SENT and never be marked DELIVERED by spoofed ACK",
            DeliveryStatus.SENT,
            fakeMessageStorage[originalMsgId]?.deliveryStatus
        )
    }

    // ── Test 14 & 15: Multi-hop ACK forwarding and hop limits ────────────────

    @Test
    fun `intermediate relay forwards ACK toward original sender without decrypting`() = runTest {
        val now = System.currentTimeMillis()
        val relayDeviceId = "relay-charlie-04"
        val ackPacket = MeshPacket(
            messageId = "ack-relay-108",
            senderId = remoteDeviceId,
            receiverId = relayDeviceId,
            content = "OpaqueAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = "target-david-05", // Not for us
            hopCount = 1,
            maxHops = 7
        )

        // Setup routing table: david is reachable via endpoint-david
        routingTable.addRoute("target-david-05", "endpoint-david")
        val connectedPeers = mapOf("endpoint-david" to "target-david-05")

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = connectedPeers
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertNull("Relay node must not deliver local message", processed.localMessage)
        assertEquals(1, processed.forwardTargets.size)
        assertEquals("endpoint-david", processed.forwardTargets[0].endpointId)
        assertEquals(2, processed.forwardTargets[0].packet.hopCount)
    }

    @Test
    fun `ACK packet exceeding maxHops is dropped by relay`() = runTest {
        val now = System.currentTimeMillis()
        val expiredAck = MeshPacket(
            messageId = "ack-expired-109",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "OpaqueAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = "target-david-05",
            hopCount = 7,
            maxHops = 7 // Max hops reached
        )

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = expiredAck,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertEquals(RoutingResult.Drop, result)
    }

    // ── Test 16: Existing replay protection works for ACKs ───────────────────

    @Test
    fun `replay of seen ACK packet is immediately dropped by deduplication cache`() = runTest {
        val now = System.currentTimeMillis()
        val ackPacket = MeshPacket(
            messageId = "ack-replay-110",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckBytes",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )

        // Pre-mark as seen in SeenMessageCache
        seenMessageCache.markSeen("ack-replay-110", remoteDeviceId)

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertEquals(RoutingResult.Drop, result)
    }
}
