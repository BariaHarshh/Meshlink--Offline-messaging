package com.meshlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_devices")
data class KnownDeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val lastSeen: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnownDeviceEntity) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}
