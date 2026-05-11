package com.meshlink.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Named

// Safe-signal text constant — must match what ChatScreen renders
private const val SAFE_SIGNAL_TEXT = "\uD83D\uDFE2 I AM SAFE"

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val nearbyRepository: NearbyRepository,
    @Named("localDeviceId") val localDeviceId: String
) : ViewModel() {

    /**
     * Volatile Nearby endpointId from nav args.
     * Only used for Nearby API calls (requestConnection etc.).
     * Never used as a Room key — use [peerDeviceId] for that.
     */
    val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    val deviceName: String = (savedStateHandle.get<String>("deviceName"))?.let {
        URLDecoder.decode(it, "UTF-8")
    } ?: deviceId

    /**
     * Always returns the freshest Nearby endpointId for this peer.
     * Falls back to the original nav-arg endpointId if not yet re-discovered.
     */
    private val liveEndpointId: String
        get() = nearbyRepository.currentEndpointForName(deviceName) ?: deviceId

    /**
     * Stable SHA-256 peer deviceId — the Room conversation key.
     * Null until ECDH handshake completes; then updated reactively by watching [connectionStates].
     * [messages] is keyed off this via flatMapLatest so the Room query is always correct.
     */
    private val peerDeviceId: StateFlow<String> = nearbyRepository.connectionStates
        .map { _ ->
            nearbyRepository.peerDeviceIdForEndpoint(liveEndpointId) ?: deviceId
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), deviceId)

    /**
     * All messages for this conversation, always keyed by the STABLE peerDeviceId.
     * Automatically switches Room query key when the handshake completes and peerDeviceId
     * changes from the initial endpointId fallback to the real SHA-256 deviceId.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = peerDeviceId
        .flatMapLatest { peerId ->
            Timber.d("ChatViewModel: querying messages for peerId=$peerId")
            messageRepository.getMessagesByConversation(peerId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionState: StateFlow<ConnectionState> = nearbyRepository.connectionStates
        .map { states ->
            states[liveEndpointId]
                ?: states[deviceId]
                ?: ConnectionState.IDLE
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.IDLE)

    /** Number of currently CONNECTED peers across the whole mesh (for top bar chip). */
    val peerCount: StateFlow<Int> = nearbyRepository.connectionStates
        .map { states -> states.count { it.value == ConnectionState.CONNECTED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun onSendClick() {
        val text = _inputText.value.trim()
        if (text.isBlank()) return
        _inputText.value = ""

        val finalDestDeviceId = peerDeviceId.value
        val now               = System.currentTimeMillis()

        Timber.d("Sending to peerDeviceId=$finalDestDeviceId (liveEndpoint=$liveEndpointId)")

        // Build the plaintext packet — routeToDevice handles encryption and routing internally:
        //   • Direct connection   → CHAT (AES-256-GCM, Phase 3)
        //   • Known but offline   → ROUTED_CHAT (ECIES, Phase 4) — queued for auto-delivery
        //   • Unknown             → store in PendingMessageRepository (Phase 4)
        val packet = MeshPacket(
            senderId    = localDeviceId,
            receiverId  = finalDestDeviceId,
            content     = text,              // plaintext; routeToDevice encrypts
            timestamp   = now,
            originId    = localDeviceId,
            finalDestId = finalDestDeviceId
        )

        viewModelScope.launch {
            nearbyRepository.routeToDevice(finalDestDeviceId, packet)
        }
    }

    /** Send "🟢 I AM SAFE" as a direct message to this peer. */
    fun onSendSafeSignal() {
        val finalDestDeviceId = peerDeviceId.value
        val now               = System.currentTimeMillis()
        val packet = MeshPacket(
            senderId    = localDeviceId,
            receiverId  = finalDestDeviceId,
            content     = SAFE_SIGNAL_TEXT,
            timestamp   = now,
            originId    = localDeviceId,
            finalDestId = finalDestDeviceId
        )
        viewModelScope.launch {
            nearbyRepository.routeToDevice(finalDestDeviceId, packet)
        }
    }

    /** Send an emergency broadcast to all reachable nodes. */
    fun onSendBroadcast(content: String) {
        nearbyRepository.sendBroadcast(content)
    }
}
