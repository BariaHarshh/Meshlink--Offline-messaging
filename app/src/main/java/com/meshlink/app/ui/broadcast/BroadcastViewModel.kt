package com.meshlink.app.ui.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.repository.NearbyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BroadcastUiState(
    val message:   String = "",
    val isSending: Boolean = false,
    val isSent:    Boolean = false,
    val error:     String? = null
)

@HiltViewModel
class BroadcastViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BroadcastUiState())
    val state: StateFlow<BroadcastUiState> = _state

    fun onMessageChanged(text: String) {
        _state.update { it.copy(message = text, error = null) }
    }

    fun sendBroadcast() {
        val text = _state.value.message.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "Message cannot be empty") }
            return
        }

        _state.update { it.copy(isSending = true, error = null) }

        // Check if there are connected peers before attempting broadcast
        val hasPeers = nearbyRepository.getConnectedPeers().isNotEmpty()
        if (!hasPeers) {
            _state.update { it.copy(isSending = false, error = "No peers connected") }
            return
        }

        viewModelScope.launch {
            nearbyRepository.sendBroadcast(text)
            _state.update { it.copy(isSending = false, isSent = true) }
            // Auto-reset after showing success so user can send another
            delay(3000)
            _state.update { it.copy(isSent = false, message = "") }
        }
    }
}
