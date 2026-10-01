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
}

