package com.meshlink.app.data.repository

import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.mapper.toDomain
import com.meshlink.app.data.local.mapper.toEntity
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesByConversation(peerId: String): Flow<List<Message>> =
        messageDao.getByConversation(peerId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getLatestMessagePerConversation(): Flow<List<Message>> =
        messageDao.getLatestMessagePerConversation().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun insertMessage(message: Message) {
        messageDao.insert(message.toEntity())
    }

    override suspend fun updateDeliveryStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateDeliveryStatus(messageId, status.name)
    }

    override suspend fun getMessageById(messageId: String): Message? =
        messageDao.getMessageById(messageId)?.toDomain()

    override suspend fun claimRetry(messageId: String, now: Long, nextRetryAt: Long, maxRetries: Int): Boolean =
        messageDao.claimRetry(messageId, now, nextRetryAt, maxRetries) > 0

    override suspend fun getEligibleRetries(myDeviceId: String, now: Long, maxRetries: Int): List<Message> =
        messageDao.getEligibleRetries(myDeviceId, now, maxRetries).map { it.toDomain() }

    override suspend fun markFailedIfExpiredOrExhausted(now: Long, maxRetries: Int): Int =
        messageDao.markFailedIfExpiredOrExhausted(now, maxRetries)

    override suspend fun markFailed(messageId: String) {
        messageDao.markFailed(messageId)
    }

    override suspend fun updateRetrySchedule(messageId: String, retryCount: Int, nextRetryAt: Long) {
        messageDao.updateRetrySchedule(messageId, retryCount, nextRetryAt)
    }
}
