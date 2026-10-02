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
    val deliveryStatus: DeliveryStatus = if (delivered) DeliveryStatus.DELIVERED else DeliveryStatus.PENDING,
    /** Phase 4D: Bounded retry count (0..MAX_RETRY_COUNT). */
    val retryCount: Int = 0,
    /** Phase 4D: Timestamp (epoch ms) after which this message becomes eligible for retry. */
    val nextRetryAt: Long = 0L,
    /** Phase 4D: Immutable expiration deadline (epoch ms). Retries never extend this. */
    val expiresAt: Long = timestamp + DEFAULT_TTL_MS
) {
    companion object {
        const val DEFAULT_TTL_MS = 48 * 60 * 60 * 1000L // 48 hours
        const val MAX_RETRY_COUNT = 5
        const val BASE_RETRY_DELAY_MS = 30_000L // 30s
        const val MAX_RETRY_DELAY_MS = 600_000L // 10 minutes (600s)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
