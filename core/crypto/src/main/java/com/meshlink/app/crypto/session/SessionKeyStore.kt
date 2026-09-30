package com.meshlink.app.crypto.session

import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store for per-endpoint AES-256 session keys, authenticated peer identities,
 * and replay-prevention state.
 *
 * Session keys and ephemeral secrets are NEVER persisted to disk. They exist only for the
 * lifetime of an active Nearby Connections session and are wiped on disconnect.
 *
 * Replay protection tracks recent (senderId, messageId) tuples within an LRU cache.
 */
@Singleton
class SessionKeyStore @Inject constructor() {

    companion object {
        private const val MAX_MESSAGE_IDS = 2_000
    }

    // endpointId → AES-256 SecretKey (in-memory only)
    private val sessionKeys = ConcurrentHashMap<String, SecretKey>()

    // endpointId → authenticated peer deviceId
    private val sessionPeers = ConcurrentHashMap<String, String>()

    // endpointId → authenticated peer public key
    private val sessionPeerPubKeys = ConcurrentHashMap<String, ByteArray>()

    // Bounded LRU set of recently-seen message identifier keys for replay prevention
    private val recentMessageIds: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashMap<String, Long>(MAX_MESSAGE_IDS + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
                size > MAX_MESSAGE_IDS
        }.keySet()
    )

    // ── Session key lifecycle ─────────────────────────────────────────────────

    /**
     * Stores an established authenticated session.
     */
    fun storeSession(endpointId: String, peerDeviceId: String, peerPublicKey: ByteArray, key: SecretKey) {
        sessionKeys[endpointId] = key
        sessionPeers[endpointId] = peerDeviceId
        sessionPeerPubKeys[endpointId] = peerPublicKey
        Timber.d("SessionKeyStore: session established for endpoint=$endpointId peerDevice=$peerDeviceId")
    }

    fun storeSessionKey(endpointId: String, key: SecretKey) {
        sessionKeys[endpointId] = key
        Timber.d("SessionKeyStore: stored session key for endpoint=$endpointId")
    }

    fun getSessionKey(endpointId: String): SecretKey? = sessionKeys[endpointId]

    fun getAuthenticatedPeerDeviceId(endpointId: String): String? = sessionPeers[endpointId]

    fun getAuthenticatedPeerPublicKey(endpointId: String): ByteArray? = sessionPeerPubKeys[endpointId]

    fun isHandshakeComplete(endpointId: String): Boolean = sessionKeys.containsKey(endpointId)

    /**
     * Clears all session state for [endpointId].
     * Called on disconnect to ensure forward secrecy and memory hygiene.
     */
    fun clearSession(endpointId: String) {
        sessionKeys.remove(endpointId)
        sessionPeers.remove(endpointId)
        sessionPeerPubKeys.remove(endpointId)
        Timber.d("SessionKeyStore: cleared session for endpoint=$endpointId")
    }

    // ── Replay prevention ─────────────────────────────────────────────────────

    /**
     * Returns true and records [messageId] if it is new (first time seen).
     * Binds [senderId] to the messageId when available for sender-scoped replay protection.
     * Returns false (replay detected) if the tuple was already recorded.
     */
    fun isNewMessage(messageId: String, senderId: String = ""): Boolean {
        val key = if (senderId.isNotEmpty()) "$senderId:$messageId" else messageId
        return recentMessageIds.add(key)
    }
}
