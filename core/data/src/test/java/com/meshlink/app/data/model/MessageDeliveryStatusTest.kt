package com.meshlink.app.data.model

import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.data.local.mapper.toDomain
import com.meshlink.app.data.local.mapper.toEntity
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.Message
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4A Unit Tests: Message Delivery Status & Migration.
 *
 * Verifies:
 * 1. New Message instances default to DeliveryStatus.PENDING and delivered = false.
 * 2. Message instances created with delivered = true map to DeliveryStatus.DELIVERED.
 * 3. EntityMappers accurately convert between Message and MessageEntity with all statuses.
 * 4. MIGRATION_4_5 adds column deliveryStatus without altering existing ciphertext, ids, or timestamps.
 * 5. Migration backfills delivered = 1 records to DELIVERED and delivered = 0 records to PENDING.
 */
class MessageDeliveryStatusTest {

    @Test
    fun `new Message defaults to PENDING delivery status`() {
        val ciphertext = "Secret Payload".toByteArray(Charsets.UTF_8)
        val msg = Message(
            id         = "msg-001",
            senderId   = "node-a",
            receiverId = "node-b",
            ciphertext = ciphertext,
            timestamp  = 1700000000000L
        )

        assertEquals(DeliveryStatus.PENDING, msg.deliveryStatus)
        assertFalse("New message must not be marked delivered", msg.delivered)
        assertEquals("msg-001", msg.id)
        assertEquals("node-a", msg.senderId)
        assertEquals("node-b", msg.receiverId)
        assertArrayEquals(ciphertext, msg.ciphertext)
        assertEquals(1700000000000L, msg.timestamp)
    }

    @Test
    fun `Message created with delivered=true defaults to DELIVERED status`() {
        val msg = Message(
            id         = "msg-002",
            senderId   = "node-a",
            receiverId = "node-b",
            ciphertext = "Payload".toByteArray(Charsets.UTF_8),
            timestamp  = 1700000000000L,
            delivered  = true
        )

        assertEquals(DeliveryStatus.DELIVERED, msg.deliveryStatus)
        assertTrue(msg.delivered)
    }

    @Test
    fun `Message supports all DeliveryStatus transitions`() {
        val baseMsg = Message(
            id         = "msg-003",
            senderId   = "node-a",
            receiverId = "node-b",
            ciphertext = "Status Test".toByteArray(Charsets.UTF_8),
            timestamp  = 1700000000000L
        )

        val queued = baseMsg.copy(deliveryStatus = DeliveryStatus.QUEUED)
        assertEquals(DeliveryStatus.QUEUED, queued.deliveryStatus)

        val sent = queued.copy(deliveryStatus = DeliveryStatus.SENT)
        assertEquals(DeliveryStatus.SENT, sent.deliveryStatus)

        val delivered = sent.copy(deliveryStatus = DeliveryStatus.DELIVERED, delivered = true)
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertTrue(delivered.delivered)

        val failed = baseMsg.copy(deliveryStatus = DeliveryStatus.FAILED)
        assertEquals(DeliveryStatus.FAILED, failed.deliveryStatus)
    }

    @Test
    fun `EntityMappers map all delivery statuses accurately to and from Entity`() {
        for (status in DeliveryStatus.values()) {
            val domain = Message(
                id             = "msg-test-${status.name}",
                senderId       = "node-sender",
                receiverId     = "node-receiver",
                ciphertext     = "Test ${status.name}".toByteArray(Charsets.UTF_8),
                timestamp      = 1700000000000L,
                delivered      = status == DeliveryStatus.DELIVERED,
                senderName     = "Dr. Alice",
                deliveryStatus = status
            )

            val entity = domain.toEntity()
            assertEquals(status.name, entity.deliveryStatus)
            assertEquals("Dr. Alice", entity.senderName)
            assertEquals("msg-test-${status.name}", entity.id)

            val roundTrip = entity.toDomain()
            assertEquals(status, roundTrip.deliveryStatus)
            assertEquals("Dr. Alice", roundTrip.senderName)
            assertEquals("msg-test-${status.name}", roundTrip.id)
            assertArrayEquals(domain.ciphertext, roundTrip.ciphertext)
            assertEquals(domain.timestamp, roundTrip.timestamp)
        }
    }

    @Test
    fun `MIGRATION_4_5 metadata is correct`() {
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `MIGRATION_4_5 executes correct non-destructive DDL and backfill queries`() {
        val executedSql = mutableListOf<String>()
        val dbProxy = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as androidx.sqlite.db.SupportSQLiteDatabase

        AppDatabase.MIGRATION_4_5.migrate(dbProxy)

        assertEquals(2, executedSql.size)
        assertTrue(
            "Must add deliveryStatus column with PENDING default",
            executedSql[0].contains("ALTER TABLE messages ADD COLUMN deliveryStatus TEXT NOT NULL DEFAULT 'PENDING'")
        )
        assertTrue(
            "Must backfill delivered messages to DELIVERED",
            executedSql[1].contains("UPDATE messages SET deliveryStatus = 'DELIVERED' WHERE delivered = 1")
        )
    }
}

