package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.domain.model.MeshPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface NearbyRepository {

    /** Live list of endpoints discovered via Nearby advertising/discovery. */
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>

    /** Per-endpoint connection state keyed by endpointId. */
    val connectionStates: StateFlow<Map<String, ConnectionState>>

    /** Emits every packet received from any connected endpoint. */
    val incomingPackets: SharedFlow<MeshPacket>

    fun startAdvertisingAndDiscovery()

    fun stopAdvertisingAndDiscovery()

    /**
     * Restart only advertising (not discovery) so updated display name takes effect
     * without disconnecting existing peers.
     */
    fun restartAdvertisingOnly()

    /** Initiate an outbound connection to [endpointId]. Auto-accepted on both sides. */
    fun requestConnection(endpointId: String)

    fun sendPacket(endpointId: String, packet: MeshPacket)

    fun disconnect(endpointId: String)

    /**
     * Returns the current live endpointId for a known device name, or null.
     * Endpoint IDs change on every reconnect; device names stay constant.
     */
    fun currentEndpointForName(deviceName: String): String?

    /**
     * Returns the stable crypto deviceId (SHA-256 of peer's public key) for a given
     * endpointId, or null if the ECDH handshake has not yet completed.
     *
     * This is the ONLY identifier that must be used as senderId/receiverId in Room.
     * Nearby's endpointId is volatile — it changes on every reconnect.
     */
    fun peerDeviceIdForEndpoint(endpointId: String): String?

    /**
     * Returns all currently CONNECTED endpoints as a map of endpointId → stable peerDeviceId.
     * Only entries whose connection state is CONNECTED (post-handshake) are included.
     * Used by [MeshRouter] to decide forwarding targets.
     */
    fun getConnectedPeers(): Map<String, String>

    /**
     * Phase 4 entry point for sending a message.
     *
     * Handles both direct and multi-hop cases:
     *  • If [finalDestDeviceId] is directly connected (session key exists) → CHAT (AES-256-GCM)
     *  • If [finalDestDeviceId] is reachable via a relay (public key known) → ROUTED_CHAT (ECIES)
     *  • If no route available → enqueue in PendingMessageRepository for later delivery
     *
     * Unlike [sendPacket] (which is a direct Nearby send), this method contains routing logic.
     *
     * @param finalDestDeviceId Stable SHA-256 deviceId of the intended recipient.
     * @param packet MeshPacket with content = plaintext. Encryption is applied internally.
     */
    fun routeToDevice(finalDestDeviceId: String, packet: MeshPacket)

    /**
     * Pause Nearby advertising + discovery (e.g. battery low, screen off in idle mode).
     * Does NOT stop the foreground service — the service stays alive so it can resume quickly.
     */
    fun pauseScanning()

    /**
     * Resume advertising + discovery after a [pauseScanning] call.
     * Always resets to fast-scan mode (adaptive backoff reset).
     */
    fun resumeScanning()

    /**
     * Send an emergency broadcast to ALL currently connected peers and their reachable neighbors.
     * The message is delivered locally and flooded with [MeshPacket.PacketType.BROADCAST].
     *
     * @param content Plaintext broadcast message content.
     */
    fun sendBroadcast(content: String)
}
