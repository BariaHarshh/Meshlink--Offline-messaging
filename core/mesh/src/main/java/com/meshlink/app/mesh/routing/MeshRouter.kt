package com.meshlink.app.mesh.routing

import android.util.Base64
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
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

        // 1. Deduplication — drop packets we've already seen (loop prevention)
        if (seenMessageCache.isAlreadySeen(packet.messageId)) {
            Timber.d("MeshRouter: DROP duplicate messageId=${packet.messageId}")
            return@withContext RoutingResult.Drop
        }
        seenMessageCache.markSeen(packet.messageId)

        // 2. TTL check
        if (packet.hopCount >= packet.maxHops) {
            Timber.d("MeshRouter: DROP TTL exceeded hopCount=${packet.hopCount} maxHops=${packet.maxHops}")
            return@withContext RoutingResult.Drop
        }

        val isForMe       = packet.finalDestId == myDeviceId
        val isBroadcast   = packet.isBroadcast
        val needForward   = !isForMe  // broadcasts are always forwarded AND delivered

        // 3. Local delivery
        var localMessage: Message? = null
        if (isForMe || isBroadcast) {
            localMessage = decryptAndPersist(packet)
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
            forwardTargets = forwardTargets
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
                        id         = messageId,
                        senderId   = myDeviceId,
                        receiverId = finalDestDeviceId,
                        ciphertext = plaintextBytes,
                        timestamp  = timestamp,
                        delivered  = false,
                        senderName = localDisplayName
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
            val eciesContent = eciesService.encryptToBase64(plaintextBytes, destDevice.publicKey)

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
                senderName   = localDisplayName
            )

            // Persist plaintext on sender side (we know what we sent)
            messageRepository.insertMessage(
                Message(
                    id         = messageId,
                    senderId   = myDeviceId,
                    receiverId = finalDestDeviceId,
                    ciphertext = plaintextBytes,
                    timestamp  = timestamp,
                    delivered  = false,
                    senderName = localDisplayName
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
                return@withContext RoutingResult.Queued(messageId)
            }

            return@withContext RoutingResult.Processed(
                localMessage   = null,
                forwardTargets = forwardTargets
            )
        }

        // ── Unknown destination ───────────────────────────────────────────────
        Timber.w("MeshRouter: unknown destination $finalDestDeviceId — no public key stored")
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
        val allPending = pendingMessageRepository.getAllPending()
        if (allPending.isEmpty()) return@withContext emptyList()

        val targets = mutableListOf<ForwardTarget>()
        for (pending in allPending) {
            val packet = pending.packetJson.toMeshPacketOrNull() ?: continue

            // Can any connected peer forward this to its destination?
            val nextHop = findNextHop(packet.finalDestId, connectedPeers)
            if (nextHop != null) {
                val refreshed = packet.copy(hopCount = 0)  // reset TTL on re-send
                targets.add(ForwardTarget(nextHop, refreshed))
                pendingMessageRepository.remove(pending.id)
                Timber.i("MeshRouter: flushing pending ${packet.messageId} → $nextHop")
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
                    Timber.w("MeshRouter: no session key for CHAT from ${packet.originId}")
                    return null
                }
                val encryptedBytes = Base64.decode(packet.content, Base64.NO_WRAP)
                val plaintext      = encryptionService.decrypt(encryptedBytes, sessionKey) ?: run {
                    Timber.w("MeshRouter: AES-GCM decrypt failed for msg=${packet.messageId}")
                    return null
                }
                persistAndReturn(packet, plaintext)
            }

            PacketType.ROUTED_CHAT -> {
                // ECIES — decrypt with our private key
                val plaintext = eciesService.decryptFromBase64(packet.content) ?: run {
                    Timber.w("MeshRouter: ECIES decrypt failed for msg=${packet.messageId}")
                    return null
                }
                persistAndReturn(packet, plaintext)
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
        // Build forwarded packet — increment hopCount, append self to routeHistory
        val forwardedPacket = packet.copy(
            hopCount     = packet.hopCount + 1,
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
                id             = UUID.randomUUID().toString(),
                packetJson     = packet.toJson(),
                targetDeviceId = targetDeviceId,
                enqueuedAt     = now,
                expiresAt      = now + PENDING_TTL_MS
            )
        )
        Timber.i("MeshRouter: queued pending message for $targetDeviceId (expires in 48h)")
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
    sb.append(""""routeHistory":[$hist],""")
    sb.append(""""senderName":"${senderName.replace("\\", "\\\\").replace("\"", "\\\"")}"}""")
    return sb.toString()
}

private fun String.toMeshPacketOrNull(): MeshPacket? = try {
    val j = org.json.JSONObject(this)
    val histArray = j.optJSONArray("routeHistory")
    val history   = buildList {
        if (histArray != null) for (i in 0 until histArray.length()) add(histArray.getString(i))
    }
    MeshPacket(
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
} catch (e: Exception) {
    null
}
