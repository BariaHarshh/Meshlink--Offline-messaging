package com.meshlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val ciphertext: ByteArray,
    val timestamp: Long,
    val delivered: Boolean,
    val senderName: String = "",
    val deliveryStatus: String = if (delivered) "DELIVERED" else "PENDING",
    val retryCount: Int = 0,
    val nextRetryAt: Long = 0L,
    val expiresAt: Long = timestamp + 48 * 60 * 60 * 1000L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
