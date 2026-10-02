package com.meshlink.app.mesh.routing

import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.MeshPacket.PacketType
import com.meshlink.app.domain.model.PendingMessage
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import io.mockk.coEvery
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
import java.util.UUID

/**
 * Phase 4D: Bounded Retry & Recovery Comprehensive Test Suite.
 */
class Phase4dBoundedRetryTest {

    private val localDeviceId = "local-alice-node-001"
    private val remoteDeviceId = "remote-bob-node-002"
    private val peerEndpoint = "endpoint-bob-999"
    private val remotePublicKey = ByteArray(91) { 0x02.toByte() }

    private lateinit var routingTable: RoutingTable
    private lateinit var seenMessageCache: SeenMessageCache
    private lateinit var encryptionService: EncryptionService
    private lateinit var eciesService: EciesService
    private lateinit var sessionKeyStore: SessionKeyStore
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var pendingMessageRepository: PendingMessageRepository
    private lateinit var messageRepository: MessageRepository

    private lateinit var meshRouter: MeshRouter

    private val fakePendingStorage = mutableMapOf<String, PendingMessage>()
    private val fakeMessageStorage = mutableMapOf<String, Message>()

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

        pendingMessageRepository = mockk(relaxed = true)
        coEvery { pendingMessageRepository.enqueue(any()) } answers {
            val msg = firstArg<PendingMessage>()
            fakePendingStorage[msg.id] = msg
        }
        coEvery { pendingMessageRepository.remove(any()) } answers {
            val id = firstArg<String>()
            fakePendingStorage.remove(id)
        }
        coEvery { pendingMessageRepository.getAllPending() } answers {
            fakePendingStorage.values.toList()
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
                // If already DELIVERED, do not overwrite unless status is DELIVERED
                if (existing.deliveryStatus != DeliveryStatus.DELIVERED || status == DeliveryStatus.DELIVERED) {
                    fakeMessageStorage[id] = existing.copy(
                        deliveryStatus = status,
                        delivered = (status == DeliveryStatus.DELIVERED || existing.delivered)
                    )
                }
            }
        }
        coEvery { messageRepository.getMessageById(any()) } answers {
            val id = firstArg<String>()
            fakeMessageStorage[id]
        }
        coEvery { messageRepository.getEligibleRetries(eq(localDeviceId), any(), any()) } answers {
            val now = secondArg<Long>()
            val maxRetries = thirdArg<Int>()
            fakeMessageStorage.values.filter { msg ->
                msg.senderId == localDeviceId &&
                        (msg.deliveryStatus == DeliveryStatus.SENT || msg.deliveryStatus == DeliveryStatus.QUEUED) &&
                        msg.retryCount < maxRetries &&
                        msg.nextRetryAt <= now &&
                        msg.expiresAt > now
            }
        }
        coEvery { messageRepository.claimRetry(any(), any(), any(), any()) } answers {
            val id = firstArg<String>()
            val now = secondArg<Long>()
            val nextRetryAt = thirdArg<Long>()
            val maxRetries = arg<Int>(3)
            val msg = fakeMessageStorage[id]
            if (msg != null &&
                (msg.deliveryStatus == DeliveryStatus.SENT || msg.deliveryStatus == DeliveryStatus.QUEUED) &&
                msg.retryCount < maxRetries &&
                msg.nextRetryAt <= now &&
                msg.expiresAt > now
            ) {
                fakeMessageStorage[id] = msg.copy(
                    retryCount = msg.retryCount + 1,
                    nextRetryAt = nextRetryAt
                )
                true
            } else {
                false
            }
        }
        coEvery { messageRepository.markFailedIfExpiredOrExhausted(any(), any()) } answers {
            val now = firstArg<Long>()
            val maxRetries = secondArg<Int>()
            var count = 0
            fakeMessageStorage.values.forEach { msg ->
                if ((msg.deliveryStatus == DeliveryStatus.PENDING || msg.deliveryStatus == DeliveryStatus.QUEUED || msg.deliveryStatus == DeliveryStatus.SENT) &&
                    (msg.expiresAt <= now || msg.retryCount >= maxRetries)
                ) {
                    fakeMessageStorage[msg.id] = msg.copy(deliveryStatus = DeliveryStatus.FAILED)
                    count++
                }
            }
            count
        }
        coEvery { messageRepository.markFailed(any()) } answers {
            val id = firstArg<String>()
            val msg = fakeMessageStorage[id]
            if (msg != null && msg.deliveryStatus != DeliveryStatus.DELIVERED) {
                fakeMessageStorage[id] = msg.copy(deliveryStatus = DeliveryStatus.FAILED)
            }
        }
        coEvery { messageRepository.updateRetrySchedule(any(), any(), any()) } answers {
            val id = firstArg<String>()
            val retryCount = secondArg<Int>()
            val nextRetryAt = thirdArg<Long>()
            val msg = fakeMessageStorage[id]
            if (msg != null && msg.deliveryStatus != DeliveryStatus.DELIVERED) {
                fakeMessageStorage[id] = msg.copy(retryCount = retryCount, nextRetryAt = nextRetryAt)
            }
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

    // ── Tests 1, 3, 4: Backoff calculation & Jitter Bounds ───────────────────

    @Test
    fun `backoff calculation matches truncated exponential backoff with bounded jitter`() {
        for (i in 0..10) {
            val delay = meshRouter.calculateNextRetryDelayMs(i)
            val expectedBase = minOf(Message.BASE_RETRY_DELAY_MS * (1L shl i), Message.MAX_RETRY_DELAY_MS)
            assertTrue("Delay $delay must be >= expected base $expectedBase", delay >= expectedBase)
            assertTrue("Delay $delay must be <= base + 5000ms jitter", delay <= expectedBase + 5000L)
        }
    }

    // ── Tests 2, 6, 8, 9, 10, 11: SENT message retry preserves IDs, ciphertext, and metadata ──

    @Test
    fun `SENT message retry increments retryCount and preserves messageId, timestamp, ciphertext, and expiresAt`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "sent-msg-retry-001"
        val originalTimestamp = now - 60_000L
        val originalExpiresAt = originalTimestamp + Message.DEFAULT_TTL_MS
        val ciphertext = "Hello Bob through Mesh".toByteArray(Charsets.UTF_8)

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = ciphertext,
            timestamp = originalTimestamp,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 0,
            nextRetryAt = now - 1000L, // eligible
            expiresAt = originalExpiresAt
        )

        coEvery { eciesService.encryptToBase64(any(), eq(remotePublicKey), any()) } returns "RetriedEciesCiphertext"

        val connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        val retries = meshRouter.retryEligibleMessages(connectedPeers)

        assertEquals("Must generate 1 retry forward target", 1, retries.size)
        val target = retries[0]
        val packet = target.packet

        assertEquals(msgId, packet.messageId)
        assertEquals(localDeviceId, packet.originId)
        assertEquals(remoteDeviceId, packet.finalDestId)
        assertEquals(originalTimestamp, packet.timestamp)
        assertEquals("RetriedEciesCiphertext", packet.content)

        val updatedMsg = fakeMessageStorage[msgId]
        assertNotNull(updatedMsg)
        assertEquals("retryCount must increment to 1", 1, updatedMsg?.retryCount)
        assertEquals("expiresAt must remain unchanged", originalExpiresAt, updatedMsg?.expiresAt)
        assertTrue("nextRetryAt must be advanced into the future", updatedMsg!!.nextRetryAt > now)
        assertEquals(DeliveryStatus.SENT, updatedMsg.deliveryStatus)
    }

    // ── Tests 5, 13: Retry exhaustion reaches terminal FAILED ────────────────

    @Test
    fun `message reaching MAX_RETRY_COUNT transitions to FAILED and is never retried`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "exhausted-msg-002"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Exhausted".toByteArray(Charsets.UTF_8),
            timestamp = now - 3600_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = Message.MAX_RETRY_COUNT, // 5
            nextRetryAt = now - 1000L,
            expiresAt = now + Message.DEFAULT_TTL_MS
        )

        val connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        val retries = meshRouter.retryEligibleMessages(connectedPeers)

        assertTrue("Exhausted message must not produce forward targets", retries.isEmpty())
        val updatedMsg = fakeMessageStorage[msgId]
        assertEquals("Exhausted message must transition to FAILED", DeliveryStatus.FAILED, updatedMsg?.deliveryStatus)
    }

    // ── Tests 11, 12: Expired message transitions to FAILED without sending ─

    @Test
    fun `expired message immediately transitions to FAILED and is never transmitted`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "expired-msg-003"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Expired Content".toByteArray(Charsets.UTF_8),
            timestamp = now - 200_000_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.QUEUED,
            retryCount = 1,
            nextRetryAt = now - 1000L,
            expiresAt = now - 10_000L // Expired!
        )

        val connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        val retries = meshRouter.retryEligibleMessages(connectedPeers)

        assertTrue("Expired message must not produce forward targets", retries.isEmpty())
        val updatedMsg = fakeMessageStorage[msgId]
        assertEquals("Expired message must transition to FAILED", DeliveryStatus.FAILED, updatedMsg?.deliveryStatus)
    }

    // ── Tests 14, 15: DELIVERED and FAILED messages are never retried ─────────

    @Test
    fun `DELIVERED message is never retried even if nextRetryAt is in the past`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "delivered-msg-004"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Delivered text".toByteArray(Charsets.UTF_8),
            timestamp = now - 50_000L,
            delivered = true,
            deliveryStatus = DeliveryStatus.DELIVERED,
            retryCount = 0,
            nextRetryAt = now - 1000L,
            expiresAt = now + Message.DEFAULT_TTL_MS
        )

        val connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        val retries = meshRouter.retryEligibleMessages(connectedPeers)

        assertTrue("DELIVERED message must never produce retry targets", retries.isEmpty())
        val msg = fakeMessageStorage[msgId]
        assertEquals(DeliveryStatus.DELIVERED, msg?.deliveryStatus)
    }

    @Test
    fun `FAILED message is never retried or resurrected`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "failed-msg-005"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Failed text".toByteArray(Charsets.UTF_8),
            timestamp = now - 50_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.FAILED,
            retryCount = 5,
            nextRetryAt = now - 1000L,
            expiresAt = now + Message.DEFAULT_TTL_MS
        )

        val connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        val retries = meshRouter.retryEligibleMessages(connectedPeers)

        assertTrue("FAILED message must never produce retry targets", retries.isEmpty())
        val msg = fakeMessageStorage[msgId]
        assertEquals(DeliveryStatus.FAILED, msg?.deliveryStatus)
    }

    // ── Test 16: ACK arriving during retry transitions to DELIVERED and cancels retry ─

    @Test
    fun `ACK arrival transitions message to DELIVERED and prevents future retries`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "ack-race-msg-006"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Race Test".toByteArray(Charsets.UTF_8),
            timestamp = now - 20_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 1,
            nextRetryAt = now + 60_000L,
            expiresAt = now + Message.DEFAULT_TTL_MS
        )

        // Recipient ACK arrives
        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", msgId)
            put("ackTimestamp", now)
        }
        coEvery { eciesService.decryptFromBase64(any(), any()) } returns ackPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val ackPacket = MeshPacket(
            messageId = "ack-pkt-999",
            senderId = remoteDeviceId,
            receiverId = localDeviceId,
            content = "EncryptedAckPayload",
            timestamp = now,
            type = PacketType.ACK,
            originId = remoteDeviceId,
            finalDestId = localDeviceId
        )

        val result = meshRouter.route(
            fromEndpointId = peerEndpoint,
            packet = ackPacket,
            connectedPeers = mapOf(peerEndpoint to remoteDeviceId)
        )

        assertTrue(result is RoutingResult.Processed)
        val updatedMsg = fakeMessageStorage[msgId]
        assertNotNull(updatedMsg)
        assertEquals(DeliveryStatus.DELIVERED, updatedMsg?.deliveryStatus)
        assertTrue(updatedMsg!!.delivered)

        // Subsequent retry scan produces 0 targets
        val retries = meshRouter.retryEligibleMessages(mapOf(peerEndpoint to remoteDeviceId))
        assertTrue("No retries should be generated for DELIVERED message", retries.isEmpty())
    }

    // ── Test 17: Concurrent retry workers cannot double-claim ────────────────

    @Test
    fun `concurrent retry workers cannot both claim the same message`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "concurrency-msg-007"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Claim Test".toByteArray(Charsets.UTF_8),
            timestamp = now - 10_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 0,
            nextRetryAt = now - 1000L,
            expiresAt = now + Message.DEFAULT_TTL_MS
        )

        val nextRetryAt = now + 30_000L
        val claim1 = messageRepository.claimRetry(msgId, now, nextRetryAt, Message.MAX_RETRY_COUNT)
        assertTrue("First worker must successfully claim retry", claim1)

        val claim2 = messageRepository.claimRetry(msgId, now, nextRetryAt + 30_000L, Message.MAX_RETRY_COUNT)
        assertFalse("Second worker must fail to claim because nextRetryAt is now in the future", claim2)
    }

    // ── Test 7: QUEUED message retry behavior ────────────────────────────────

    @Test
    fun `QUEUED message retry retains as QUEUED when route is unavailable`() = runTest {
        val now = System.currentTimeMillis()
        val msgId = "queued-retry-msg-008"

        fakeMessageStorage[msgId] = Message(
            id = msgId,
            senderId = localDeviceId,
            receiverId = remoteDeviceId,
            ciphertext = "Queued content".toByteArray(Charsets.UTF_8),
            timestamp = now - 10_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.QUEUED,
            retryCount = 0,
            nextRetryAt = now - 1000L,
            expiresAt = now + Message.DEFAULT_TTL_MS
        )

        coEvery { eciesService.encryptToBase64(any(), eq(remotePublicKey), any()) } returns "EciesCiphertext"

        // No connected peers available
        val retries = meshRouter.retryEligibleMessages(emptyMap())

        assertTrue("No targets generated when no peers connected", retries.isEmpty())
        val updatedMsg = fakeMessageStorage[msgId]
        assertNotNull(updatedMsg)
        assertEquals(DeliveryStatus.QUEUED, updatedMsg?.deliveryStatus)
        assertEquals(1, updatedMsg?.retryCount)
        assertTrue("Pending message must be retained in queue", fakePendingStorage.containsKey(msgId))
    }
}
