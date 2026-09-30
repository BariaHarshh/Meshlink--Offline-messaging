package com.meshlink.app.mesh.routing

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU cache of recently seen message identifiers used to prevent routing loops and replay attacks.
 *
 * Combines originId and messageId along with timestamp TTL.
 *
 * Thread safety: all public methods are @Synchronized.
 *
 * @param capacity Maximum number of messageIds to track (default 2,000).
 * @param ttlMs    Time-to-live per entry in milliseconds (default 10 minutes).
 */
@Singleton
class SeenMessageCache @Inject constructor() {

    companion object {
        private const val DEFAULT_CAPACITY = 2_000
        private const val DEFAULT_TTL_MS   = 10 * 60 * 1_000L  // 10 minutes
    }

    private data class Entry(val seenAt: Long)

    // accessOrder = true -> LRU eviction on capacity overflow
    private val cache = object : LinkedHashMap<String, Entry>(
        DEFAULT_CAPACITY + 1, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Entry>): Boolean =
            size > DEFAULT_CAPACITY
    }

    private fun buildKey(messageId: String, originId: String): String =
        if (originId.isNotEmpty()) "$originId:$messageId" else messageId

    /**
     * Returns true if this [messageId] (and optional [originId]) was seen within [DEFAULT_TTL_MS].
     * Expired entries are treated as unseen.
     */
    @Synchronized
    fun isAlreadySeen(messageId: String, originId: String = ""): Boolean {
        val key = buildKey(messageId, originId)
        val entry = cache[key] ?: return false
        val age = System.currentTimeMillis() - entry.seenAt
        if (age > DEFAULT_TTL_MS) {
            cache.remove(key)
            return false
        }
        return true
    }

    /**
     * Records [messageId] (and optional [originId]) as seen right now.
     */
    @Synchronized
    fun markSeen(messageId: String, originId: String = "") {
        val key = buildKey(messageId, originId)
        cache[key] = Entry(seenAt = System.currentTimeMillis())
        Timber.v("SeenCache: marked seen $key (cache size=${cache.size})")
    }
}
