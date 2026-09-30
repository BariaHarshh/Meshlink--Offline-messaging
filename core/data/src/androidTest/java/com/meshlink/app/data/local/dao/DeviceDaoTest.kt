package com.meshlink.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var deviceDao: DeviceDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        deviceDao = database.deviceDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsertAndQueryDevice() = runTest {
        val device = KnownDeviceEntity(
            deviceId = "device-1",
            displayName = "Alice's Phone",
            publicKey = "pubkey123".toByteArray(),
            lastSeen = System.currentTimeMillis()
        )

        deviceDao.upsert(device)

        val result = deviceDao.getAll().first()
        assertEquals(1, result.size)
        assertEquals("device-1", result[0].deviceId)
        assertEquals("Alice's Phone", result[0].displayName)
    }

    @Test
    fun upsertUpdatesExistingDevice() = runTest {
        val device = KnownDeviceEntity(
            deviceId = "device-1",
            displayName = "Old Name",
            publicKey = "key".toByteArray(),
            lastSeen = 1000L
        )
        deviceDao.upsert(device)

        val updated = device.copy(displayName = "New Name", lastSeen = 2000L)
        deviceDao.upsert(updated)

        val result = deviceDao.getAll().first()
        assertEquals(1, result.size)
        assertEquals("New Name", result[0].displayName)
        assertEquals(2000L, result[0].lastSeen)
    }
}
