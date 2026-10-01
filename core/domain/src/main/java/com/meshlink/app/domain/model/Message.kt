package com.meshlink.app.domain.model

data class Message(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val ciphertext: ByteArray,
    val timestamp: Long,
    val delivered: Boolean = false,
    /** Human-readable display name of the sender. Empty string if unknown. */
    val senderName: String = "",
    /** Phase 4A: Detailed delivery lifecycle status. */
    val deliveryStatus: DeliveryStatus = if (delivered) DeliveryStatus.DELIVERED else DeliveryStatus.PENDING
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
