package com.meshlink.app.mesh.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeenMessageCacheTest {

    private lateinit var cache: SeenMessageCache

    @Before
    fun setUp() {
        cache = SeenMessageCache()
    }

    @Test
    fun `new messageId is not seen`() {
        assertFalse(cache.isAlreadySeen("msg-1"))
    }

    @Test
    fun `markSeen then isAlreadySeen returns true`() {
        cache.markSeen("msg-1")
        assertTrue(cache.isAlreadySeen("msg-1"))
    }

    @Test
    fun `different message IDs are independent`() {
        cache.markSeen("msg-A")
        assertTrue(cache.isAlreadySeen("msg-A"))
        assertFalse(cache.isAlreadySeen("msg-B"))
    }

    @Test
    fun `multiple marks do not crash and keep entry seen`() {
        cache.markSeen("dup")
        cache.markSeen("dup")
        cache.markSeen("dup")
        assertTrue(cache.isAlreadySeen("dup"))
    }

    @Test
    fun `cache handles 1000 entries without evicting the last one`() {
        for (i in 0..999) {
            cache.markSeen("msg-$i")
        }
        // The most-recently-used entry (999) must still be present
        assertTrue(cache.isAlreadySeen("msg-999"))
    }

    @Test
    fun `cache evicts oldest entry when capacity exceeded`() {
        // Fill capacity to exactly 1000
        for (i in 0..999) {
            cache.markSeen("msg-$i")
        }
        // Access every entry except msg-0 to make msg-0 the LRU
        for (i in 1..999) {
            cache.isAlreadySeen("msg-$i")
        }
        // Adding one more entry should evict msg-0 (LRU)
        cache.markSeen("msg-new")
        assertFalse("LRU entry should have been evicted", cache.isAlreadySeen("msg-0"))
        assertTrue(cache.isAlreadySeen("msg-new"))
    }

    @Test
    fun `concurrent access from two threads does not throw`() {
        val t1 = Thread { repeat(500) { cache.markSeen("t1-$it") } }
        val t2 = Thread { repeat(500) { cache.isAlreadySeen("t2-$it") } }
        t1.start(); t2.start()
        t1.join(); t2.join()
        // No assertion — test just verifies no crash / deadlock under concurrent access
    }
}
