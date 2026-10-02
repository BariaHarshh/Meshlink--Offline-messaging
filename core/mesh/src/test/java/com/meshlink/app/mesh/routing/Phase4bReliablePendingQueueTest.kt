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
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Phase 4B Unit Tests: Reliable Pending Message Queue.
 *
 * Verifies:
 * 1. A pending message remains in the repository when candidate is flushed / transmission fails.
 * 2. A pending message is removed from the repository ONLY after successful transmission.
 * 3. Failed transmission or re-enqueueing does NOT reset or extend expiresAt.
 * 4. Successful transmission transitions message status to SENT.
 * 5. Phase 4B never sets status to DELIVERED on transmission success.
 * 6. Repeated queue flushes do not delete or lose pending messages.
 * 7. Duplicate queue insertion uses messageId for idempotency and duplicate prevention.
 * 8. Expired pending messages (expiresAt <= now) are dropped on flush.
 */
class Phase4bReliablePendingQueueTest {

    private val myDeviceId = "local-device-01"
    private val destDeviceId = "dest-device-02"
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

    // In-memory fake storage for pending messages and messages to verify real lifecycle
    private val fakePendingStorage = mutableMapOf<String, PendingMessage>()
    private val fakeMessageStorage = mutableMapOf<String, Message>()

    private val destPublicKey: ByteArray by lazy {
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
        coEvery { eciesService.encryptToBase64(any(), any(), any()) } returns "fake-ecies-ciphertext"

        deviceRepository = mockk(relaxed = true)
        coEvery { deviceRepository.getDeviceById(destDeviceId) } returns KnownDevice(
            deviceId = destDeviceId,
            displayName = "Bob",
            publicKey = destPublicKey,
            lastSeen = System.currentTimeMillis()
        )

        pendingMessageRepository = mockk(relaxed = true)
        coEvery { pendingMessageRepository.enqueue(any()) } answers {
            val msg = firstArg<PendingMessage>()
            fakePendingStorage[msg.id] = msg
        }
        coEvery { pendingMessageRepository.getAllPending() } answers {
            fakePendingStorage.values.toList()
        }
        coEvery { pendingMessageRepository.getPendingFor(any()) } answers {
            val target = firstArg<String>()
            fakePendingStorage.values.filter { it.targetDeviceId == target }
        }
        coEvery { pendingMessageRepository.remove(any()) } answers {
            val id = firstArg<String>()
            fakePendingStorage.remove(id)
        }
        coEvery { pendingMessageRepository.deleteExpired(any()) } answers {
            val threshold = firstArg<Long>()
            fakePendingStorage.entries.removeIf { it.value.expiresAt <= threshold }
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
                fakeMessageStorage[id] = existing.copy(deliveryStatus = status)
            }
        }
        coEvery { messageRepository.getMessageById(any()) } answers {
            val id = firstArg<String>()
            fakeMessageStorage[id]
        }

