package com.meshlink.app.data.repository

import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.mapper.toDomain
import com.meshlink.app.data.local.mapper.toEntity
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

}
