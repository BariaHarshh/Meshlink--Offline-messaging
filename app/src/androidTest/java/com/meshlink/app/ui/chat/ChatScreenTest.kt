package com.meshlink.app.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.MainActivity
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.data.di.RepositoryModule
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
import java.net.URLEncoder

@HiltAndroidTest
@UninstallModules(RepositoryModule::class, NearbyBindModule::class)
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

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

    private val testEndpointId = "test-endpoint-1"
    private val testDeviceName = "TestDevice"

    @Before
    fun setUp() {
        hiltRule.inject()
        every { nearbyRepo.connectionStates } returns MutableStateFlow(emptyMap())
        every { nearbyRepo.peerDeviceIdForEndpoint(any()) } returns null
        every { nearbyRepo.currentEndpointForName(any()) }  returns null
        every { messageRepo.getMessagesByConversation(any()) } returns flowOf(emptyList())
    }

    private fun launchChatScreen(
        connectionState: ConnectionState = ConnectionState.IDLE,
        messages: List<Message> = emptyList()
    ) {
        every { nearbyRepo.connectionStates } returns MutableStateFlow(
            mapOf(testEndpointId to connectionState)
        )
        every { messageRepo.getMessagesByConversation(any()) } returns flowOf(messages)

        composeRule.setContent {
            val navController = rememberNavController()
            val encodedName   = URLEncoder.encode(testDeviceName, "UTF-8")
            NavHost(navController = navController, startDestination = "chat") {
                composable(
                    route     = "chat",
                    arguments = listOf(
                        navArgument("deviceId")   { type = NavType.StringType; defaultValue = testEndpointId },
                        navArgument("deviceName") { type = NavType.StringType; defaultValue = encodedName }
                    )
                ) {
                    ChatScreen(onBackClick = {})
                }
            }
        }
    }

    @Test
    fun backButton_isDisplayed() {
        launchChatScreen()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun deviceName_isDisplayedInTopBar() {
        launchChatScreen()
        composeRule.onNodeWithText(testDeviceName).assertIsDisplayed()
    }

    @Test
    fun messageInput_isDisabledWhenDisconnected() {
        launchChatScreen(connectionState = ConnectionState.DISCONNECTED)
        composeRule.onNodeWithContentDescription("Message input").assertIsNotEnabled()
    }

    @Test
    fun messageInput_isEnabledWhenConnected() {
        launchChatScreen(connectionState = ConnectionState.CONNECTED)
        composeRule.onNodeWithContentDescription("Message input").assertIsEnabled()
    }

    @Test
    fun sentMessage_isDisplayedInList() {
        val msg = Message(
            id         = "msg-1",
            senderId   = testEndpointId,
            receiverId = "local",
            ciphertext = "Hello MeshLink!".toByteArray(),
            timestamp  = System.currentTimeMillis(),
            delivered  = false
        )
        launchChatScreen(messages = listOf(msg))
        composeRule.onNodeWithText("Hello MeshLink!").assertIsDisplayed()
    }

    @Test
    fun receivedMessage_isDisplayedInList() {
        val msg = Message(
            id         = "msg-2",
            senderId   = "other-device",
            receiverId = testEndpointId,
            ciphertext = "Hey there!".toByteArray(),
            timestamp  = System.currentTimeMillis(),
            delivered  = true
        )
        launchChatScreen(messages = listOf(msg))
        composeRule.onNodeWithText("Hey there!").assertIsDisplayed()
    }

    @Test
    fun typeInInput_textAppearsInTextField() {
        launchChatScreen(connectionState = ConnectionState.CONNECTED)
        composeRule.onNodeWithContentDescription("Message input").performTextInput("Hello!")
        composeRule.onNodeWithText("Hello!").assertIsDisplayed()
    }
}
