package com.meshlink.app.mesh.routing

import android.util.Base64
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.MeshPacket.PacketType
import com.meshlink.app.domain.model.PendingMessage
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Core mesh routing engine — pure logic, no Nearby transport calls.
 *
 * [NearbyRepositoryImpl] calls [route] for every incoming packet and [buildAndRoute] for
 * every outgoing message, then acts on the returned [RoutingResult].
 *
 * Routing algorithm:
 *   1. Deduplication   — [SeenMessageCache] drops packets we've already forwarded.
 *   2. TTL check       — drop if hopCount >= maxHops.
 *   3. Local delivery  — if finalDestId == myDeviceId (or broadcast), decrypt and deliver.
 *   4. Forwarding      — if route exists in [RoutingTable], unicast to next hop;
 *                        otherwise flood to all connected peers except the sender.
 *   5. Store-and-fwd   — if no peers available at all, enqueue in [PendingMessageRepository].
 *
 * Encryption model:
 *   CHAT          → AES-256-GCM session key (direct link only)
 *   ROUTED_CHAT   → ECIES (recipient's public key; relay nodes are opaque)
 *   BROADCAST     → no encryption (public announcement)
 */
@Singleton
class MeshRouter @Inject constructor(
    @Named("localDeviceId") private val myDeviceId: String,
    private val routingTable: RoutingTable,
    private val seenMessageCache: SeenMessageCache,
    private val encryptionService: EncryptionService,
    private val eciesService: EciesService,
    private val sessionKeyStore: SessionKeyStore,
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val pendingMessageRepository: PendingMessageRepository,
    private val userProfileManager: UserProfileManager
) {
    companion object {
        private const val PENDING_TTL_MS = 48 * 60 * 60 * 1_000L  // 48 hours

        /**
         * Builds deterministic Additional Authenticated Data (AAD) binding security-sensitive
         * routing metadata to the AEAD cipher context.
         */
        fun computeMetadataAad(
            originId: String,
            finalDestId: String,
            messageId: String,
            timestamp: Long
        ): ByteArray =
            "ROUTED_CHAT_AAD:$originId:$finalDestId:$messageId:$timestamp".toByteArray(Charsets.UTF_8)

        /**
         * Phase 4C: Builds deterministic Additional Authenticated Data (AAD) binding security-sensitive
         * routing metadata to the AEAD cipher context for ACK packets.
         */
        fun computeAckMetadataAad(
            originId: String,
            finalDestId: String,
            ackPacketId: String,
            timestamp: Long
        ): ByteArray =
            "ROUTED_ACK_AAD:$originId:$finalDestId:$ackPacketId:$timestamp".toByteArray(Charsets.UTF_8)
    }

    // ── Incoming packet routing ───────────────────────────────────────────────

    /**
     * Process an incoming [packet] received from [fromEndpointId].
     *
     * @param fromEndpointId  Nearby endpointId of the immediate sender (one hop back).
     * @param packet          The deserialized [MeshPacket].
     * @param connectedPeers  Current snapshot of endpointId → peerDeviceId for all CONNECTED peers.
     *
     * @return [RoutingResult] describing what [NearbyRepositoryImpl] should do next.
     *         The caller is responsible for all Nearby transport calls.
     */
    suspend fun route(
        fromEndpointId: String,
        packet: MeshPacket,
        connectedPeers: Map<String, String>    // endpointId → peerDeviceId
    ): RoutingResult = withContext(Dispatchers.IO) {

        // 0. Packet validation
        val validation = PacketValidator.validatePacket(packet)
        if (validation is PacketValidator.ValidationResult.Invalid) {
            Timber.w("MeshRouter: DROP malformed packet")
            return@withContext RoutingResult.Drop
        }

        // Loop detection: drop if this node is already in routeHistory
        if (packet.routeHistory.contains(myDeviceId)) {
            Timber.d("MeshRouter: DROP routing loop detected")
            return@withContext RoutingResult.Drop
        }

        // Route history size check
        if (packet.routeHistory.size >= PacketValidator.MAX_ROUTE_HISTORY) {
            Timber.d("MeshRouter: DROP route history exceeded limit")
            return@withContext RoutingResult.Drop
        }

        // 1. Deduplication & Replay protection — drop packets we've already seen
        if (seenMessageCache.isAlreadySeen(packet.messageId, packet.originId)) {
            Timber.d("MeshRouter: DROP duplicate/replay packet")
            return@withContext RoutingResult.Drop
        }
        seenMessageCache.markSeen(packet.messageId, packet.originId)

        // 2. TTL check
        if (packet.hopCount >= packet.maxHops) {
            Timber.d("MeshRouter: DROP TTL exceeded")
            return@withContext RoutingResult.Drop
        }

        val isForMe       = packet.finalDestId == myDeviceId
        val isBroadcast   = packet.isBroadcast
        val needForward   = !isForMe  // broadcasts are always forwarded AND delivered

        // ── Phase 4C: ACK Handling ──────────────────────────────────────────
        if (packet.type == PacketType.ACK) {
            if (isForMe) {
                // Sender received an ACK for an outgoing message
                val aad = computeAckMetadataAad(
                    originId    = packet.originId,
                    finalDestId = packet.finalDestId,
                    ackPacketId = packet.messageId,
                    timestamp   = packet.timestamp
                )
                val decryptedBytes = eciesService.decryptFromBase64(packet.content, aad)
                if (decryptedBytes == null) {
                    Timber.w("SECURITY: ACK decryption/verification failed from origin=${packet.originId}")
                    return@withContext RoutingResult.Drop
                }

                val ackMessageId = try {
                    val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))
                    json.getString("ackMessageId")
                } catch (e: Exception) {
                    Timber.w(e, "MeshRouter: malformed ACK payload from origin=${packet.originId}")
                    return@withContext RoutingResult.Drop
                }

                // Verify the original message exists, we are the sender, and packet.originId is the intended recipient
                val originalMsg = messageRepository.getMessageById(ackMessageId)
                if (originalMsg == null) {
                    Timber.w("MeshRouter: received ACK for unknown message $ackMessageId — ignoring")
                    return@withContext RoutingResult.Drop
                }

                if (originalMsg.receiverId != packet.originId) {
                    Timber.w("SECURITY: Spoofed ACK rejected — original receiver (${originalMsg.receiverId}) != ACK origin (${packet.originId})")
                    return@withContext RoutingResult.Drop
                }

                if (originalMsg.senderId != myDeviceId) {
                    Timber.w("SECURITY: ACK rejected — original sender (${originalMsg.senderId}) is not local device ($myDeviceId)")
                    return@withContext RoutingResult.Drop
                }

                messageRepository.updateDeliveryStatus(ackMessageId, DeliveryStatus.DELIVERED)
                Timber.i("MeshRouter: ACK verified for $ackMessageId from ${packet.originId} -> DELIVERED")

                return@withContext RoutingResult.Processed(
                    localMessage   = null,
                    forwardTargets = emptyList()
                )
            } else {
                // Relay node: forward ACK toward finalDestId
                val forwardTargets = buildForwardTargets(
                    packet         = packet,
                    fromEndpointId = fromEndpointId,
                    connectedPeers = connectedPeers,
                    isForMe        = false
                )
                return@withContext RoutingResult.Processed(
                    localMessage   = null,
                    forwardTargets = forwardTargets
                )
            }
        }

        // 3. Local delivery
        var localMessage: Message? = null
        val ackTargets = mutableListOf<ForwardTarget>()
        if (isForMe || isBroadcast) {
            localMessage = decryptAndPersist(packet)
            // Phase 4C: Generate ACK only after successful authentication, decryption, and persistence
            if (localMessage != null && !isBroadcast && packet.type != PacketType.ACK) {
                ackTargets.addAll(generateAckTargets(packet, connectedPeers))
            }
        }

        // 4. Forward / flood
        val forwardTargets = if (needForward || isBroadcast) {
            buildForwardTargets(
                packet          = packet,
                fromEndpointId  = fromEndpointId,
                connectedPeers  = connectedPeers,
                isForMe         = isForMe
            )
        } else emptyList()

        RoutingResult.Processed(
            localMessage   = localMessage,
            forwardTargets = forwardTargets + ackTargets
        )
    }

    // ── Outgoing message routing ──────────────────────────────────────────────

    /**
     * Encrypt and route an outgoing message from the local device.
     *
     * Decision tree:
     *   • [finalDestDeviceId] is directly connected (session key in [SessionKeyStore])
     *     → PacketType.CHAT  (AES-256-GCM, existing Phase 3 path)
     *   • [finalDestDeviceId] is known (public key in [DeviceRepository]) but not connected
     *     → PacketType.ROUTED_CHAT (ECIES; relayed by intermediate peers)
     *   • Completely unknown
     *     → Enqueue in [PendingMessageRepository] and return [RoutingResult.Queued]
     */
    suspend fun buildAndRoute(
        finalDestDeviceId: String,
        plaintext: String,
        connectedPeers: Map<String, String>,   // endpointId → peerDeviceId
        timestamp: Long = System.currentTimeMillis()
    ): RoutingResult = withContext(Dispatchers.IO) {

        val plaintextBytes    = plaintext.toByteArray(Charsets.UTF_8)
        val messageId         = UUID.randomUUID().toString()
        val localDisplayName  = userProfileManager.getDisplayName()

        // ── Direct connection path (CHAT) ─────────────────────────────────────
        val directEndpoint = connectedPeers.entries
            .firstOrNull { it.value == finalDestDeviceId }?.key

        if (directEndpoint != null) {
            val sessionKey = sessionKeyStore.getSessionKey(directEndpoint)
            if (sessionKey != null) {
                val encryptedBytes   = encryptionService.encrypt(plaintextBytes, sessionKey)
                val encryptedContent = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

                val packet = MeshPacket(
                    senderId     = myDeviceId,
                    receiverId   = finalDestDeviceId,
                    content      = encryptedContent,
                    timestamp    = timestamp,
                    type         = PacketType.CHAT,
                    messageId    = messageId,
                    originId     = myDeviceId,
                    finalDestId  = finalDestDeviceId,
                    hopCount     = 0,
                    maxHops      = 7,
                    senderName   = localDisplayName
                )

                // Persist plaintext (sender side)
                messageRepository.insertMessage(
                    Message(
                        id             = messageId,
                        senderId       = myDeviceId,
                        receiverId     = finalDestDeviceId,
                        ciphertext     = plaintextBytes,
                        timestamp      = timestamp,
                        delivered      = false,
                        senderName     = localDisplayName,
                        deliveryStatus = DeliveryStatus.PENDING
                    )
                )

                seenMessageCache.markSeen(messageId)
                return@withContext RoutingResult.Processed(
                    localMessage   = null,
                    forwardTargets = listOf(ForwardTarget(directEndpoint, packet))
                )
            }
        }

        // ── Multi-hop path (ROUTED_CHAT via ECIES) ────────────────────────────
        val destDevice = deviceRepository.getDeviceById(finalDestDeviceId)

        if (destDevice != null) {
            val aad = computeMetadataAad(
                originId    = myDeviceId,
                finalDestId = finalDestDeviceId,
                messageId   = messageId,
                timestamp   = timestamp
            )

            // Phase 3A: Bundle plaintext and senderName inside inner encrypted payload.
            // Relays only forward the opaque ciphertext and do not learn the sender's display name.
            val innerPayloadJson = JSONObject().apply {
                put("text", plaintext)
                if (localDisplayName.isNotEmpty()) {
                    put("senderName", localDisplayName)
                }
            }
            val innerPayloadBytes = innerPayloadJson.toString().toByteArray(Charsets.UTF_8)
            val eciesContent = eciesService.encryptToBase64(innerPayloadBytes, destDevice.publicKey, aad)

            val packet = MeshPacket(
                senderId     = myDeviceId,
                receiverId   = finalDestDeviceId,  // will be updated per-hop by forwarder
                content      = eciesContent,
                timestamp    = timestamp,
                type         = PacketType.ROUTED_CHAT,
                messageId    = messageId,
                originId     = myDeviceId,
                finalDestId  = finalDestDeviceId,
                hopCount     = 0,
                maxHops      = 7,
                senderName   = ""  // Omitted from outer routing metadata
            )

            // Persist plaintext on sender side (we know what we sent)
            val initialNextRetry = timestamp + calculateNextRetryDelayMs(0)
            val expiresAt = timestamp + Message.DEFAULT_TTL_MS
            messageRepository.insertMessage(
                Message(
                    id             = messageId,
                    senderId       = myDeviceId,
                    receiverId     = finalDestDeviceId,
                    ciphertext     = plaintextBytes,
                    timestamp      = timestamp,
                    delivered      = false,
                    senderName     = localDisplayName,
                    deliveryStatus = DeliveryStatus.PENDING,
                    retryCount     = 0,
                    nextRetryAt    = initialNextRetry,
                    expiresAt      = expiresAt
                )
            )

            seenMessageCache.markSeen(messageId)

            val forwardTargets = buildForwardTargets(
                packet         = packet,
                fromEndpointId = null,       // no "from" for self-originated
                connectedPeers = connectedPeers,
                isForMe        = false
            )

            if (forwardTargets.isEmpty()) {
                // No connected peers — queue it
                enqueuePending(packet, finalDestDeviceId, timestamp)
                messageRepository.updateDeliveryStatus(messageId, DeliveryStatus.QUEUED)
                return@withContext RoutingResult.Queued(messageId)
            }

            return@withContext RoutingResult.Processed(
                localMessage   = null,
                forwardTargets = forwardTargets
            )
        }

        // ── Unknown destination ───────────────────────────────────────────────
        Timber.w("MeshRouter: unknown destination — no public key stored")
        RoutingResult.UnknownDestination(finalDestDeviceId)
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /**
     * Build a [PacketType.BROADCAST] packet originating from this device and flood it.
     * The broadcast is also delivered locally (creates a system message in Room).
     */
    fun buildBroadcast(
        content: String,
        connectedPeers: Map<String, String>
    ): RoutingResult {
        val messageId = UUID.randomUUID().toString()
        seenMessageCache.markSeen(messageId)

        val packet = MeshPacket(
            senderId    = myDeviceId,
            receiverId  = MeshPacket.BROADCAST_DEST,
            content     = content,
            timestamp   = System.currentTimeMillis(),
            type        = PacketType.BROADCAST,
            messageId   = messageId,
            originId    = myDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount    = 0,
            maxHops     = 10,
            senderName  = userProfileManager.getDisplayName()
        )

        val targets = connectedPeers.keys.map { ep -> ForwardTarget(ep, packet) }
        return RoutingResult.Processed(localMessage = null, forwardTargets = targets)
    }

    // ── Pending queue flushing ────────────────────────────────────────────────

    /**
     * Called when a new peer connects. Returns [ForwardTarget]s for any queued messages
     * that can now be delivered via the newly connected peers.
     *
     * [NearbyRepositoryImpl] calls this after every successful handshake.
     */
    suspend fun flushPendingQueue(
        connectedPeers: Map<String, String>
    ): List<ForwardTarget> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val allPending = pendingMessageRepository.getAllPending()
        if (allPending.isEmpty()) return@withContext emptyList()

        val targets = mutableListOf<ForwardTarget>()
        for (pending in allPending) {
            // Enforce global expiration: drop if expired
            if (pending.expiresAt <= now) {
                pendingMessageRepository.remove(pending.id)
                messageRepository.markFailed(pending.id)
                Timber.d("MeshRouter: dropped expired pending message ${pending.id}")
                continue
            }

            // Check if corresponding message is already DELIVERED or FAILED
            val msg = messageRepository.getMessageById(pending.id)
            if (msg != null && (msg.deliveryStatus == DeliveryStatus.DELIVERED || msg.deliveryStatus == DeliveryStatus.FAILED)) {
                pendingMessageRepository.remove(pending.id)
                continue
            }

            val packet = pending.packetJson.toMeshPacketOrNull() ?: run {
                pendingMessageRepository.remove(pending.id)
                continue
            }

            // Enforce global hop limit: drop if max hops already reached
            if (packet.hopCount >= packet.maxHops) {
                pendingMessageRepository.remove(pending.id)
                Timber.d("MeshRouter: dropped pending message — max hops reached")
                continue
            }

            // Can any connected peer forward this to its destination?
            val nextHop = findNextHop(packet.finalDestId, connectedPeers)
            if (nextHop != null) {
                // Preserve original hopCount, timestamp, and lifetime (do NOT reset hopCount!)
                targets.add(ForwardTarget(nextHop, packet))
                // Phase 4B: Do NOT delete pending message from repository here.
                // It is only removed after transport transmission success is confirmed via onSendSuccess().
                Timber.i("MeshRouter: candidate found for pending message ${packet.messageId} → $nextHop (hopCount=${packet.hopCount})")
            }
        }
        targets
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun decryptAndPersist(packet: MeshPacket): Message? {
        return when (packet.type) {
            PacketType.CHAT -> {
                // AES-256-GCM via session key with origin sender
                val originEndpoint = findEndpointForDevice(packet.originId)
                val sessionKey     = originEndpoint?.let { sessionKeyStore.getSessionKey(it) }
                if (sessionKey == null) {
                    Timber.w("MeshRouter: no session key for CHAT")
                    return null
                }
                val encryptedBytes = Base64.decode(packet.content, Base64.NO_WRAP)
                val plaintext      = encryptionService.decrypt(encryptedBytes, sessionKey) ?: run {
                    Timber.w("MeshRouter: AES-GCM decrypt failed")
                    return null
                }
                persistAndReturn(packet, plaintext)
            }

            PacketType.ROUTED_CHAT -> {
                // ECIES — decrypt with our private key, verifying metadata AAD
                val aad = computeMetadataAad(
                    originId    = packet.originId,
                    finalDestId = packet.finalDestId,
                    messageId   = packet.messageId,
                    timestamp   = packet.timestamp
                )
                val decryptedBytes = eciesService.decryptFromBase64(packet.content, aad) ?: run {
                    Timber.w("MeshRouter: ECIES decrypt failed (authentication/metadata verification failed)")
                    return null
                }

                // Phase 3A: Extract inner payload (text + senderName)
                val (plaintextBytes, extractedSenderName) = try {
                    val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))
                    if (json.has("text")) {
                        val text = json.getString("text")
                        val sender = json.optString("senderName", packet.senderName)
                        Pair(text.toByteArray(Charsets.UTF_8), sender)
                    } else {
                        Pair(decryptedBytes, packet.senderName)
                    }
                } catch (_: Exception) {
                    Pair(decryptedBytes, packet.senderName)
                }

                persistAndReturn(packet.copy(senderName = extractedSenderName), plaintextBytes)
            }

            PacketType.BROADCAST -> {
                // Plaintext — no decryption needed
                persistAndReturn(packet, packet.content.toByteArray(Charsets.UTF_8))
            }

            else -> null  // HANDSHAKE / ACK not routed through MeshRouter
        }
    }

    private suspend fun persistAndReturn(packet: MeshPacket, plaintext: ByteArray): Message {
        val msg = Message(
            id         = packet.messageId,
            senderId   = packet.originId,
            receiverId = myDeviceId,
            ciphertext = plaintext,     // stored as plaintext for UI display (at-rest = disk encryption)
            timestamp  = packet.timestamp,
            delivered  = true,
            senderName = packet.senderName
        )
        messageRepository.insertMessage(msg)

        // Update the sender's known display name if provided
        if (packet.senderName.isNotEmpty()) {
            val existingDevice = deviceRepository.getDeviceById(packet.originId)
            if (existingDevice != null && existingDevice.displayName != packet.senderName) {
                deviceRepository.updateDisplayName(packet.originId, packet.senderName)
            }
        }

        return msg
    }

    private fun buildForwardTargets(
        packet: MeshPacket,
        fromEndpointId: String?,
        connectedPeers: Map<String, String>,
        isForMe: Boolean
    ): List<ForwardTarget> {
        val nextHopCount = packet.hopCount + 1
        if (nextHopCount >= packet.maxHops) {
            Timber.d("MeshRouter: dropping packet — TTL exceeded")
            return emptyList()
        }
        if (packet.routeHistory.size >= PacketValidator.MAX_ROUTE_HISTORY) {
            Timber.d("MeshRouter: dropping packet — route history limit exceeded")
            return emptyList()
        }

        // Build forwarded packet — increment hopCount, append self to routeHistory
        val forwardedPacket = packet.copy(
            hopCount     = nextHopCount,
            routeHistory = packet.routeHistory + myDeviceId,
            senderId     = myDeviceId   // we are the current hop sender
        )

        return if (packet.isBroadcast) {
            // Flood to all connected peers except the one we received from
            connectedPeers.keys
                .filter { it != fromEndpointId }
                .map { ep -> ForwardTarget(ep, forwardedPacket) }
        } else {
            val nextHop = findNextHop(packet.finalDestId, connectedPeers)
            if (nextHop != null && nextHop != fromEndpointId) {
                listOf(ForwardTarget(nextHop, forwardedPacket))
            } else {
                // No known route — flood (excluding sender)
                connectedPeers.keys
                    .filter { it != fromEndpointId }
                    .also { eps ->
                        if (eps.isEmpty() && !isForMe) {
                            Timber.d("MeshRouter: no peers to forward to — packet will be lost")
                        }
                    }
                    .map { ep -> ForwardTarget(ep, forwardedPacket) }
            }
        }
    }

    private fun findNextHop(
        finalDestDeviceId: String,
        connectedPeers: Map<String, String>
    ): String? {
        // 1. Direct connection — best possible route
        val directEp = connectedPeers.entries.firstOrNull { it.value == finalDestDeviceId }?.key
        if (directEp != null) return directEp

        // 2. Routing table lookup (learned from previous packets)
        val tableHop = routingTable.getNextHop(finalDestDeviceId)
        if (tableHop != null && connectedPeers.containsKey(tableHop)) return tableHop

        return null
    }

    private fun findEndpointForDevice(deviceId: String): String? {
        // This requires NearbyRepositoryImpl to supply the connectedPeers map at call time.
        // MeshRouter.route() receives connectedPeers directly — used only for outgoing buildAndRoute.
        // For incoming CHAT packets, the session key is keyed by endpointId of the immediate sender.
        // Since CHAT is only used for direct connections (hopCount == 0), fromEndpointId == origin endpoint.
        return null  // Handled by the caller who has fromEndpointId context
    }

    private suspend fun enqueuePending(packet: MeshPacket, targetDeviceId: String, now: Long) {
        pendingMessageRepository.enqueue(
            PendingMessage(
                id             = packet.messageId,
                packetJson     = packet.toJson(),
                targetDeviceId = targetDeviceId,
                enqueuedAt     = packet.timestamp,
                expiresAt      = packet.timestamp + PENDING_TTL_MS
            )
        )
        Timber.i("MeshRouter: queued pending message ${packet.messageId} (expires in 48h from origin)")
    }

    // ── Phase 4B: Transmission lifecycle callbacks ────────────────────────────

    /**
     * Called when transport confirms successful transmission of a packet.
     * Safely deletes the pending message from [pendingMessageRepository] only after
     * transmission success is confirmed, and updates outgoing message status to [DeliveryStatus.SENT].
     */
    suspend fun onSendSuccess(messageId: String, originId: String) = withContext(Dispatchers.IO) {
        pendingMessageRepository.remove(messageId)
        if (originId == myDeviceId) {
            val now = System.currentTimeMillis()
            val msg = messageRepository.getMessageById(messageId)
            if (msg != null && msg.deliveryStatus != DeliveryStatus.DELIVERED) {
                val nextRetry = now + calculateNextRetryDelayMs(msg.retryCount)
                messageRepository.updateRetrySchedule(messageId, msg.retryCount, nextRetry)
                messageRepository.updateDeliveryStatus(messageId, DeliveryStatus.SENT)
            }
            Timber.d("MeshRouter: transmission confirmed for $messageId -> SENT")
        }
    }

    /**
     * Called when transport transmission fails.
     * Retains the pending message in Room (never deletes on failure).
     * If this was an unqueued outgoing routed message, queues it so it will be retried on next flush.
     */
    suspend fun onSendFailure(packet: MeshPacket) = withContext(Dispatchers.IO) {
        if (packet.originId == myDeviceId && packet.type == PacketType.ROUTED_CHAT) {
            enqueuePending(packet, packet.finalDestId, packet.timestamp)
            messageRepository.updateDeliveryStatus(packet.messageId, DeliveryStatus.QUEUED)
            Timber.w("MeshRouter: transport failed for ${packet.messageId}, retained in queue as QUEUED")
        }
    }
    // ── Phase 4C: ACK generation ──────────────────────────────────────────────

    /**
     * Generates an authenticated ACK [MeshPacket] for an accepted, decrypted, and persisted message.
     * Returns forward targets for immediate dispatch, or enqueues if no peers are currently connected.
     */
    suspend fun generateAckTargets(
        originalPacket: MeshPacket,
        connectedPeers: Map<String, String>
    ): List<ForwardTarget> = withContext(Dispatchers.IO) {
        // Broadcast and ACK packets never generate ACKs
        if (originalPacket.isBroadcast || originalPacket.type == PacketType.BROADCAST || originalPacket.type == PacketType.ACK) {
            return@withContext emptyList()
        }

        val senderDeviceId = originalPacket.originId
        val senderDevice = deviceRepository.getDeviceById(senderDeviceId)
        if (senderDevice == null) {
            Timber.w("MeshRouter: cannot send ACK to $senderDeviceId — public key unknown")
            return@withContext emptyList()
        }

        val now = System.currentTimeMillis()
        val ackPacketId = UUID.randomUUID().toString()
        val ackPayloadJson = JSONObject().apply {
            put("ackMessageId", originalPacket.messageId)
            put("ackTimestamp", now)
        }
        val ackPayloadBytes = ackPayloadJson.toString().toByteArray(Charsets.UTF_8)
        val aad = computeAckMetadataAad(
            originId    = myDeviceId,
            finalDestId = senderDeviceId,
            ackPacketId = ackPacketId,
            timestamp   = now
        )

        val encryptedAckContent = eciesService.encryptToBase64(ackPayloadBytes, senderDevice.publicKey, aad)

        val ackPacket = MeshPacket(
            senderId     = myDeviceId,
            receiverId   = senderDeviceId,
            content      = encryptedAckContent,
            timestamp    = now,
            type         = PacketType.ACK,
            messageId    = ackPacketId,
            originId     = myDeviceId,
            finalDestId  = senderDeviceId,
            hopCount     = 0,
            maxHops      = 7,
            senderName   = ""
        )

        seenMessageCache.markSeen(ackPacketId, myDeviceId)

        val forwardTargets = buildForwardTargets(
            packet         = ackPacket,
            fromEndpointId = null,
            connectedPeers = connectedPeers,
            isForMe        = false
        )

        if (forwardTargets.isEmpty()) {
            enqueuePending(ackPacket, senderDeviceId, now)
            Timber.i("MeshRouter: ACK for ${originalPacket.messageId} enqueued for future delivery")
            return@withContext emptyList()
        }

        Timber.i("MeshRouter: generated ACK for ${originalPacket.messageId} → $senderDeviceId")
        forwardTargets
    }

    // ── Phase 4D: Bounded Retry & Recovery ────────────────────────────────────

    /**
     * Calculates truncated exponential backoff delay with jitter.
     * Retry 1: ~30s, Retry 2: ~60s, Retry 3: ~120s, Retry 4: ~240s, Retry 5: ~480s (max 600s).
     */
    fun calculateNextRetryDelayMs(retryCount: Int): Long {
        val baseDelayMs = Message.BASE_RETRY_DELAY_MS
        val maxDelayMs = Message.MAX_RETRY_DELAY_MS
        val multiplier = 1L shl retryCount.coerceIn(0, 10)
        val exponential = baseDelayMs * multiplier
        val bounded = minOf(exponential, maxDelayMs)
        val jitter = (0..5000).random()
        return bounded + jitter
    }

    /**
     * Phase 4D: Scans for retry-eligible unresolved messages (SENT or QUEUED),
     * enforces the immutable expiration deadline and maximum retry count,
     * atomically claims eligible messages, and generates ForwardTargets for retransmission.
     */
    suspend fun retryEligibleMessages(
        connectedPeers: Map<String, String>
    ): List<ForwardTarget> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Mark expired or retry-exhausted messages as FAILED
        messageRepository.markFailedIfExpiredOrExhausted(now, Message.MAX_RETRY_COUNT)

        // 2. Query candidates eligible for retry
        val candidates = messageRepository.getEligibleRetries(myDeviceId, now, Message.MAX_RETRY_COUNT)
        if (candidates.isEmpty()) return@withContext emptyList()

        val forwardTargets = mutableListOf<ForwardTarget>()

        for (candidate in candidates) {
            // Expiration check: if now >= expiresAt or max retries reached, mark FAILED and skip
            if (now >= candidate.expiresAt || candidate.retryCount >= Message.MAX_RETRY_COUNT) {
                messageRepository.markFailed(candidate.id)
                pendingMessageRepository.remove(candidate.id)
                continue
            }

            // Calculate next retry delay for next attempt
            val nextDelay = calculateNextRetryDelayMs(candidate.retryCount + 1)
            val nextRetryAt = now + nextDelay

            // Atomic database claim: only proceed if this worker successfully claimed the increment
            val claimed = messageRepository.claimRetry(candidate.id, now, nextRetryAt, Message.MAX_RETRY_COUNT)
            if (!claimed) {
                Timber.d("MeshRouter: retry for ${candidate.id} was already claimed or status changed, skipping")
                continue
            }

            // Reconstruct the exact same packet preserving original messageId, timestamp, originId, finalDestId, and ciphertext
            val destDevice = deviceRepository.getDeviceById(candidate.receiverId)
            if (destDevice == null) {
                Timber.w("MeshRouter: cannot retry ${candidate.id} — recipient public key unknown")
                continue
            }

            val plaintext = String(candidate.ciphertext, Charsets.UTF_8)
            val aad = computeMetadataAad(
                originId    = candidate.senderId,
                finalDestId = candidate.receiverId,
                messageId   = candidate.id,
                timestamp   = candidate.timestamp
            )

            val innerPayloadJson = JSONObject().apply {
                put("text", plaintext)
                if (candidate.senderName.isNotEmpty()) {
                    put("senderName", candidate.senderName)
                }
            }
            val innerPayloadBytes = innerPayloadJson.toString().toByteArray(Charsets.UTF_8)
            val eciesContent = eciesService.encryptToBase64(innerPayloadBytes, destDevice.publicKey, aad)

            val packet = MeshPacket(
                senderId     = myDeviceId,
                receiverId   = candidate.receiverId,
                content      = eciesContent,
                timestamp    = candidate.timestamp,
                type         = PacketType.ROUTED_CHAT,
                messageId    = candidate.id,
                originId     = candidate.senderId,
                finalDestId  = candidate.receiverId,
                hopCount     = 0,
                maxHops      = 7,
                senderName   = ""
            )

            val targets = buildForwardTargets(
                packet         = packet,
                fromEndpointId = null,
                connectedPeers = connectedPeers,
                isForMe        = false
            )

            if (targets.isEmpty()) {
                enqueuePending(packet, candidate.receiverId, candidate.timestamp)
                messageRepository.updateDeliveryStatus(candidate.id, DeliveryStatus.QUEUED)
                Timber.i("MeshRouter: retried ${candidate.id} but no active route, retained as QUEUED")
            } else {
                forwardTargets.addAll(targets)
                Timber.i("MeshRouter: retrying message ${candidate.id} (attempt ${candidate.retryCount + 1}/${Message.MAX_RETRY_COUNT})")
            }
        }

        forwardTargets
    }
}

