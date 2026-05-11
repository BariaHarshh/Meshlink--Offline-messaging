package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Upsert
    suspend fun upsert(device: KnownDeviceEntity)

    @Query("SELECT * FROM known_devices ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): KnownDeviceEntity?

    @Query("UPDATE known_devices SET displayName = :newName WHERE deviceId = :deviceId")
    suspend fun updateDisplayName(deviceId: String, newName: String)
}