        meshRouter = MeshRouter(
            myDeviceId = myDeviceId,
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

    // ── Test 1 & 6: Pending message remains in DB across flushes until success ─

    @Test
    fun `flushPendingQueue does NOT delete pending message from repository`() = runTest {
        val now = System.currentTimeMillis()
        val packet = MeshPacket(
            messageId = "msg-101",
            senderId = myDeviceId,
            receiverId = destDeviceId,
            content = "Ciphertext",
            timestamp = now,
            type = PacketType.ROUTED_CHAT,
            originId = myDeviceId,
            finalDestId = destDeviceId
        )
        val pending = PendingMessage(
            id = packet.messageId,
            packetJson = """{"messageId":"${packet.messageId}","senderId":"$myDeviceId","receiverId":"$destDeviceId","content":"Ciphertext","timestamp":$now,"type":"ROUTED_CHAT","originId":"$myDeviceId","finalDestId":"$destDeviceId","hopCount":0,"maxHops":7,"routeHistory":[]}""",
            targetDeviceId = destDeviceId,
            enqueuedAt = now,
            expiresAt = now + 48 * 3600 * 1000L
        )
        fakePendingStorage[pending.id] = pending

        // Connected peers has a direct route to destDeviceId
        val connectedPeers = mapOf(peerEndpoint to destDeviceId)

        // First flush
        val targets1 = meshRouter.flushPendingQueue(connectedPeers)
        assertEquals(1, targets1.size)
        assertEquals(peerEndpoint, targets1[0].endpointId)

        // Pending message MUST STILL be in repository (not prematurely deleted)
        assertTrue(
            "Pending message must remain in queue after flush until transmission succeeds",
            fakePendingStorage.containsKey("msg-101")
        )

        // Second flush (e.g. repeated connection event)
        val targets2 = meshRouter.flushPendingQueue(connectedPeers)
        assertEquals(1, targets2.size)
        assertTrue(
            "Repeated flush must not lose the pending message",
            fakePendingStorage.containsKey("msg-101")
        )
    }

    // ── Test 2 & 4: Successful transmission removes from queue and sets SENT ──

    @Test
    fun `onSendSuccess removes pending message from queue and transitions status to SENT`() = runTest {
        val now = System.currentTimeMillis()
        val messageId = "msg-102"
        val message = Message(
            id = messageId,
            senderId = myDeviceId,
            receiverId = destDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = now,
            delivered = false,
            deliveryStatus = DeliveryStatus.QUEUED
        )
        fakeMessageStorage[messageId] = message
        val pending = PendingMessage(
            id = messageId,
            packetJson = """{"messageId":"$messageId"}""",
            targetDeviceId = destDeviceId,
            enqueuedAt = now,
            expiresAt = now + 48 * 3600 * 1000L
        )
        fakePendingStorage[messageId] = pending

        // Confirm send success
        meshRouter.onSendSuccess(messageId, myDeviceId)

        // Must be removed from pending storage
        assertFalse(
            "Pending message must be deleted from queue on transmission success",
            fakePendingStorage.containsKey(messageId)
        )

        // DeliveryStatus must be SENT
        assertEquals(
            DeliveryStatus.SENT,
            fakeMessageStorage[messageId]?.deliveryStatus
        )
    }

    // ── Test 5: Phase 4B never sets DELIVERED ────────────────────────────────

    @Test
    fun `Phase 4B onSendSuccess NEVER transitions status to DELIVERED`() = runTest {
        val now = System.currentTimeMillis()
        val messageId = "msg-103"
        val message = Message(
            id = messageId,
            senderId = myDeviceId,
            receiverId = destDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = now,
            delivered = false,
            deliveryStatus = DeliveryStatus.QUEUED
        )
        fakeMessageStorage[messageId] = message

        meshRouter.onSendSuccess(messageId, myDeviceId)

        assertNotEquals(
            "Phase 4B must not set DELIVERED — only Phase 4C ACK may do so",
            DeliveryStatus.DELIVERED,
            fakeMessageStorage[messageId]?.deliveryStatus
        )
        assertEquals(DeliveryStatus.SENT, fakeMessageStorage[messageId]?.deliveryStatus)
    }

    // ── Test 3: Failed transmission preserves original expiresAt ──────────────

    @Test
    fun `onSendFailure preserves original expiresAt and does not extend lifetime`() = runTest {
        val t0 = 1000000L
        val originalExpiresAt = t0 + 48 * 3600 * 1000L
        val packet = MeshPacket(
            messageId = "msg-104",
            senderId = myDeviceId,
            receiverId = destDeviceId,
            content = "Ciphertext",
            timestamp = t0,
            type = PacketType.ROUTED_CHAT,
            originId = myDeviceId,
            finalDestId = destDeviceId
        )
        val message = Message(
            id = packet.messageId,
            senderId = myDeviceId,
            receiverId = destDeviceId,
            ciphertext = "Secret".toByteArray(),
            timestamp = t0,
            delivered = false,
            deliveryStatus = DeliveryStatus.PENDING
        )
        fakeMessageStorage[packet.messageId] = message

        // Transmit fails 10 hours later
        meshRouter.onSendFailure(packet)

        val queuedEntry = fakePendingStorage[packet.messageId]
        assertNotNull("Failed transmission must retain/enqueue the message in Room", queuedEntry)
        assertEquals(
            "expiresAt must remain exactly original packet timestamp + 48h",
            originalExpiresAt,
            queuedEntry!!.expiresAt
        )
        assertEquals(
            DeliveryStatus.QUEUED,
            fakeMessageStorage[packet.messageId]?.deliveryStatus
        )
    }

    // ── Test 7: Duplicate queue insertion prevention / Idempotency ────────────

    @Test
    fun `enqueuePending uses messageId as primary key to prevent duplicate entries`() = runTest {
        val now = System.currentTimeMillis()

        // Calling buildAndRoute when offline queues the message
        val result1 = meshRouter.buildAndRoute(
            finalDestDeviceId = destDeviceId,
            plaintext = "Hello World",
            connectedPeers = emptyMap(),
            timestamp = now
        )
        assertTrue(result1 is RoutingResult.Queued)
        val msgId = (result1 as RoutingResult.Queued).messageId

        assertEquals(1, fakePendingStorage.size)
        assertTrue(fakePendingStorage.containsKey(msgId))
        assertEquals(DeliveryStatus.QUEUED, fakeMessageStorage[msgId]?.deliveryStatus)

        // If onSendFailure or re-queue occurs for the same packet
        val packet = MeshPacket(
            messageId = msgId,
            senderId = myDeviceId,
            receiverId = destDeviceId,
            content = "Ciphertext",
            timestamp = now,
            type = PacketType.ROUTED_CHAT,
            originId = myDeviceId,
            finalDestId = destDeviceId
        )
        meshRouter.onSendFailure(packet)

        // Still exactly 1 entry in queue for this messageId
        assertEquals(1, fakePendingStorage.size)
        assertEquals(msgId, fakePendingStorage[msgId]?.id)
    }

    // ── Test 8: Expired pending messages are dropped ──────────────────────────

    @Test
    fun `flushPendingQueue drops expired pending messages`() = runTest {
        val now = System.currentTimeMillis()
        val expiredPending = PendingMessage(
            id = "msg-expired",
            packetJson = """{"messageId":"msg-expired"}""",
            targetDeviceId = destDeviceId,
            enqueuedAt = now - 50 * 3600 * 1000L,
            expiresAt = now - 2 * 3600 * 1000L // expired 2h ago
        )
        val validPending = PendingMessage(
            id = "msg-valid",
            packetJson = """{"messageId":"msg-valid","senderId":"$myDeviceId","receiverId":"$destDeviceId","content":"Valid","timestamp":$now,"type":"ROUTED_CHAT","originId":"$myDeviceId","finalDestId":"$destDeviceId","hopCount":0,"maxHops":7,"routeHistory":[]}""",
            targetDeviceId = destDeviceId,
            enqueuedAt = now,
            expiresAt = now + 48 * 3600 * 1000L
        )
        fakePendingStorage[expiredPending.id] = expiredPending
        fakePendingStorage[validPending.id] = validPending

        val connectedPeers = mapOf(peerEndpoint to destDeviceId)
        val targets = meshRouter.flushPendingQueue(connectedPeers)

        // Only the valid packet is returned
        assertEquals(1, targets.size)
        assertEquals("msg-valid", targets[0].packet.messageId)

        // Expired entry must have been removed
        assertFalse(fakePendingStorage.containsKey("msg-expired"))
        // Valid entry must remain until send success
        assertTrue(fakePendingStorage.containsKey("msg-valid"))
    }
}
