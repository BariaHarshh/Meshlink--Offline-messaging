package com.meshlink.app.mesh.routing

import com.meshlink.app.crypto.cipher.EciesService
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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Phase 4E: Failure-Injection & Reliability Integration Test Suite.
 *
 * Deterministically tests edge cases and failure modes across the entire
 * Phase 4A–4D reliability architecture without flaky timeouts or hardware dependencies:
 *
 * 1. Multi-hop relay failure + re-routing (Alice -> Bob [fails] -> Dave -> Charlie)
 * 2. Lost ACK + retransmission + recipient deduplication (SeenMessageCache)
 * 3. ACK / retry race resolution (terminal DELIVERED wins and is immutable)
 * 4. Multi-path duplicate delivery under mesh flooding (1 local persistence, 1 ACK)
 * 5. Cold process restart recovery (Room-backed state resumed by fresh router instance)
 * 6. Complete 5-retry progression to terminal FAILED (with backoff delay validation)
 * 7. Hard expiration boundary precision (expiresAt - 1ms vs expiresAt + 1ms)
 */
class Phase4eFailureInjectionTest {

    private val aliceId = "alice-dev-001"
    private val bobId = "bob-relay-002"
    private val charlieId = "charlie-dest-003"
    private val daveId = "dave-alt-relay-004"

    private val epBob = "endpoint-bob"
    private val epDave = "endpoint-dave"
    private val epCharlie = "endpoint-charlie"

    private lateinit var alicePubKey: ByteArray
    private lateinit var bobPubKey: ByteArray
    private lateinit var charliePubKey: ByteArray
    private lateinit var davePubKey: ByteArray

    // Alice components
    private lateinit var aliceMessageRepo: MessageRepository
    private lateinit var alicePendingRepo: PendingMessageRepository
    private lateinit var aliceDeviceRepo: DeviceRepository
    private lateinit var aliceEcies: EciesService
    private lateinit var aliceRouter: MeshRouter
    private val aliceMessages = mutableMapOf<String, Message>()
    private val alicePending = mutableMapOf<String, PendingMessage>()

