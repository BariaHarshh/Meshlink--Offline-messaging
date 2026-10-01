package com.meshlink.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        messageDao = database.messageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryMessage() = runTest {
        val message = MessageEntity(
            id = "msg-1",
            senderId = "device-a",
            receiverId = "device-b",
            ciphertext = "hello".toByteArray(),
            timestamp = System.currentTimeMillis(),
            delivered = false
        )

        messageDao.insert(message)

        val result = messageDao.getByConversation("device-a").first()
        assertEquals(1, result.size)
        assertEquals("msg-1", result[0].id)
        assertEquals("device-a", result[0].senderId)
    }

    @Test
    fun queryByConversationReturnsBothDirections() = runTest {
        val sent = MessageEntity(
            id = "msg-1",
            senderId = "me",
            receiverId = "peer",
            ciphertext = "hi".toByteArray(),
            timestamp = 1000L,
            delivered = true
        )
        val received = MessageEntity(
            id = "msg-2",
            senderId = "peer",
            receiverId = "me",
            ciphertext = "hey".toByteArray(),
            timestamp = 2000L,
            delivered = true
        )

        messageDao.insert(sent)
        messageDao.insert(received)

        val result = messageDao.getByConversation("peer").first()
        assertEquals(2, result.size)
        assertEquals("msg-1", result[0].id)
        assertEquals("msg-2", result[1].id)
    }

    @Test
    fun updateDeliveryStatusModifiesOnlyDeliveryStatus() = runTest {
        val originalCiphertext = "confidential payload".toByteArray()
        val originalTimestamp = 1700000000000L
        val message = MessageEntity(
            id = "msg-update-test",
            senderId = "sender-node",
            receiverId = "receiver-node",
            ciphertext = originalCiphertext,
            timestamp = originalTimestamp,
            delivered = false,
            senderName = "Alice",
            deliveryStatus = "PENDING"
        )

        messageDao.insert(message)

        // 1. Initial assertion
        val initial = messageDao.getMessageById("msg-update-test")
        assertEquals("PENDING", initial?.deliveryStatus)
        assertEquals(false, initial?.delivered)

        // 2. Transition to SENT
        messageDao.updateDeliveryStatus("msg-update-test", "SENT")
        val sent = messageDao.getMessageById("msg-update-test")
        assertEquals("SENT", sent?.deliveryStatus)
        assertEquals(false, sent?.delivered)
        assertEquals("sender-node", sent?.senderId)
        assertEquals("receiver-node", sent?.receiverId)
        assertEquals("Alice", sent?.senderName)
        assertEquals(originalTimestamp, sent?.timestamp)
        org.junit.Assert.assertArrayEquals(originalCiphertext, sent?.ciphertext)

        // 3. Transition to DELIVERED
        messageDao.updateDeliveryStatus("msg-update-test", "DELIVERED")
        val delivered = messageDao.getMessageById("msg-update-test")
        assertEquals("DELIVERED", delivered?.deliveryStatus)
        assertEquals(true, delivered?.delivered)
        assertEquals("sender-node", delivered?.senderId)
        assertEquals("receiver-node", delivered?.receiverId)
        assertEquals("Alice", delivered?.senderName)
        assertEquals(originalTimestamp, delivered?.timestamp)
        org.junit.Assert.assertArrayEquals(originalCiphertext, delivered?.ciphertext)
    }
}

