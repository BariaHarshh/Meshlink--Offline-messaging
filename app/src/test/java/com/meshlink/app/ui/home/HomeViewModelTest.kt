package com.meshlink.app.ui.home

import app.cash.turbine.test
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepo: MessageRepository
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var viewModel: HomeViewModel

    private val localDeviceId = "local-device-id"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepo = mockk(relaxed = true)
        deviceRepo  = mockk(relaxed = true)
        nearbyRepo  = mockk(relaxed = true)

        // Default: no connections
        every { nearbyRepo.connectionStates } returns MutableStateFlow(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `conversations is empty when no messages`() = runTest {
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, localDeviceId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.conversations.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conversations maps peer name from device repository`() = runTest {
        val now = System.currentTimeMillis()
        val msg = Message(
            id         = "msg-1",
            senderId   = "peer-id",
            receiverId = localDeviceId,
            ciphertext = "hello".toByteArray(),
            timestamp  = now,
            delivered  = true
        )
        val device = KnownDevice(
            deviceId    = "peer-id",
            displayName = "Alice",
            publicKey   = ByteArray(0),
            lastSeen    = now
        )
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(msg))
        every { deviceRepo.getAllDevices() }                    returns flowOf(listOf(device))

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, localDeviceId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.conversations.test {
            val conversations = awaitItem()
            assertEquals(1, conversations.size)
            assertEquals("Alice", conversations[0].deviceName)
            assertEquals("peer-id", conversations[0].deviceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conversations falls back to truncated deviceId when device not in repo`() = runTest {
        val now = System.currentTimeMillis()
        val msg = Message(
            id         = "msg-1",
            senderId   = "unknown-peer-device",
            receiverId = localDeviceId,
            ciphertext = "hello".toByteArray(),
            timestamp  = now,
            delivered  = false
        )
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(msg))
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, localDeviceId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.conversations.test {
            val conversations = awaitItem()
            assertEquals(1, conversations.size)
            assertEquals("unknown-", conversations[0].deviceName)  // take(8)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conversations are sorted newest-first`() = runTest {
        val older = Message("msg-1", "peer-a", localDeviceId, "old".toByteArray(), 1_000L, true)
        val newer = Message("msg-2", "peer-b", localDeviceId, "new".toByteArray(), 9_000L, true)

        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(older, newer))
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, localDeviceId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.conversations.test {
            val conversations = awaitItem()
            assertEquals(2, conversations.size)
            assertTrue(conversations[0].timestamp > conversations[1].timestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renameDevice delegates to deviceRepository`() = runTest {
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, localDeviceId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameDevice("peer-id", "  Bob  ")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepo.updateDisplayName("peer-id", "Bob") }
    }

    @Test
    fun `peerCount reflects connected peers`() = runTest {
        val states = MutableStateFlow(
            mapOf(
                "ep-1" to ConnectionState.CONNECTED,
                "ep-2" to ConnectionState.CONNECTED,
                "ep-3" to ConnectionState.CONNECTING
            )
        )
        every { nearbyRepo.connectionStates } returns states
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, localDeviceId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.peerCount.test {
            assertEquals(2, awaitItem())  // only 2 CONNECTED, not CONNECTING
            cancelAndIgnoreRemainingEvents()
        }
    }
}
