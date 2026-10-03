package com.meshlink.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.ui.home.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ChatsListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val deviceRepository:  DeviceRepository,
    private val nearbyRepository:  NearbyRepository,
    @Named("localDeviceId") private val localDeviceId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val conversations: StateFlow<List<Conversation>> = messageRepository
        .getLatestMessagePerConversation()
        .combine(deviceRepository.getAllDevices()) { messages, devices ->
            val deviceNameMap = devices.associate { it.deviceId to it.displayName }
            messages.map { msg ->
                val peerId   = if (msg.senderId == localDeviceId) msg.receiverId else msg.senderId
                val peerName = msg.senderName.takeIf { it.isNotEmpty() && msg.senderId != localDeviceId }
                    ?: deviceNameMap[peerId]
                    ?: peerId.take(8)
                Conversation(
                    deviceId       = peerId,
                    deviceName     = peerName,
                    lastMessage    = String(msg.ciphertext, Charsets.UTF_8),
                    timestamp      = msg.timestamp,
                    formattedTime  = formatConversationTime(msg.timestamp),
                    deliveryStatus = msg.deliveryStatus
                )
            }.sortedByDescending { it.timestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredConversations: StateFlow<List<Conversation>> = combine(conversations, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.deviceName.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    private fun formatConversationTime(epochMillis: Long): String {
        val msgCal = Calendar.getInstance().also { it.timeInMillis = epochMillis }
        val now    = Calendar.getInstance()
        return when {
            isSameDay(msgCal, now)    ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
            isYesterday(msgCal, now)  -> "Yesterday"
            else                      ->
                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(epochMillis))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(a: Calendar, b: Calendar): Boolean {
        val yesterday = Calendar.getInstance().also {
            it.timeInMillis = b.timeInMillis
            it.add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(a, yesterday)
    }
}
