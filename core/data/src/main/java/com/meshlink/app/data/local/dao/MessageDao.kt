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