// ── Routing result ────────────────────────────────────────────────────────────

/** Represents what [NearbyRepositoryImpl] should do after [MeshRouter] processes a packet. */
sealed class RoutingResult {
    /** Silently discard — duplicate or TTL exceeded. */
    object Drop : RoutingResult()

    /** Normal outcome: optional local delivery + zero or more forward targets. */
    data class Processed(
        val localMessage: Message?,              // non-null = emit to UI
        val forwardTargets: List<ForwardTarget>  // list of Nearby sends to execute
    ) : RoutingResult()

    /** Enqueued for store-and-forward delivery. */
    data class Queued(val messageId: String) : RoutingResult()

    /** Destination unknown and no public key available — cannot encrypt or queue. */
    data class UnknownDestination(val deviceId: String) : RoutingResult()
}

data class ForwardTarget(
    val endpointId: String,
    val packet: MeshPacket
)

// ── Serialization helpers (extension functions) ────────────────────────────

private fun MeshPacket.toJson(): String {
    val sb = StringBuilder()
    sb.append("""{"messageId":"$messageId","senderId":"$senderId","receiverId":"$receiverId",""")
    sb.append(""""content":"${content.replace("\\", "\\\\").replace("\"", "\\\"")}",""")
    sb.append(""""timestamp":$timestamp,"type":"${type.name}",""")
    sb.append(""""originId":"$originId","finalDestId":"$finalDestId",""")
    sb.append(""""hopCount":$hopCount,"maxHops":$maxHops,""")
    val hist = routeHistory.joinToString(",") { "\"$it\"" }
    sb.append(""""routeHistory":[$hist]""")
    if (type != PacketType.ROUTED_CHAT && type != PacketType.ACK && senderName.isNotEmpty()) {
        sb.append(""", "senderName":"${senderName.replace("\\", "\\\\").replace("\"", "\\\"")}"}""")
    } else {
        sb.append("}")
    }
    return sb.toString()
}

private fun String.toMeshPacketOrNull(): MeshPacket? = try {
    val j = org.json.JSONObject(this)
    val histArray = j.optJSONArray("routeHistory")
    val history   = buildList {
        if (histArray != null) for (i in 0 until histArray.length()) add(histArray.getString(i))
    }
    val packet = MeshPacket(
        messageId    = j.optString("messageId", java.util.UUID.randomUUID().toString()),
        senderId     = j.getString("senderId"),
        receiverId   = j.getString("receiverId"),
        content      = j.getString("content"),
        timestamp    = j.getLong("timestamp"),
        type         = MeshPacket.PacketType.valueOf(j.optString("type", "CHAT")),
        originId     = j.optString("originId", j.getString("senderId")),
        finalDestId  = j.optString("finalDestId", j.getString("receiverId")),
        hopCount     = j.optInt("hopCount", 0),
        maxHops      = j.optInt("maxHops", 7),
        routeHistory = history,
        senderName   = j.optString("senderName", "")
    )
    if (PacketValidator.validatePacket(packet) is PacketValidator.ValidationResult.Invalid) null else packet
} catch (e: Exception) {
    null
}
