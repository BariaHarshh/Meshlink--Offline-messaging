package com.meshlink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.dao.PendingMessageDao
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.data.local.entity.PendingMessageEntity

@Database(
    entities = [
        MessageEntity::class,
        KnownDeviceEntity::class,
        PendingMessageEntity::class   // Phase 4: store-and-forward queue
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun deviceDao(): DeviceDao
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        /**
         * v1 → v2: adds [pending_messages] table for store-and-forward queuing.
         * Pure additive migration — no existing data is affected.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_messages (
                        id              TEXT    NOT NULL PRIMARY KEY,
                        packetJson      TEXT    NOT NULL,
                        targetDeviceId  TEXT    NOT NULL,
                        enqueuedAt      INTEGER NOT NULL,
                        expiresAt       INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pending_messages_targetDeviceId " +
                    "ON pending_messages (targetDeviceId)"
                )
            }
        }

        /**
         * v2 → v3: adds senderName column to messages table for user identity display.
         * Pure additive migration — existing messages get empty string as default.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN senderName TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
