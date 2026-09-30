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
}
