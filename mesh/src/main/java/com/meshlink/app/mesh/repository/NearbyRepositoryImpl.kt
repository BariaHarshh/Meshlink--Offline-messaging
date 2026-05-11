package com.meshlink.app.mesh.repository

import android.util.Base64
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.HandshakeManager
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.battery.AdaptiveScanController
import com.meshlink.app.mesh.routing.ForwardTarget
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.routing.RoutingResult
import com.meshlink.app.mesh.routing.RoutingTable
import com.meshlink.app.mesh.util.toBytes
import com.meshlink.app.mesh.util.toMeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class NearbyRepositoryImpl @Inject constructor(
    private val connectionsClient: ConnectionsClient,
    @Named("localDeviceId") private val localDeviceId: String,
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val handshakeManager: HandshakeManager,
    private val encryptionService: EncryptionService,
    private val sessionKeyStore: SessionKeyStore,
    // Phase 4
    private val meshRouter: MeshRouter,
    private val routingTable: RoutingTable,
    // Phase 5: battery
    private val adaptiveScanController: AdaptiveScanController,
    // User identity
    private val userProfileManager: UserProfileManager
) : NearbyRepository {

    companion object {
        private const val SERVICE_ID = "com.meshlink.app"
        private val     STRATEGY     = Strategy.P2P_CLUSTER
    }

    @Volatile private var scanningPaused = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── State ─────────────────────────────────────────────────────────────────

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    override val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    @Volatile private var isAdvertising = false
    @Volatile private var isDiscovering = false

    /** deviceName → current live endpointId (names are stable, endpointIds change per reconnect) */
    private val nameToEndpointId = ConcurrentHashMap<String, String>()

    /**
     * Volatile Nearby endpointId → stable peer crypto deviceId.
     * Populated after ECDH handshake. Cleared on disconnect.
     */
    private val endpointIdToDeviceId = ConcurrentHashMap<String, String>()

    // ── Nearby callbacks ──────────────────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Timber.d("Endpoint found: $endpointId name=${info.endpointName}")
            adaptiveScanController.onDeviceFound()  // Phase 5: reset to fast scan
            nameToEndpointId[info.endpointName] = endpointId
            _discoveredDevices.update { current ->
                val filtered = current.filter { it.name != info.endpointName }
                filtered + DiscoveredDevice(endpointId, info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Timber.d("Endpoint lost: $endpointId")
            _discoveredDevices.update { it.filter { d -> d.endpointId != endpointId } }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Timber.d("Connection initiated: $endpointId (${info.endpointName}), auto-accepting")
            _connectionStates.update { it + (endpointId to ConnectionState.CONNECTING) }
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e -> Timber.e(e, "acceptConnection failed for $endpointId") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Timber.d("Nearby link established to $endpointId — starting ECDH handshake")
                _connectionStates.update { it + (endpointId to ConnectionState.HANDSHAKING) }
                sendHandshakePacket(endpointId)
                scope.launch {
                    delay(500)
                    if (!isDiscovering) startDiscoveryInternal()
                }
            } else {
                Timber.w("Connection to $endpointId failed: ${result.status.statusMessage}")
                _connectionStates.update { it + (endpointId to ConnectionState.DISCONNECTED) }
                scheduleRestart()
            }
        }

        override fun onDisconnected(endpointId: String) {
            Timber.d("Disconnected from $endpointId")
            _connectionStates.update { it + (endpointId to ConnectionState.DISCONNECTED) }
            handshakeManager.clearSession(endpointId)
            endpointIdToDeviceId.remove(endpointId)
            routingTable.removeRoutesFor(endpointId)  // Phase 4: invalidate stale routes
            isAdvertising = false
            isDiscovering = false
            scheduleRestart()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes  = payload.asBytes() ?: return
            val packet = bytes.toMeshPacket() ?: return

            when (packet.type) {
                MeshPacket.PacketType.HANDSHAKE    -> handleHandshake(endpointId, packet)
                // All message types (CHAT, ROUTED_CHAT, BROADCAST) go through MeshRouter
                MeshPacket.PacketType.CHAT,
                MeshPacket.PacketType.ROUTED_CHAT,
                MeshPacket.PacketType.BROADCAST    -> handleRoutedPacket(endpointId, packet)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Timber.v("Payload delivered to $endpointId payloadId=${update.payloadId}")
            }
        }
    }

    // ── Handshake ─────────────────────────────────────────────────────────────

    private fun sendHandshakePacket(endpointId: String) {
        val packet  = handshakeManager.createHandshakePacket(localDeviceId, endpointId)
        val payload = Payload.fromBytes(packet.toBytes())
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener { Timber.d("Handshake sent to $endpointId") }
            .addOnFailureListener { e -> Timber.e(e, "sendHandshake to $endpointId failed") }
    }

    private fun handleHandshake(endpointId: String, packet: MeshPacket) {
        Timber.d("HANDSHAKE received from $endpointId (peerId=${packet.senderId})")
        scope.launch(Dispatchers.IO) {
            val success = handshakeManager.processHandshake(endpointId, packet.content)
            if (success) {
                // 1. Record endpointId → stable peerDeviceId mapping
                endpointIdToDeviceId[endpointId] = packet.senderId
                Timber.d("Identity mapped: $endpointId → ${packet.senderId}")

                // 2. Update routing table: direct 1-hop route
                routingTable.addRoute(packet.senderId, endpointId)

                // 3. Mark CONNECTED — UI send button activates
                _connectionStates.update { it + (endpointId to ConnectionState.CONNECTED) }

                // 4. Persist peer identity for future ECIES encryption
                val peerPubKeyBytes = Base64.decode(packet.content, Base64.NO_WRAP)
                deviceRepository.upsertDevice(
                    KnownDevice(
                        deviceId    = packet.senderId,
                        displayName = nameToEndpointId.entries
                            .firstOrNull { it.value == endpointId }?.key ?: packet.senderId,
                        publicKey   = peerPubKeyBytes,
                        lastSeen    = System.currentTimeMillis()
                    )
                )

                // 5. Phase 4: flush any queued messages for newly reachable devices
                val peers   = getConnectedPeers()
                val pending = meshRouter.flushPendingQueue(peers)
                pending.forEach { target -> dispatchToNearby(target) }

                Timber.i("Session CONNECTED with $endpointId — E2E active, ${pending.size} pending flushed")
            } else {
                Timber.e("Handshake failed for $endpointId — disconnecting")
                disconnect(endpointId)
            }
        }
    }

    // ── Phase 4: incoming routed packet ──────────────────────────────────────

    /**
     * All CHAT / ROUTED_CHAT / BROADCAST packets come here.
     * MeshRouter decides: deliver locally, forward, or drop.
     */
    private fun handleRoutedPacket(fromEndpointId: String, packet: MeshPacket) {
        scope.launch(Dispatchers.IO) {
            // Intercept direct CHAT packets (Phase 3 path) — need session key context
            val effectivePacket = if (packet.type == MeshPacket.PacketType.CHAT &&
                packet.hopCount == 0 && packet.finalDestId == localDeviceId) {
                // Decrypt inline here since we need fromEndpointId for session key lookup
                handleDirectChat(fromEndpointId, packet)
                return@launch
            } else {
                packet
            }

            val peers  = getConnectedPeers()
            val result = meshRouter.route(fromEndpointId, effectivePacket, peers)

            when (result) {
                is RoutingResult.Drop     -> { /* already logged */ }
                is RoutingResult.Processed -> {
                    result.localMessage?.let { msg ->
                        _incomingPackets.emit(
                            effectivePacket.copy(content = String(msg.ciphertext, Charsets.UTF_8))
                        )
                    }
                    result.forwardTargets.forEach { target -> dispatchToNearby(target) }
                }
                else -> { /* Queued / UnknownDestination don't happen for incoming */ }
            }
        }
    }

    /**
     * Phase 3 direct CHAT path — uses AES-256-GCM session key keyed by endpointId.
     * Called only for hopCount == 0 CHAT packets addressed to this device.
     */
    private suspend fun handleDirectChat(fromEndpointId: String, packet: MeshPacket) {
        if (!sessionKeyStore.isNewMessage(packet.messageId)) {
            Timber.w("REPLAY: dropping duplicate messageId=${packet.messageId}")
            return
        }

        val sessionKey = sessionKeyStore.getSessionKey(fromEndpointId) ?: run {
            Timber.w("No session key for $fromEndpointId — dropping")
            return
        }

        val encryptedBytes = Base64.decode(packet.content, Base64.NO_WRAP)
        val plaintext      = encryptionService.decrypt(encryptedBytes, sessionKey) ?: run {
            Timber.w("AES-GCM decrypt failed from $fromEndpointId")
            return
        }

        val plaintextStr = String(plaintext, Charsets.UTF_8)
        messageRepository.insertMessage(
            com.meshlink.app.domain.model.Message(
                id         = packet.messageId,
                senderId   = packet.originId,
                receiverId = localDeviceId,
                ciphertext = plaintext,
                timestamp  = packet.timestamp,
                delivered  = true,
                senderName = packet.senderName
            )
        )

        // Update peer's display name from the message if provided
        if (packet.senderName.isNotEmpty()) {
            val existingDevice = deviceRepository.getDeviceById(packet.originId)
            if (existingDevice != null && existingDevice.displayName != packet.senderName) {
                deviceRepository.updateDisplayName(packet.originId, packet.senderName)
            }
        }

        _incomingPackets.emit(packet.copy(content = plaintextStr))
    }

    // ── Public API ────────────────────────────────────────────────────────────

    override fun startAdvertisingAndDiscovery() {
        startAdvertisingInternal()
        startDiscoveryInternal()
    }

    override fun stopAdvertisingAndDiscovery() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        isAdvertising = false
        isDiscovering = false
        _discoveredDevices.value = emptyList()
    }

    override fun restartAdvertisingOnly() {
        connectionsClient.stopAdvertising()
        isAdvertising = false
        startAdvertisingInternal()
    }

    override fun requestConnection(endpointId: String) {
        _connectionStates.update { it + (endpointId to ConnectionState.CONNECTING) }
        connectionsClient.requestConnection(userProfileManager.getDisplayName(), endpointId, connectionLifecycleCallback)
            .addOnSuccessListener { Timber.d("Connection requested to $endpointId") }
            .addOnFailureListener { e ->
                Timber.e(e, "requestConnection to $endpointId failed")
                _connectionStates.update { it + (endpointId to ConnectionState.DISCONNECTED) }
            }
    }

    /**
     * Phase 3 direct send — still used internally by [MeshRouter] results for CHAT packets.
     * External callers (ChatViewModel) should use [routeToDevice] instead.
     */
    override fun sendPacket(endpointId: String, packet: MeshPacket) {
        val state = _connectionStates.value[endpointId]
        if (state != ConnectionState.CONNECTED) {
            Timber.w("sendPacket: dropping — state=$state for $endpointId (need CONNECTED)")
            return
        }
        val peerDeviceId = endpointIdToDeviceId[endpointId] ?: run {
            Timber.e("sendPacket: no peerDeviceId for $endpointId despite CONNECTED state")
            return
        }
        scope.launch(Dispatchers.IO) {
            val sessionKey = sessionKeyStore.getSessionKey(endpointId) ?: return@launch
            val plaintextBytes   = packet.content.toByteArray(Charsets.UTF_8)
            val encryptedBytes   = encryptionService.encrypt(plaintextBytes, sessionKey)
            val encryptedContent = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val localDisplayName = userProfileManager.getDisplayName()
            val securePacket     = packet.copy(
                content     = encryptedContent,
                originId    = localDeviceId,
                finalDestId = peerDeviceId,
                senderName  = localDisplayName
            )
            messageRepository.insertMessage(
                com.meshlink.app.domain.model.Message(
                    id         = securePacket.messageId,
                    senderId   = localDeviceId,
                    receiverId = peerDeviceId,
                    ciphertext = plaintextBytes,
                    timestamp  = securePacket.timestamp,
                    delivered  = false,
                    senderName = localDisplayName
                )
            )
            val msgId = securePacket.messageId
            val payload = Payload.fromBytes(securePacket.toBytes())
            connectionsClient.sendPayload(endpointId, payload)
                .addOnFailureListener { e ->
                    Timber.e(e, "sendPayload to $endpointId failed")
                    _connectionStates.update { it + (endpointId to ConnectionState.DISCONNECTED) }
                    isAdvertising = false; isDiscovering = false
                    scheduleRestart()
                }
        }
    }

    /**
     * Phase 4 entry point — handles direct CHAT and multi-hop ROUTED_CHAT automatically.
     * ChatViewModel calls this instead of [sendPacket].
     */
    override fun routeToDevice(finalDestDeviceId: String, packet: MeshPacket) {
        scope.launch(Dispatchers.IO) {
            val peers  = getConnectedPeers()
            val result = meshRouter.buildAndRoute(
                finalDestDeviceId = finalDestDeviceId,
                plaintext         = packet.content,
                connectedPeers    = peers,
                timestamp         = packet.timestamp
            )
            when (result) {
                is RoutingResult.Processed -> result.forwardTargets.forEach { dispatchToNearby(it) }
                is RoutingResult.Queued    -> Timber.i("Message queued for $finalDestDeviceId")
                is RoutingResult.UnknownDestination -> {
                    // Persist locally so the message appears in chat UI with a "queued" icon.
                    // It will be delivered once the peer connects and flushPendingQueue runs.
                    val localDisplayName = userProfileManager.getDisplayName()
                    messageRepository.insertMessage(
                        com.meshlink.app.domain.model.Message(
                            id         = packet.messageId,
                            senderId   = localDeviceId,
                            receiverId = finalDestDeviceId,
                            ciphertext = packet.content.toByteArray(Charsets.UTF_8),
                            timestamp  = packet.timestamp,
                            delivered  = false,
                            senderName = localDisplayName
                        )
                    )
                    Timber.w("Cannot route to $finalDestDeviceId — public key unknown, saved locally")
                }
                is RoutingResult.Drop      -> { /* no-op */ }
            }
        }
    }

    override fun sendBroadcast(content: String) {
        scope.launch(Dispatchers.IO) {
            val peers  = getConnectedPeers()
            val result = meshRouter.buildBroadcast(content, peers)
            if (result is RoutingResult.Processed) {
                result.forwardTargets.forEach { dispatchToNearby(it) }
            }
        }
    }

    override fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        handshakeManager.clearSession(endpointId)
        endpointIdToDeviceId.remove(endpointId)
        routingTable.removeRoutesFor(endpointId)
        _connectionStates.update { it + (endpointId to ConnectionState.DISCONNECTED) }
    }

    override fun currentEndpointForName(deviceName: String): String? =
        nameToEndpointId[deviceName]

    override fun peerDeviceIdForEndpoint(endpointId: String): String? =
        endpointIdToDeviceId[endpointId]

    override fun getConnectedPeers(): Map<String, String> {
        val connectedStates = _connectionStates.value
            .filter { it.value == ConnectionState.CONNECTED }
            .keys
        return connectedStates
            .mapNotNull { ep -> endpointIdToDeviceId[ep]?.let { deviceId -> ep to deviceId } }
            .toMap()
    }

    // ── Transport dispatch ────────────────────────────────────────────────────

    /**
     * Sends a [ForwardTarget]'s packet over Nearby Connections.
     * No encryption is applied here — the packet's content is already in its final wire form
     * (AES-GCM or ECIES) as set by [MeshRouter].
     */
    private fun dispatchToNearby(target: ForwardTarget) {
        val (endpointId, packet) = target
        val state = _connectionStates.value[endpointId]
        if (state != ConnectionState.CONNECTED) {
            Timber.w("dispatchToNearby: skipping $endpointId — state=$state")
            return
        }
        val payload = Payload.fromBytes(packet.toBytes())
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Timber.d("Forwarded ${packet.type} msgId=${packet.messageId} hop=${packet.hopCount} → $endpointId")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "dispatchToNearby failed for $endpointId")
            }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Find the best endpoint to route an ACK back to [originDeviceId].
     * Prefers a direct connection; falls back to the endpoint we received the message from.
     */
    private fun findAckRoute(
        originDeviceId: String,
        connectedPeers: Map<String, String>,
        fromEndpointId: String
    ): String? {
        // Direct connection to the originator is best
        val direct = connectedPeers.entries.firstOrNull { it.value == originDeviceId }?.key
        if (direct != null) return direct
        // Fall back to the hop we received from (reverse path)
        return fromEndpointId
    }

    override fun pauseScanning() {
        if (scanningPaused) return
        scanningPaused = true
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        isAdvertising = false
        isDiscovering = false
        Timber.i("AdaptiveScan: scanning PAUSED (battery low or doze)")
    }

    override fun resumeScanning() {
        if (!scanningPaused) return
        scanningPaused = false
        adaptiveScanController.onUserActive()
        startAdvertisingInternal()
        startDiscoveryInternal()
        Timber.i("AdaptiveScan: scanning RESUMED")
    }

    private fun scheduleRestart() {
        scope.launch {
            adaptiveScanController.onRestartComplete()  // update backoff
            val delay = adaptiveScanController.nextRestartDelayMs
            Timber.d("scheduleRestart: waiting ${delay}ms before next scan cycle")
            delay(delay)
            if (!scanningPaused) {
                startAdvertisingInternal()
                startDiscoveryInternal()
            }
        }
    }

    private fun startAdvertisingInternal() {
        if (isAdvertising) return
        // Read fresh display name each time so profile changes take effect
        val currentName = userProfileManager.getDisplayName()
        connectionsClient.startAdvertising(
            currentName, SERVICE_ID, connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Timber.d("Advertising started as '$currentName'")
            isAdvertising = true
        }.addOnFailureListener { e ->
            Timber.e(e, "startAdvertising failed")
            isAdvertising = false
        }
    }

    private fun startDiscoveryInternal() {
        if (isDiscovering) return
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Timber.d("Discovery started")
            isDiscovering = true
        }.addOnFailureListener { e ->
            Timber.e(e, "startDiscovery failed")
            isDiscovering = false
        }
    }
}
