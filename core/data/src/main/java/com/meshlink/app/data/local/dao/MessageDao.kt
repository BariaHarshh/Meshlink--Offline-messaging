package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshlink.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * Phase 4A/4D: Updates ONLY the delivery status of a message.
     * Preserves terminal DELIVERED status against regression.
     */
    @Query("""
        UPDATE messages 
        SET deliveryStatus = :status, 
            delivered = CASE WHEN :status = 'DELIVERED' THEN 1 ELSE delivered END 
        WHERE id = :messageId 
          AND (:status = 'DELIVERED' OR deliveryStatus != 'DELIVERED')
    """)
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    /**
     * Phase 4D: Atomically claims a message for retry and increments its retryCount.
     * Returns 1 if claimed, 0 if another worker already claimed it or status changed.
     */
    @Query("""
        UPDATE messages 
        SET retryCount = retryCount + 1, 
            nextRetryAt = :nextRetryAt 
        WHERE id = :messageId 
          AND deliveryStatus IN ('SENT', 'QUEUED') 
          AND retryCount < :maxRetries 
          AND nextRetryAt <= :now 
          AND expiresAt > :now
    """)
    suspend fun claimRetry(messageId: String, now: Long, nextRetryAt: Long, maxRetries: Int): Int

    /**
     * Phase 4D: Returns unacknowledged outgoing messages eligible for retry.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE senderId = :myDeviceId 
          AND deliveryStatus IN ('SENT', 'QUEUED') 
          AND retryCount < :maxRetries 
          AND nextRetryAt <= :now 
          AND expiresAt > :now 
        ORDER BY nextRetryAt ASC
    """)
    suspend fun getEligibleRetries(myDeviceId: String, now: Long, maxRetries: Int): List<MessageEntity>

    /**
     * Phase 4D: Marks expired or retry-exhausted unresolved messages as FAILED.
     */
    @Query("""
        UPDATE messages 
        SET deliveryStatus = 'FAILED' 
        WHERE deliveryStatus IN ('PENDING', 'QUEUED', 'SENT') 
          AND (expiresAt <= :now OR retryCount >= :maxRetries)
    """)
    suspend fun markFailedIfExpiredOrExhausted(now: Long, maxRetries: Int): Int

    /**
     * Phase 4D: Marks a specific message as FAILED if not already DELIVERED.
     */
    @Query("UPDATE messages SET deliveryStatus = 'FAILED' WHERE id = :messageId AND deliveryStatus != 'DELIVERED'")
    suspend fun markFailed(messageId: String)

    /**
     * Phase 4D: Updates retry schedule for a message.
     */
    @Query("UPDATE messages SET retryCount = :retryCount, nextRetryAt = :nextRetryAt WHERE id = :messageId AND deliveryStatus != 'DELIVERED'")
    suspend fun updateRetrySchedule(messageId: String, retryCount: Int, nextRetryAt: Long)

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE senderId = :peerId OR receiverId = :peerId ORDER BY timestamp ASC")
    fun getByConversation(peerId: String): Flow<List<MessageEntity>>



    /**
     * Returns the latest message per conversation peer.
     * Groups by the "other" party (senderId for incoming, receiverId for outgoing).
     */
    @Query("""
        SELECT * FROM messages
        WHERE id IN (
            SELECT id FROM messages AS m
            WHERE m.timestamp = (
                SELECT MAX(m2.timestamp) FROM messages AS m2
                WHERE (m2.senderId = m.senderId AND m2.receiverId = m.receiverId)
                   OR (m2.senderId = m.receiverId AND m2.receiverId = m.senderId)
            )
            GROUP BY
                CASE WHEN m.senderId < m.receiverId
                     THEN m.senderId || '|' || m.receiverId
                     ELSE m.receiverId || '|' || m.senderId
                END
        )
        ORDER BY timestamp DESC
    """)
    fun getLatestMessagePerConversation(): Flow<List<MessageEntity>>
}
