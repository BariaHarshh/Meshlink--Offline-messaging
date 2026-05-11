package com.meshlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted store-and-forward entry.
 *
 * Survives app restarts so messages queued while Device C was unreachable are
 * retried the next time the app runs and a viable relay connects.
 *
 * [targetDeviceId] is indexed so [PendingMessageDao.getPendingFor] is O(log n).
 */
@Entity(
    tableName = "pending_messages",
    indices = [Index(value = ["targetDeviceId"])]
)
data class PendingMessageEntity(
    @PrimaryKey val id: String,
    /** Serialized [com.meshlink.app.domain.model.MeshPacket] JSON. */
    val packetJson: String,
    /** Stable SHA-256 deviceId of the final recipient. */
    val targetDeviceId: String,
    val enqueuedAt: Long,
    /** expiresAt = enqueuedAt + 48 hours. [MeshCleanupWorker] deletes expired entries. */
    val expiresAt: Long
)
