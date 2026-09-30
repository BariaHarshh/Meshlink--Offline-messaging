package com.meshlink.app.crypto

import com.meshlink.app.crypto.session.SessionKeyStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * JVM unit tests for [SessionKeyStore] — covers session lifecycle, peer identity lookup,
 * and replay-prevention via [SessionKeyStore.isNewMessage].
 *
 * No Android deps; runs as plain Kotlin JVM tests.
 */
class SessionKeyStoreTest {

    private lateinit var store: SessionKeyStore

    private fun fakeKey(seed: Int = 0): SecretKey =
        SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES")

    @Before
    fun setUp() {
        store = SessionKeyStore()
    }

    // ── Session storage and retrieval ─────────────────────────────────────────

    @Test
    fun `getSessionKey returns null before any session stored`() {
        assertNull(store.getSessionKey("endpoint-1"))
    }

    @Test
    fun `isHandshakeComplete is false before session stored`() {
        assertFalse(store.isHandshakeComplete("endpoint-1"))
    }

    @Test
    fun `storeSession makes isHandshakeComplete true`() {
        store.storeSession("ep-1", "dev-1", ByteArray(65), fakeKey())
        assertTrue(store.isHandshakeComplete("ep-1"))
    }

    @Test
    fun `getSessionKey returns stored key after storeSession`() {
        val key = fakeKey(42)
        store.storeSession("ep-1", "dev-1", ByteArray(65), key)
        assertNotNull(store.getSessionKey("ep-1"))
    }

    @Test
    fun `getAuthenticatedPeerDeviceId returns peerDeviceId after storeSession`() {
        store.storeSession("ep-1", "peer-device-abc", ByteArray(65), fakeKey())
        val result = store.getAuthenticatedPeerDeviceId("ep-1")
        assert(result == "peer-device-abc") { "Expected peer-device-abc but got $result" }
    }

    @Test
    fun `getAuthenticatedPeerPublicKey returns stored public key bytes`() {
        val pubKey = ByteArray(65) { (it + 7).toByte() }
        store.storeSession("ep-1", "dev-X", pubKey, fakeKey())
        val stored = store.getAuthenticatedPeerPublicKey("ep-1")
        assertNotNull(stored)
        assertTrue(stored!!.contentEquals(pubKey))
    }

    @Test
    fun `storeSessionKey alone makes isHandshakeComplete true`() {
        store.storeSessionKey("ep-2", fakeKey(99))
        assertTrue(store.isHandshakeComplete("ep-2"))
    }

    @Test
    fun `multiple endpoints are independent`() {
        store.storeSession("ep-A", "dev-A", ByteArray(65), fakeKey(1))
        store.storeSession("ep-B", "dev-B", ByteArray(65), fakeKey(2))

        assertTrue(store.isHandshakeComplete("ep-A"))
        assertTrue(store.isHandshakeComplete("ep-B"))
        assertNull(store.getSessionKey("ep-C"))  // unrelated endpoint
    }

    // ── Session clearance ─────────────────────────────────────────────────────

    @Test
    fun `clearSession removes key so isHandshakeComplete returns false`() {
        store.storeSession("ep-1", "dev-1", ByteArray(65), fakeKey())
        assertTrue(store.isHandshakeComplete("ep-1"))

        store.clearSession("ep-1")

        assertFalse(store.isHandshakeComplete("ep-1"))
        assertNull(store.getSessionKey("ep-1"))
        assertNull(store.getAuthenticatedPeerDeviceId("ep-1"))
        assertNull(store.getAuthenticatedPeerPublicKey("ep-1"))
    }

    @Test
    fun `clearSession on unknown endpoint does not throw`() {
        store.clearSession("endpoint-that-never-existed")
        // Simply should not throw
    }

    @Test
    fun `clearSession only removes the targeted endpoint`() {
        store.storeSession("ep-A", "dev-A", ByteArray(65), fakeKey(1))
        store.storeSession("ep-B", "dev-B", ByteArray(65), fakeKey(2))

        store.clearSession("ep-A")

        assertFalse(store.isHandshakeComplete("ep-A"))
        assertTrue(store.isHandshakeComplete("ep-B"))
    }

    // ── Replay prevention (isNewMessage) ──────────────────────────────────────

    @Test
    fun `isNewMessage returns true for a brand-new messageId`() {
        assertTrue(store.isNewMessage("msg-001"))
    }

    @Test
    fun `isNewMessage returns false for a repeated messageId`() {
        store.isNewMessage("msg-dup")         // first time — admitted
        assertFalse(store.isNewMessage("msg-dup"))  // second time — replay
    }

    @Test
    fun `isNewMessage is sender-scoped when senderId is provided`() {
        assertTrue(store.isNewMessage("msg-1", "sender-A"))
        // Same messageId, different sender — must be admitted (different key)
        assertTrue(store.isNewMessage("msg-1", "sender-B"))
    }

    @Test
    fun `isNewMessage replay detected within same sender scope`() {
        store.isNewMessage("msg-1", "sender-A")
        assertFalse(store.isNewMessage("msg-1", "sender-A"))  // replay
    }

    @Test
    fun `isNewMessage without senderId and with empty senderId are equivalent`() {
        // Both should resolve to the same key ("msg-x")
        store.isNewMessage("msg-x", "")
        assertFalse(store.isNewMessage("msg-x"))
        assertFalse(store.isNewMessage("msg-x", ""))
    }

    @Test
    fun `isNewMessage handles large volume of unique messages`() {
        for (i in 0 until 500) {
            assertTrue("Message $i should be new", store.isNewMessage("bulk-msg-$i"))
        }
    }

    @Test
    fun `isNewMessage is thread-safe under concurrent access`() {
        val results = java.util.concurrent.CopyOnWriteArrayList<Boolean>()

        val threads = (0 until 10).map { threadId ->
            Thread {
                for (i in 0 until 100) {
                    val result = store.isNewMessage("t$threadId-msg-$i")
                    results.add(result)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // All unique messages — every result should be true (no crashes)
        assertTrue("All results should be true for unique messages", results.all { it })
    }
}