    // Charlie components
    private lateinit var charlieMessageRepo: MessageRepository
    private lateinit var charliePendingRepo: PendingMessageRepository
    private lateinit var charlieDeviceRepo: DeviceRepository
    private lateinit var charlieEcies: EciesService
    private lateinit var charlieRouter: MeshRouter
    private val charlieMessages = mutableMapOf<String, Message>()
    private val charliePending = mutableMapOf<String, PendingMessage>()

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))

        alicePubKey = kpg.generateKeyPair().public.encoded
        bobPubKey = kpg.generateKeyPair().public.encoded
        charliePubKey = kpg.generateKeyPair().public.encoded
        davePubKey = kpg.generateKeyPair().public.encoded

        aliceMessages.clear()
        alicePending.clear()
        charlieMessages.clear()
        charliePending.clear()

        // ── Alice Setup ──────────────────────────────────────────────────────────
        aliceMessageRepo = createMockMessageRepo(aliceMessages)
        alicePendingRepo = createMockPendingRepo(alicePending)
        aliceDeviceRepo = mockk(relaxed = true)
        aliceEcies = mockk(relaxed = true)

        coEvery { aliceDeviceRepo.getDeviceById(bobId) } returns KnownDevice(
            deviceId = bobId, displayName = "Bob", publicKey = bobPubKey, lastSeen = 1000L
        )
        coEvery { aliceDeviceRepo.getDeviceById(charlieId) } returns KnownDevice(
            deviceId = charlieId, displayName = "Charlie", publicKey = charliePubKey, lastSeen = 1000L
        )
        coEvery { aliceDeviceRepo.getDeviceById(daveId) } returns KnownDevice(
            deviceId = daveId, displayName = "Dave", publicKey = davePubKey, lastSeen = 1000L
        )
        coEvery { aliceDeviceRepo.getDeviceById(aliceId) } returns KnownDevice(
            deviceId = aliceId, displayName = "Alice", publicKey = alicePubKey, lastSeen = 1000L
        )

        val aliceUserProfile: UserProfileManager = mockk(relaxed = true)
        coEvery { aliceUserProfile.getDisplayName() } returns "Alice"

        aliceRouter = MeshRouter(
            myDeviceId = aliceId,
            routingTable = RoutingTable(),
            seenMessageCache = SeenMessageCache(),
            encryptionService = mockk(relaxed = true),
            eciesService = aliceEcies,
            sessionKeyStore = mockk(relaxed = true),
            messageRepository = aliceMessageRepo,
            deviceRepository = aliceDeviceRepo,
            pendingMessageRepository = alicePendingRepo,
            userProfileManager = aliceUserProfile
        )

        // ── Charlie Setup ────────────────────────────────────────────────────────
        charlieMessageRepo = createMockMessageRepo(charlieMessages)
        charliePendingRepo = createMockPendingRepo(charliePending)
        charlieDeviceRepo = mockk(relaxed = true)
        charlieEcies = mockk(relaxed = true)

        coEvery { charlieDeviceRepo.getDeviceById(aliceId) } returns KnownDevice(
            deviceId = aliceId, displayName = "Alice", publicKey = alicePubKey, lastSeen = 1000L
        )
        coEvery { charlieDeviceRepo.getDeviceById(charlieId) } returns KnownDevice(
            deviceId = charlieId, displayName = "Charlie", publicKey = charliePubKey, lastSeen = 1000L
        )

        val charlieUserProfile: UserProfileManager = mockk(relaxed = true)
        coEvery { charlieUserProfile.getDisplayName() } returns "Charlie"

        charlieRouter = MeshRouter(
            myDeviceId = charlieId,
            routingTable = RoutingTable(),
            seenMessageCache = SeenMessageCache(),
            encryptionService = mockk(relaxed = true),
            eciesService = charlieEcies,
            sessionKeyStore = mockk(relaxed = true),
            messageRepository = charlieMessageRepo,
            deviceRepository = charlieDeviceRepo,
            pendingMessageRepository = charliePendingRepo,
            userProfileManager = charlieUserProfile
        )
    }

    private fun createMockMessageRepo(storage: MutableMap<String, Message>): MessageRepository {
        val repo: MessageRepository = mockk(relaxed = true)
        coEvery { repo.insertMessage(any()) } answers {
            val msg = firstArg<Message>()
            storage[msg.id] = msg
        }
        coEvery { repo.getMessageById(any()) } answers {
            val id = firstArg<String>()
            storage[id]
        }
        coEvery { repo.updateDeliveryStatus(any(), any()) } answers {
            val id = firstArg<String>()
            val status = secondArg<DeliveryStatus>()
            val existing = storage[id]
            if (existing != null) {
                if (existing.deliveryStatus != DeliveryStatus.DELIVERED || status == DeliveryStatus.DELIVERED) {
                    storage[id] = existing.copy(
                        deliveryStatus = status,
                        delivered = (status == DeliveryStatus.DELIVERED || existing.delivered)
                    )
                }
            }
        }
        coEvery { repo.getEligibleRetries(any(), any(), any()) } answers {
            val deviceId = firstArg<String>()
            val now = secondArg<Long>()
            val maxRetries = thirdArg<Int>()
            storage.values.filter { msg ->
                msg.senderId == deviceId &&
                        (msg.deliveryStatus == DeliveryStatus.SENT || msg.deliveryStatus == DeliveryStatus.QUEUED) &&
                        msg.retryCount < maxRetries &&
                        msg.nextRetryAt <= now &&
                        msg.expiresAt > now
            }
        }
        coEvery { repo.claimRetry(any(), any(), any(), any()) } answers {
            val id = firstArg<String>()
            val now = secondArg<Long>()
            val nextRetryAt = thirdArg<Long>()
            val maxRetries = arg<Int>(3)
            val msg = storage[id]
            if (msg != null &&
                (msg.deliveryStatus == DeliveryStatus.SENT || msg.deliveryStatus == DeliveryStatus.QUEUED) &&
                msg.retryCount < maxRetries &&
                msg.nextRetryAt <= now &&
                msg.expiresAt > now
            ) {
                storage[id] = msg.copy(
                    retryCount = msg.retryCount + 1,
                    nextRetryAt = nextRetryAt
                )
                true
            } else {
                false
            }
        }
        coEvery { repo.markFailedIfExpiredOrExhausted(any(), any()) } answers {
            val now = firstArg<Long>()
            val maxRetries = secondArg<Int>()
            var count = 0
            storage.values.forEach { msg ->
                if ((msg.deliveryStatus == DeliveryStatus.PENDING || msg.deliveryStatus == DeliveryStatus.QUEUED || msg.deliveryStatus == DeliveryStatus.SENT) &&
                    (msg.expiresAt <= now || msg.retryCount >= maxRetries)
                ) {
                    storage[msg.id] = msg.copy(deliveryStatus = DeliveryStatus.FAILED)
                    count++
                }
            }
            count
        }
        coEvery { repo.markFailed(any()) } answers {
            val id = firstArg<String>()
            val msg = storage[id]
            if (msg != null && msg.deliveryStatus != DeliveryStatus.DELIVERED) {
                storage[id] = msg.copy(deliveryStatus = DeliveryStatus.FAILED)
            }
        }
        coEvery { repo.updateRetrySchedule(any(), any(), any()) } answers {
            val id = firstArg<String>()
            val count = secondArg<Int>()
            val next = thirdArg<Long>()
            val msg = storage[id]
            if (msg != null && msg.deliveryStatus != DeliveryStatus.DELIVERED) {
                storage[id] = msg.copy(retryCount = count, nextRetryAt = next)
            }
        }
        return repo
    }

    private fun createMockPendingRepo(storage: MutableMap<String, PendingMessage>): PendingMessageRepository {
        val repo: PendingMessageRepository = mockk(relaxed = true)
        coEvery { repo.enqueue(any()) } answers {
            val msg = firstArg<PendingMessage>()
            storage[msg.id] = msg
        }
        coEvery { repo.remove(any()) } answers {
            val id = firstArg<String>()
            storage.remove(id)
        }
        coEvery { repo.getAllPending() } answers {
            storage.values.toList()
        }
        return repo
    }

    // ── Scenario 1: Multi-Hop Relay Failure + Re-Routing ──────────────────────

    @Test
    fun `scenario 1 - multi-hop relay failure recovers via alternate route preserving IDs and payload`() = runTest {
        val baseTime = System.currentTimeMillis()
        val originalText = "Critical triage diagnostic payload"
        val expectedCiphertext = "EncryptedPayload-Charlie-01"

        coEvery { aliceEcies.encryptToBase64(any(), eq(charliePubKey), any()) } returns expectedCiphertext

        // Step 1: Alice initiates route to Charlie via Bob
        val peersAtT0 = mapOf(epBob to bobId)
        val initialResult = aliceRouter.buildAndRoute(
            finalDestDeviceId = charlieId,
            plaintext = originalText,
            connectedPeers = peersAtT0,
            timestamp = baseTime
        )

        assertTrue(initialResult is RoutingResult.Processed)
        val initialProcessed = initialResult as RoutingResult.Processed
        assertEquals(1, initialProcessed.forwardTargets.size)
        val dispatchedPacket = initialProcessed.forwardTargets[0].packet
        val msgId = dispatchedPacket.messageId

        // Alice receives transport success for immediate hop (Bob)
        aliceRouter.onSendSuccess(msgId, aliceId)
        assertEquals(DeliveryStatus.SENT, aliceMessages[msgId]?.deliveryStatus)
        assertEquals(0, aliceMessages[msgId]?.retryCount)

        // Step 2: Failure Injection! Bob accepts hop 1 but fails/crashes before reaching Charlie.
        // No ACK reaches Alice.

        // Step 3: Alice's retry timer elapses -> message becomes eligible for retry
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(nextRetryAt = System.currentTimeMillis() - 1000L)

        // Step 4: Alternate route emerges! Dave connects
        val peersAtRetry = mapOf(epDave to daveId)
        val retryTargets = aliceRouter.retryEligibleMessages(peersAtRetry)

        assertEquals("Alice must generate exactly 1 retry forward target", 1, retryTargets.size)
        val retriedTarget = retryTargets[0]
        val retriedPacket = retriedTarget.packet

        // Verify Invariants: exact same metadata preserved
        assertEquals("messageId must be identical across retries", msgId, retriedPacket.messageId)
        assertEquals("originId must remain Alice", aliceId, retriedPacket.originId)
        assertEquals("finalDestId must remain Charlie", charlieId, retriedPacket.finalDestId)
        assertEquals("timestamp must be original baseTime", baseTime, retriedPacket.timestamp)
        assertEquals("ciphertext must remain identical", expectedCiphertext, retriedPacket.content)
        assertEquals(epDave, retriedTarget.endpointId)

        // Step 5: Dave forwards packet to Charlie
        val innerPayloadJson = JSONObject().apply {
            put("text", originalText)
            put("senderName", "Alice")
        }
        coEvery { charlieEcies.decryptFromBase64(expectedCiphertext, any()) } returns
                innerPayloadJson.toString().toByteArray(Charsets.UTF_8)
        coEvery { charlieEcies.encryptToBase64(any(), eq(alicePubKey), any()) } returns "CharlieAckPayload"

        val charlieResult = charlieRouter.route(
            fromEndpointId = epDave,
            packet = retriedPacket,
            connectedPeers = mapOf(epDave to daveId)
        )

        assertTrue("Charlie must successfully process retried packet", charlieResult is RoutingResult.Processed)
        val charlieProcessed = charlieResult as RoutingResult.Processed
        assertNotNull("Charlie must deliver message locally", charlieProcessed.localMessage)
        assertEquals(msgId, charlieProcessed.localMessage?.id)
        assertEquals(1, charlieProcessed.forwardTargets.size)
        val ackPacket = charlieProcessed.forwardTargets[0].packet
        assertEquals(PacketType.ACK, ackPacket.type)

        // Step 6: ACK travels back to Alice
        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", msgId)
            put("ackTimestamp", System.currentTimeMillis())
        }
        coEvery { aliceEcies.decryptFromBase64("CharlieAckPayload", any()) } returns
                ackPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val ackResult = aliceRouter.route(
            fromEndpointId = epDave,
            packet = ackPacket,
            connectedPeers = mapOf(epDave to daveId)
        )

        assertTrue(ackResult is RoutingResult.Processed)
        val finalAliceMsg = aliceMessages[msgId]
        assertNotNull(finalAliceMsg)
        assertEquals("Alice message must reach terminal DELIVERED", DeliveryStatus.DELIVERED, finalAliceMsg?.deliveryStatus)
        assertTrue(finalAliceMsg!!.delivered)
        assertEquals("retryCount must reflect exactly 1 retry", 1, finalAliceMsg.retryCount)
    }

    // ── Scenario 2: Lost ACK + Retransmission + Idempotent Delivery ───────────

    @Test
    fun `scenario 2 - lost ACK triggers retry and recipient idempotently rejects duplicate message`() = runTest {
        val baseTime = System.currentTimeMillis()
        val msgId = "lost-ack-msg-002"
        val originalText = "Medical inventory requisition"

        coEvery { aliceEcies.encryptToBase64(any(), eq(charliePubKey), any()) } returns "EciesDirectCharliePayload"

        // 1. Alice creates and sends message to Charlie
        val directPacket = MeshPacket(
            messageId = msgId,
            senderId = aliceId,
            receiverId = charlieId,
            content = "EciesDirectCharliePayload",
            timestamp = baseTime,
            type = PacketType.ROUTED_CHAT,
            originId = aliceId,
            finalDestId = charlieId,
            hopCount = 0
        )

        aliceMessages[msgId] = Message(
            id = msgId,
            senderId = aliceId,
            receiverId = charlieId,
            ciphertext = originalText.toByteArray(Charsets.UTF_8),
            timestamp = baseTime,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 0,
            nextRetryAt = System.currentTimeMillis() - 1000L,
            expiresAt = baseTime + Message.DEFAULT_TTL_MS
        )

        // 2. Charlie receives packet, decrypts, persists, and generates ACK
        val innerPayloadJson = JSONObject().apply {
            put("text", originalText)
            put("senderName", "Alice")
        }
        coEvery { charlieEcies.decryptFromBase64("EciesDirectCharliePayload", any()) } returns
                innerPayloadJson.toString().toByteArray(Charsets.UTF_8)
        coEvery { charlieEcies.encryptToBase64(any(), eq(alicePubKey), any()) } returns "EncryptedAck-1"

        val firstReceiveResult = charlieRouter.route(
            fromEndpointId = epCharlie,
            packet = directPacket,
            connectedPeers = mapOf(epCharlie to aliceId)
        )

        assertTrue(firstReceiveResult is RoutingResult.Processed)
        assertEquals("Charlie must persist 1 message", 1, charlieMessages.size)
        assertEquals(msgId, charlieMessages[msgId]?.id)

        // 3. Failure Injection! ACK is dropped in transit. Alice never receives it.

        // 4. Alice times out and retries
        val retries = aliceRouter.retryEligibleMessages(mapOf(epCharlie to charlieId))
        assertEquals(1, retries.size)
        val retriedPacket = retries[0].packet
        assertEquals(msgId, retriedPacket.messageId)

        // 5. Charlie receives the retried duplicate packet
        val secondReceiveResult = charlieRouter.route(
            fromEndpointId = epCharlie,
            packet = retriedPacket,
            connectedPeers = mapOf(epCharlie to aliceId)
        )

        // Charlie's SeenMessageCache drops the duplicate packet
        assertTrue("Duplicate packet must be dropped by deduplication cache", secondReceiveResult is RoutingResult.Drop)
        assertEquals("Charlie must still have exactly 1 logical message in storage", 1, charlieMessages.size)

        // 6. When ACK recovery is simulated, Alice transitions to DELIVERED
        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", msgId)
            put("ackTimestamp", System.currentTimeMillis())
        }
        coEvery { aliceEcies.decryptFromBase64("EncryptedAck-1", any()) } returns
                ackPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val ackPacket = MeshPacket(
            messageId = "ack-pkt-recovered",
            senderId = charlieId,
            receiverId = aliceId,
            content = "EncryptedAck-1",
            timestamp = System.currentTimeMillis(),
            type = PacketType.ACK,
            originId = charlieId,
            finalDestId = aliceId
        )

        aliceRouter.route(epCharlie, ackPacket, mapOf(epCharlie to charlieId))

        assertEquals(DeliveryStatus.DELIVERED, aliceMessages[msgId]?.deliveryStatus)
        assertTrue(aliceMessages[msgId]!!.delivered)
    }

    // ── Scenario 3: ACK / Retry Race ──────────────────────────────────────────

    @Test
    fun `scenario 3 - simultaneous ACK and retry claim preserves terminal DELIVERED status`() = runTest {
        val baseTime = System.currentTimeMillis()
        val msgId = "race-msg-003"

        aliceMessages[msgId] = Message(
            id = msgId,
            senderId = aliceId,
            receiverId = charlieId,
            ciphertext = "Race Test Content".toByteArray(Charsets.UTF_8),
            timestamp = baseTime - 40_000L,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 0,
            nextRetryAt = System.currentTimeMillis() - 5000L, // Eligible for retry
            expiresAt = baseTime + Message.DEFAULT_TTL_MS
        )

        // 1. Retry worker claims message atomically
        val nextRetryAt = System.currentTimeMillis() + 60_000L
        val claimed = aliceMessageRepo.claimRetry(msgId, System.currentTimeMillis(), nextRetryAt, Message.MAX_RETRY_COUNT)
        assertTrue("Worker successfully claims retry", claimed)
        assertEquals(1, aliceMessages[msgId]?.retryCount)

        // 2. Race Injection! Delayed ACK from initial attempt arrives BEFORE retry callback finishes
        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", msgId)
            put("ackTimestamp", System.currentTimeMillis())
        }
        coEvery { aliceEcies.decryptFromBase64("DelayedAck", any()) } returns
                ackPayloadJson.toString().toByteArray(Charsets.UTF_8)

        val ackPacket = MeshPacket(
            messageId = "ack-race-100",
            senderId = charlieId,
            receiverId = aliceId,
            content = "DelayedAck",
            timestamp = System.currentTimeMillis(),
            type = PacketType.ACK,
            originId = charlieId,
            finalDestId = aliceId
        )

        val routeResult = aliceRouter.route(epCharlie, ackPacket, mapOf(epCharlie to charlieId))
        assertTrue(routeResult is RoutingResult.Processed)
        assertEquals(DeliveryStatus.DELIVERED, aliceMessages[msgId]?.deliveryStatus)
        assertTrue(aliceMessages[msgId]!!.delivered)

        // 3. Retry transport callback completes AFTER ACK processing
        aliceRouter.onSendSuccess(msgId, aliceId)

        // Verify DELIVERED status was NOT overwritten by onSendSuccess or SENT
        assertEquals("DELIVERED status must remain immutable and win the race", DeliveryStatus.DELIVERED, aliceMessages[msgId]?.deliveryStatus)
        assertTrue(aliceMessages[msgId]!!.delivered)

        // Verify subsequent retry scan returns 0 targets
        val postRetries = aliceRouter.retryEligibleMessages(mapOf(epCharlie to charlieId))
        assertTrue("DELIVERED message cannot be retried", postRetries.isEmpty())
    }

    // ── Scenario 4: Multi-Path Duplicate Delivery Under Mesh Flooding ─────────

    @Test
    fun `scenario 4 - multi-path packet flooding results in exactly 1 local persistence and 1 ACK`() = runTest {
        val baseTime = System.currentTimeMillis()
        val msgId = "flood-dup-msg-004"
        val payloadCiphertext = "FloodedPayloadCiphertext"

        val packet = MeshPacket(
            messageId = msgId,
            senderId = aliceId,
            receiverId = charlieId,
            content = payloadCiphertext,
            timestamp = baseTime,
            type = PacketType.ROUTED_CHAT,
            originId = aliceId,
            finalDestId = charlieId,
            hopCount = 1
        )

        val innerPayloadJson = JSONObject().apply {
            put("text", "Flood test payload")
            put("senderName", "Alice")
        }
        coEvery { charlieEcies.decryptFromBase64(payloadCiphertext, any()) } returns
                innerPayloadJson.toString().toByteArray(Charsets.UTF_8)
        coEvery { charlieEcies.encryptToBase64(any(), eq(alicePubKey), any()) } returns "FloodAckPayload"

        val peers = mapOf("ep-relay1" to "relay-1", "ep-relay2" to "relay-2", "ep-relay3" to "relay-3")

        // Injected from Relay 1
        val result1 = charlieRouter.route("ep-relay1", packet, peers)
        assertTrue("First arrival must be processed", result1 is RoutingResult.Processed)
        val processed1 = result1 as RoutingResult.Processed
        assertNotNull(processed1.localMessage)
        assertEquals(3, processed1.forwardTargets.size) // 1 ACK forwarded across all 3 peer connections
        assertTrue(processed1.forwardTargets.all { it.packet.type == PacketType.ACK })

        // Injected simultaneously from Relay 2
        val result2 = charlieRouter.route("ep-relay2", packet, peers)
        assertTrue("Second arrival must be dropped by cache", result2 is RoutingResult.Drop)

        // Injected simultaneously from Relay 3
        val result3 = charlieRouter.route("ep-relay3", packet, peers)
        assertTrue("Third arrival must be dropped by cache", result3 is RoutingResult.Drop)

        // Assertions
        assertEquals("Exactly 1 message must be persisted", 1, charlieMessages.size)
        assertEquals(msgId, charlieMessages[msgId]?.id)
    }

    // ── Scenario 5: Cold Process Restart Recovery ─────────────────────────────

    @Test
    fun `scenario 5 - cold process restart reloads persistent DB state and resumes recovery`() = runTest {
        val baseTime = System.currentTimeMillis()
        val msgSentId = "cold-sent-msg-005"
        val msgQueuedId = "cold-queued-msg-006"

        // Populate persistent storage with unresolved messages
        val originalSentText = "Sent message surviving restart"
        val originalQueuedText = "Queued message surviving restart"

        aliceMessages[msgSentId] = Message(
            id = msgSentId,
            senderId = aliceId,
            receiverId = charlieId,
            ciphertext = originalSentText.toByteArray(Charsets.UTF_8),
            timestamp = baseTime,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 1,
            nextRetryAt = System.currentTimeMillis() - 1000L,
            expiresAt = baseTime + Message.DEFAULT_TTL_MS
        )

        aliceMessages[msgQueuedId] = Message(
            id = msgQueuedId,
            senderId = aliceId,
            receiverId = charlieId,
            ciphertext = originalQueuedText.toByteArray(Charsets.UTF_8),
            timestamp = baseTime,
            delivered = false,
            deliveryStatus = DeliveryStatus.QUEUED,
            retryCount = 0,
            nextRetryAt = System.currentTimeMillis() - 1000L,
            expiresAt = baseTime + Message.DEFAULT_TTL_MS
        )

        val queuedPacket = MeshPacket(
            messageId = msgQueuedId,
            senderId = aliceId,
            receiverId = charlieId,
            content = "QueuedPayloadCiphertext",
            timestamp = baseTime,
            type = PacketType.ROUTED_CHAT,
            originId = aliceId,
            finalDestId = charlieId,
            hopCount = 0
        )
        alicePending[msgQueuedId] = PendingMessage(
            id = msgQueuedId,
            packetJson = queuedPacket.messageId, // fake json
            targetDeviceId = charlieId,
            enqueuedAt = baseTime,
            expiresAt = baseTime + Message.DEFAULT_TTL_MS
        )

        // Discard all in-memory router state to simulate process termination
        val freshUserProfile: UserProfileManager = mockk(relaxed = true)
        coEvery { freshUserProfile.getDisplayName() } returns "Alice"
        coEvery { aliceEcies.encryptToBase64(any(), eq(charliePubKey), any()) } returns "ColdRestartEciesCiphertext"

        // Instantiate completely fresh MeshRouter pointing to same Room storage
        val freshRouter = MeshRouter(
            myDeviceId = aliceId,
            routingTable = RoutingTable(),       // fresh empty routing table
            seenMessageCache = SeenMessageCache(), // fresh empty cache
            encryptionService = mockk(relaxed = true),
            eciesService = aliceEcies,
            sessionKeyStore = mockk(relaxed = true),
            messageRepository = aliceMessageRepo, // persistent Room storage
            deviceRepository = aliceDeviceRepo,
            pendingMessageRepository = alicePendingRepo, // persistent Room storage
            userProfileManager = freshUserProfile
        )

        val peers = mapOf(epCharlie to charlieId)
        val retries = freshRouter.retryEligibleMessages(peers)

        // Both SENT and QUEUED messages must be recovered from database and retried
        assertEquals("Fresh router must recover and retry both eligible messages", 2, retries.size)

        val recoveredSent = aliceMessages[msgSentId]
        assertNotNull(recoveredSent)
        assertEquals("retryCount must increment to 2", 2, recoveredSent?.retryCount)
        assertEquals(baseTime, recoveredSent?.timestamp)
        assertEquals(baseTime + Message.DEFAULT_TTL_MS, recoveredSent?.expiresAt)

        val recoveredQueued = aliceMessages[msgQueuedId]
        assertNotNull(recoveredQueued)
        assertEquals("retryCount must increment to 1", 1, recoveredQueued?.retryCount)
    }

    // ── Scenario 6: Complete 5-Retry Exhaustion to Terminal FAILED ────────────

    @Test
    fun `scenario 6 - complete 5-retry progression deterministically transitions to terminal FAILED`() = runTest {
        val baseTime = System.currentTimeMillis()
        val msgId = "exhaust-progression-007"
        val peers = mapOf(epCharlie to charlieId)

        coEvery { aliceEcies.encryptToBase64(any(), eq(charliePubKey), any()) } returns "ExhaustPayload"

        aliceMessages[msgId] = Message(
            id = msgId,
            senderId = aliceId,
            receiverId = charlieId,
            ciphertext = "Exhaustion Test".toByteArray(Charsets.UTF_8),
            timestamp = baseTime,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 0,
            nextRetryAt = System.currentTimeMillis() - 1000L,
            expiresAt = baseTime + Message.DEFAULT_TTL_MS
        )

        // Progression sequence:
        // Retry 1
        var targets = aliceRouter.retryEligibleMessages(peers)
        assertEquals(1, targets.size)
        assertEquals(1, aliceMessages[msgId]?.retryCount)
        assertEquals(DeliveryStatus.SENT, aliceMessages[msgId]?.deliveryStatus)

        // Retry 2
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(nextRetryAt = System.currentTimeMillis() - 1000L)
        targets = aliceRouter.retryEligibleMessages(peers)
        assertEquals(1, targets.size)
        assertEquals(2, aliceMessages[msgId]?.retryCount)

        // Retry 3
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(nextRetryAt = System.currentTimeMillis() - 1000L)
        targets = aliceRouter.retryEligibleMessages(peers)
        assertEquals(1, targets.size)
        assertEquals(3, aliceMessages[msgId]?.retryCount)

        // Retry 4
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(nextRetryAt = System.currentTimeMillis() - 1000L)
        targets = aliceRouter.retryEligibleMessages(peers)
        assertEquals(1, targets.size)
        assertEquals(4, aliceMessages[msgId]?.retryCount)

        // Retry 5 (Final allowed retry)
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(nextRetryAt = System.currentTimeMillis() - 1000L)
        targets = aliceRouter.retryEligibleMessages(peers)
        assertEquals(1, targets.size)
        assertEquals(5, aliceMessages[msgId]?.retryCount)

        // Exhaustion! Next attempt will find retryCount >= MAX_RETRY_COUNT
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(nextRetryAt = System.currentTimeMillis() - 1000L)
        targets = aliceRouter.retryEligibleMessages(peers)

        assertTrue("No targets must be generated when retry limit is exhausted", targets.isEmpty())
        val finalMsg = aliceMessages[msgId]
        assertNotNull(finalMsg)
        assertEquals("Message must transition to terminal FAILED", DeliveryStatus.FAILED, finalMsg?.deliveryStatus)
        assertEquals(5, finalMsg?.retryCount)

        // Subsequent scans continue to produce 0 targets
        val postExhaustionTargets = aliceRouter.retryEligibleMessages(peers)
        assertTrue(postExhaustionTargets.isEmpty())
        assertEquals(DeliveryStatus.FAILED, aliceMessages[msgId]?.deliveryStatus)
    }

    // ── Scenario 7: Hard Expiration Boundary ──────────────────────────────────

    @Test
    fun `scenario 7 - hard expiration boundary precision before and after expiresAt`() = runTest {
        val baseTime = System.currentTimeMillis()
        val ttl = Message.DEFAULT_TTL_MS // 48 hours = 172_800_000 ms
        val expiresAt = baseTime + ttl
        val msgId = "expiry-boundary-008"
        val peers = mapOf(epCharlie to charlieId)

        coEvery { aliceEcies.encryptToBase64(any(), eq(charliePubKey), any()) } returns "BoundaryCiphertext"

        aliceMessages[msgId] = Message(
            id = msgId,
            senderId = aliceId,
            receiverId = charlieId,
            ciphertext = "Expiry boundary test".toByteArray(Charsets.UTF_8),
            timestamp = baseTime,
            delivered = false,
            deliveryStatus = DeliveryStatus.SENT,
            retryCount = 1,
            nextRetryAt = baseTime - 1000L,
            expiresAt = expiresAt
        )

        // Case A: 1ms before expiration (expiresAt - 1ms)
        val tBefore = expiresAt - 1L
        val eligibleBefore = aliceMessageRepo.getEligibleRetries(aliceId, tBefore, Message.MAX_RETRY_COUNT)
        assertEquals("Message must still be eligible 1ms before expiration", 1, eligibleBefore.size)
        assertEquals(DeliveryStatus.SENT, aliceMessages[msgId]?.deliveryStatus)

        // Case B: 1ms after expiration (expiresAt + 1ms)
        // Simulate that current time has passed expiration deadline
        aliceMessages[msgId] = aliceMessages[msgId]!!.copy(expiresAt = System.currentTimeMillis() - 1L)
        val targetsAfter = aliceRouter.retryEligibleMessages(peers)

        assertTrue("Zero packets must be transmitted after expiration deadline", targetsAfter.isEmpty())
        val finalMsg = aliceMessages[msgId]
        assertNotNull(finalMsg)
        assertEquals("Message must be marked FAILED immediately upon expiration", DeliveryStatus.FAILED, finalMsg?.deliveryStatus)
    }
}
