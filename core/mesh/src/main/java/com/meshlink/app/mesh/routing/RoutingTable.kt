package com.meshlink.app.mesh.routing

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory routing table: maps a known [destinationDeviceId] to the live [endpointId]
 * of the best next-hop Nearby peer that can reach it.
 *
 * Phase 4 uses a simple **direct-neighbor** routing model:
 *   • Every directly connected peer is a 1-hop route to itself.
 *   • Multi-hop routes are NOT stored here — unknown destinations use flooding.
 *
 * Routes have a TTL ([ROUTE_TTL_MS], default 60 s).  A route is considered stale once
 * [getNextHop] is called and the entry's age exceeds the TTL.  Routes are also explicitly
 * removed when a peer disconnects via [removeRoutesFor].
 *
 * Thread safety: all public methods are @Synchronized.
 */
@Singleton
class RoutingTable @Inject constructor() {

    companion object {
        /** A route entry older than this is treated as expired. */
        private const val ROUTE_TTL_MS = 60_000L
    }

    private data class RouteEntry(
        val nextHopEndpointId: String,
        val addedAt: Long = System.currentTimeMillis()
    )

    // destinationDeviceId → best next-hop entry
    private val table = HashMap<String, RouteEntry>()

    /**
     * Add or refresh a direct route.
     * Called by [NearbyRepositoryImpl] immediately after a successful ECDH handshake.
     *
     * @param destinationDeviceId Stable crypto deviceId of the destination.
     * @param nextHopEndpointId   Nearby endpointId of the direct neighbor that IS (or can reach) the destination.
     */
    @Synchronized
    fun addRoute(destinationDeviceId: String, nextHopEndpointId: String) {
        table[destinationDeviceId] = RouteEntry(nextHopEndpointId)
        Timber.d("RoutingTable: added route $destinationDeviceId → endpointId=$nextHopEndpointId")
    }

    /**
     * Returns the next-hop endpointId for [destinationDeviceId], or null if:
     *   • No route exists (use flooding).
     *   • The existing route has expired (stale — use flooding).
     */
    @Synchronized
    fun getNextHop(destinationDeviceId: String): String? {
        val entry = table[destinationDeviceId] ?: return null
        val age   = System.currentTimeMillis() - entry.addedAt
        if (age > ROUTE_TTL_MS) {
            table.remove(destinationDeviceId)
            Timber.d("RoutingTable: route to $destinationDeviceId expired after ${age}ms")
            return null
        }
        return entry.nextHopEndpointId
    }

    /**
     * Remove all routes whose next-hop is [endpointId].
     * Called when a Nearby endpoint disconnects so we don't forward into a dead link.
     */
    @Synchronized
    fun removeRoutesFor(endpointId: String) {
        val removed = table.entries.removeAll { it.value.nextHopEndpointId == endpointId }
        if (removed) Timber.d("RoutingTable: removed routes via endpointId=$endpointId")
    }

    /** Returns a snapshot of all known destinations (for debug/logging). */
    @Synchronized
    fun knownDestinations(): Set<String> = table.keys.toSet()
}
