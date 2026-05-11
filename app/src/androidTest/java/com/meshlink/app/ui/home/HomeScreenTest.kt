package com.meshlink.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.MainActivity
import com.meshlink.app.data.di.RepositoryModule
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.mesh.di.NearbyBindModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(RepositoryModule::class, NearbyBindModule::class)
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val messageRepo: MessageRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val deviceRepo: DeviceRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val pendingMessageRepo: PendingMessageRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val nearbyRepo: NearbyRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())
        every { nearbyRepo.connectionStates }                   returns MutableStateFlow(emptyMap())
    }

    @Test
    fun emptyState_showsNoActiveChannels() {
        composeRule.setContent {
            HomeScreen(onConversationClick = { _, _ -> })
        }

        composeRule.onNodeWithText("No active channels yet").assertIsDisplayed()
    }

    @Test
    fun header_showsMeshActive() {
        composeRule.setContent {
            HomeScreen(onConversationClick = { _, _ -> })
        }

        composeRule.onNodeWithText("MESH ACTIVE").assertIsDisplayed()
    }

    @Test
    fun conversationList_showsDeviceName() {
        val now = System.currentTimeMillis()
        val msg = Message("m1", "peer-1", "local", "Hi".toByteArray(), now, true)
        val dev = KnownDevice("peer-1", "Charlie", ByteArray(0), now)

        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(msg))
        every { deviceRepo.getAllDevices() }                    returns flowOf(listOf(dev))

        composeRule.setContent {
            HomeScreen(onConversationClick = { _, _ -> })
        }

        composeRule.onNodeWithText("Charlie").assertIsDisplayed()
    }

    @Test
    fun networkStatus_showsPeerCount() {
        composeRule.setContent {
            HomeScreen(onConversationClick = { _, _ -> })
        }

        composeRule.onNodeWithText("0 PEERS").assertIsDisplayed()
        composeRule.onNodeWithText("SECURE MESH ACTIVE").assertIsDisplayed()
    }
}
