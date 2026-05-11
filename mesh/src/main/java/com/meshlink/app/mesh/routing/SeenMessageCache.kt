package com.meshlink.app.mesh.routing

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU cache of recently seen messageIds used to prevent routing loops.
 *
 * When a packet arrives or is about to be forwarded, the router calls [markSeen].
 * If [isAlreadySeen] returns true the packet is silently dropped.
 *
 * Implementation:
 *   A [LinkedHashMap] in access-order mode evicts the oldest entry once the capacity
 *   limit is reached, bounding memory usage regardless of network traffic volume.
 *   Each entry also carries an expiry timestamp; entries older than [ttlMs] are treated
 *   as unseen (allows the same messageId to be retransmitted after a long offline period
 *   without causing a false-positive loop-drop).
 *
 * Thread safety: all public methods are @Synchronized — called from Nearby callbacks on
 * arbitrary threads.
 *
 * @param capacity Maximum number of messageIds to track (default 1,000).
 * @param ttlMs    Time-to-live per entry in milliseconds (default 10 minutes).
 */
@Singleton
class SeenMessageCache @Inject constructor() {

    companion object {
        private const val DEFAULT_CAPACITY = 1_000
        private const val DEFAULT_TTL_MS   = 10 * 60 * 1_000L  // 10 minutes
    }

    private data class Entry(val seenAt: Long)

    // accessOrder = false → insertion-order eviction on capacity overflow
    private val cache = object : LinkedHashMap<String, Entry>(
        DEFAULT_CAPACITY + 1, 0.75f, false
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Entry>): Boolean =
            size > DEFAULT_CAPACITY
    }

    /**
     * Returns true if this [messageId] was seen within the last [DEFAULT_TTL_MS] milliseconds.
     * Expired entries are treated as unseen.
     */
    @Synchronized
    fun isAlreadySeen(messageId: String): Boolean {
        val entry = cache[messageId] ?: return false
        val age   = System.currentTimeMillis() - entry.seenAt
        if (age > DEFAULT_TTL_MS) {
            cache.remove(messageId)
            return false
        }
        return true
    }

    /**
     * Records [messageId] as seen right now.
     * Call this before forwarding — if the packet is dropped later, no harm done.
     */
    @Synchronized
    fun markSeen(messageId: String) {
        cache[messageId] = Entry(seenAt = System.currentTimeMillis())
        Timber.v("SeenCache: marked seen $messageId (cache size=${cache.size})")
    }
}
