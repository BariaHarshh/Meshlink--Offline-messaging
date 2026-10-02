package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesByConversation(peerId: String): Flow<List<Message>>
    fun getLatestMessagePerConversation(): Flow<List<Message>>
    suspend fun insertMessage(message: Message)
    suspend fun updateDeliveryStatus(messageId: String, status: DeliveryStatus)
    suspend fun getMessageById(messageId: String): Message?
    suspend fun claimRetry(messageId: String, now: Long, nextRetryAt: Long, maxRetries: Int = Message.MAX_RETRY_COUNT): Boolean
    suspend fun getEligibleRetries(myDeviceId: String, now: Long, maxRetries: Int = Message.MAX_RETRY_COUNT): List<Message>
    suspend fun markFailedIfExpiredOrExhausted(now: Long, maxRetries: Int = Message.MAX_RETRY_COUNT): Int
    suspend fun markFailed(messageId: String)
    suspend fun updateRetrySchedule(messageId: String, retryCount: Int, nextRetryAt: Long)
}

