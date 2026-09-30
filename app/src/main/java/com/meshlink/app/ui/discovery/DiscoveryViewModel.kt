package com.meshlink.app.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.domain.repository.NearbyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository
) : ViewModel() {

    val devices: StateFlow<List<DiscoveredDevice>> = nearbyRepository.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionStates: StateFlow<Map<String, ConnectionState>> = nearbyRepository.connectionStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Emits Pair(endpointId, deviceName) when the UI should navigate to the Chat screen. */
    private val _navigateToChat = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val navigateToChat: SharedFlow<Pair<String, String>> = _navigateToChat.asSharedFlow()

    /**
     * Tap on device row → open chat if already connected, otherwise request connection first.
     */
    fun onDeviceClick(device: DiscoveredDevice) {
        val state = connectionStates.value[device.endpointId]
        when (state) {
            ConnectionState.CONNECTED -> {
                // Already connected — just navigate to chat
                viewModelScope.launch {
                    _navigateToChat.emit(device.endpointId to device.name)
                }
            }
            ConnectionState.CONNECTING, ConnectionState.HANDSHAKING -> {
                // Connection in progress — do nothing, wait for it to complete
            }
            else -> {
                // IDLE / DISCONNECTED / null — initiate connection, navigate only after connected
                nearbyRepository.requestConnection(device.endpointId)
                viewModelScope.launch {
                    connectionStates.collect { states ->
                        val current = states[device.endpointId]
                        if (current == ConnectionState.CONNECTED) {
                            _navigateToChat.emit(device.endpointId to device.name)
                            return@collect
                        }
                        if (current == ConnectionState.DISCONNECTED) {
                            return@collect  // connection failed, stop waiting
                        }
                    }
                }
            }
        }
    }

    /**
     * Explicit disconnect button pressed.
     */
    fun onDisconnectClick(endpointId: String) {
        nearbyRepository.disconnect(endpointId)
    }
}
