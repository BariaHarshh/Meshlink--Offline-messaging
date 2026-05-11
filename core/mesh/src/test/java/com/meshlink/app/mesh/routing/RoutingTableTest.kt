package com.meshlink.app.mesh.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutingTableTest {

    private lateinit var table: RoutingTable

    @Before
    fun setUp() {
        table = RoutingTable()
    }

    @Test
    fun `unknown destination returns null`() {
        assertNull(table.getNextHop("device-unknown"))
    }

    @Test
    fun `addRoute then getNextHop returns the endpoint`() {
        table.addRoute("device-A", "endpoint-1")
        assertEquals("endpoint-1", table.getNextHop("device-A"))
    }

    @Test
    fun `addRoute overwrites an existing entry`() {
        table.addRoute("device-A", "endpoint-1")
        table.addRoute("device-A", "endpoint-2")
        assertEquals("endpoint-2", table.getNextHop("device-A"))
    }

    @Test
    fun `removeRoutesFor removes all routes through that endpoint`() {
        table.addRoute("device-A", "endpoint-1")
        table.addRoute("device-B", "endpoint-1")
        table.addRoute("device-C", "endpoint-2")

        table.removeRoutesFor("endpoint-1")

        assertNull(table.getNextHop("device-A"))
        assertNull(table.getNextHop("device-B"))
        assertEquals("endpoint-2", table.getNextHop("device-C"))
    }

    @Test
    fun `removeRoutesFor on unknown endpoint does not throw`() {
        table.addRoute("device-A", "endpoint-1")
        table.removeRoutesFor("endpoint-nonexistent")
        assertEquals("endpoint-1", table.getNextHop("device-A"))
    }

    @Test
    fun `knownDestinations returns all added destinations`() {
        table.addRoute("device-A", "endpoint-1")
        table.addRoute("device-B", "endpoint-2")
        val known = table.knownDestinations()
        assertTrue(known.contains("device-A"))
        assertTrue(known.contains("device-B"))
    }

    @Test
    fun `knownDestinations is empty initially`() {
        assertTrue(table.knownDestinations().isEmpty())
    }

    @Test
    fun `multiple devices route to different endpoints independently`() {
        table.addRoute("device-A", "ep-1")
        table.addRoute("device-B", "ep-2")
        table.addRoute("device-C", "ep-3")

        assertEquals("ep-1", table.getNextHop("device-A"))
        assertEquals("ep-2", table.getNextHop("device-B"))
        assertEquals("ep-3", table.getNextHop("device-C"))
    }
}
