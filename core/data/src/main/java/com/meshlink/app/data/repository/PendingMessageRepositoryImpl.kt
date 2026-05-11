package com.meshlink.app.data.repository

import com.meshlink.app.data.local.dao.PendingMessageDao
import com.meshlink.app.data.local.entity.PendingMessageEntity
import com.meshlink.app.domain.model.PendingMessage
import com.meshlink.app.domain.repository.PendingMessageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingMessageRepositoryImpl @Inject constructor(
    private val dao: PendingMessageDao
) : PendingMessageRepository {

    override suspend fun enqueue(message: PendingMessage) {
        dao.insert(message.toEntity())
    }

    override suspend fun getPendingFor(targetDeviceId: String): List<PendingMessage> =
        dao.getPendingFor(targetDeviceId, System.currentTimeMillis()).map { it.toDomain() }

    override suspend fun getAllPending(): List<PendingMessage> =
        dao.getAllPending(System.currentTimeMillis()).map { it.toDomain() }

    override suspend fun remove(id: String) {
        dao.deleteById(id)
    }

    override suspend fun deleteExpired(beforeEpochMillis: Long) {
        dao.deleteExpired(beforeEpochMillis)
    }

    private fun PendingMessage.toEntity() = PendingMessageEntity(
        id             = id,
        packetJson     = packetJson,
        targetDeviceId = targetDeviceId,
        enqueuedAt     = enqueuedAt,
        expiresAt      = expiresAt
    )

    private fun PendingMessageEntity.toDomain() = PendingMessage(
        id             = id,
        packetJson     = packetJson,
        targetDeviceId = targetDeviceId,
        enqueuedAt     = enqueuedAt,
        expiresAt      = expiresAt
    )
}
