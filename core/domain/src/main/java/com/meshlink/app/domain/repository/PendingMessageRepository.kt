package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.PendingMessage

interface PendingMessageRepository {

    /** Enqueue a message for store-and-forward delivery. */
    suspend fun enqueue(message: PendingMessage)

    /**
     * Returns all unexpired pending messages whose [PendingMessage.targetDeviceId] matches
     * [targetDeviceId]. Used when a peer connects to flush its pending queue.
     */
    suspend fun getPendingFor(targetDeviceId: String): List<PendingMessage>

    /**
     * Returns ALL unexpired pending messages regardless of target.
     * Used when a new peer connects and we flood-check pending queue.
     */
    suspend fun getAllPending(): List<PendingMessage>

    /** Remove a successfully delivered entry by its queue id. */
    suspend fun remove(id: String)

    /** Delete all entries older than [beforeEpochMillis]. Called by [MeshCleanupWorker]. */
    suspend fun deleteExpired(beforeEpochMillis: Long)
}
