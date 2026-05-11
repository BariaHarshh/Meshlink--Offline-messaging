package com.meshlink.app.crypto.session

import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store for per-endpoint AES-256 session keys and replay-prevention state.
 *
 * Session keys are NEVER persisted to disk. They exist only for the duration
 * of a Nearby Connections session and are cleared on disconnect.
 *
 * Replay prevention: keeps the last [MAX_MESSAGE_IDS] message UUIDs seen.
 * Any packet whose messageId was already recorded is rejected.
 */
@Singleton
class SessionKeyStore @Inject constructor() {

    companion object {
        private const val MAX_MESSAGE_IDS = 2_000
    }

    // endpointId → AES-256 SecretKey (in-memory only)
    private val sessionKeys = ConcurrentHashMap<String, SecretKey>()

    // Bounded LRU set of recently-seen messageIds for replay prevention
    private val recentMessageIds: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size >= MAX_MESSAGE_IDS) {
                    // Evict oldest entry
                    iterator().apply { next(); remove() }
                }
                return super.add(element)
            }
        }
    )

    // ── Session key lifecycle ─────────────────────────────────────────────────

    fun storeSessionKey(endpointId: String, key: SecretKey) {
        sessionKeys[endpointId] = key
        Timber.d("SessionKeyStore: stored session key for $endpointId")
    }

    fun getSessionKey(endpointId: String): SecretKey? = sessionKeys[endpointId]

    fun isHandshakeComplete(endpointId: String): Boolean = sessionKeys.containsKey(endpointId)

    /**
     * Clears the session key for [endpointId].
     * Called on disconnect — ensures the key cannot be reused even if an
     * attacker gains memory access after the session ends.
     */
    fun clearSession(endpointId: String) {
        sessionKeys.remove(endpointId)
        Timber.d("SessionKeyStore: cleared session for $endpointId")
    }

    // ── Replay prevention ─────────────────────────────────────────────────────

    /**
     * Returns true and records [messageId] if it is new (first time seen).
     * Returns false (replay detected) if [messageId] was already recorded.
     */
    fun isNewMessage(messageId: String): Boolean {
        return recentMessageIds.add(messageId)   // add() returns false if already present
    }
}
