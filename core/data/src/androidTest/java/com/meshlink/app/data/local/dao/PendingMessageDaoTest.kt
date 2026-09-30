package com.meshlink.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.entity.PendingMessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingMessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PendingMessageDao

    private val now = System.currentTimeMillis()
    private val in48h = now + 48 * 60 * 60 * 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.pendingMessageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runTest {
        val entity = pendingEntity("pm-1", targetDeviceId = "device-A", expiresAt = in48h)
        dao.insert(entity)

        val result = dao.getPendingFor("device-A", now)
        assertEquals(1, result.size)
        assertEquals("pm-1", result[0].id)
    }

    @Test
    fun getPendingForFiltersOtherTargets() = runTest {
        dao.insert(pendingEntity("pm-1", targetDeviceId = "device-A", expiresAt = in48h))
        dao.insert(pendingEntity("pm-2", targetDeviceId = "device-B", expiresAt = in48h))

        val resultA = dao.getPendingFor("device-A", now)
        assertEquals(1, resultA.size)
        assertEquals("pm-1", resultA[0].id)

        val resultB = dao.getPendingFor("device-B", now)
        assertEquals(1, resultB.size)
        assertEquals("pm-2", resultB[0].id)
    }

    @Test
    fun getPendingForExcludesExpiredEntries() = runTest {
        val expired = pendingEntity("pm-expired", targetDeviceId = "device-A", expiresAt = now - 1_000L)
        val valid   = pendingEntity("pm-valid",   targetDeviceId = "device-A", expiresAt = in48h)
        dao.insert(expired)
        dao.insert(valid)

        val result = dao.getPendingFor("device-A", now)
        assertEquals(1, result.size)
        assertEquals("pm-valid", result[0].id)
    }

    @Test
    fun deleteByIdRemovesOnlyThatEntry() = runTest {
        dao.insert(pendingEntity("pm-1", targetDeviceId = "device-A", expiresAt = in48h))
        dao.insert(pendingEntity("pm-2", targetDeviceId = "device-A", expiresAt = in48h))

        dao.deleteById("pm-1")

        val result = dao.getPendingFor("device-A", now)
        assertEquals(1, result.size)
        assertEquals("pm-2", result[0].id)
    }

    @Test
    fun deleteExpiredRemovesOnlyExpiredEntries() = runTest {
        dao.insert(pendingEntity("pm-old",   targetDeviceId = "device-A", expiresAt = now - 1_000L))
        dao.insert(pendingEntity("pm-valid", targetDeviceId = "device-A", expiresAt = in48h))

        dao.deleteExpired(now)

        val all = dao.getAllPending(0L)  // 0 = epoch start, get everything that hasn't expired
        assertEquals(1, all.size)
        assertEquals("pm-valid", all[0].id)
    }

    @Test
    fun getAllPendingReturnsAllNonExpiredAcrossTargets() = runTest {
        dao.insert(pendingEntity("pm-1", targetDeviceId = "device-A", expiresAt = in48h))
        dao.insert(pendingEntity("pm-2", targetDeviceId = "device-B", expiresAt = in48h))
        dao.insert(pendingEntity("pm-3", targetDeviceId = "device-C", expiresAt = now - 1L))

        val result = dao.getAllPending(now)
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "pm-1" })
        assertTrue(result.any { it.id == "pm-2" })
    }

    @Test
    fun insertReplacesDuplicateId() = runTest {
        dao.insert(pendingEntity("pm-1", targetDeviceId = "device-A", expiresAt = in48h))
        dao.insert(pendingEntity("pm-1", targetDeviceId = "device-B", expiresAt = in48h))  // replace

        val all = dao.getAllPending(now)
        assertEquals(1, all.size)
        assertEquals("device-B", all[0].targetDeviceId)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun pendingEntity(
        id: String,
        targetDeviceId: String,
        expiresAt: Long
    ) = PendingMessageEntity(
        id             = id,
        packetJson     = """{"id":"$id"}""",
        targetDeviceId = targetDeviceId,
        enqueuedAt     = now,
        expiresAt      = expiresAt
    )
}
