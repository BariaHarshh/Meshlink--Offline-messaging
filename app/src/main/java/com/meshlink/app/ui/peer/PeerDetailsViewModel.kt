package com.meshlink.app.ui.peer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.VerificationStatus
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.NearbyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class PeerDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository,
    private val nearbyRepository: NearbyRepository
) : ViewModel() {

    val deviceId: String = checkNotNull(savedStateHandle["deviceId"])
    val deviceName: String = (savedStateHandle.get<String>("deviceName"))?.let {
        URLDecoder.decode(it, "UTF-8")
    } ?: deviceId

    val peerDevice: StateFlow<KnownDevice?> = deviceRepository.getAllDevices()
        .map { list ->
            list.find { it.deviceId == deviceId || it.displayName.equals(deviceName, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connectionState: StateFlow<ConnectionState> = nearbyRepository.connectionStates
        .map { states ->
            states[deviceId] ?: states[nearbyRepository.currentEndpointForName(deviceName)] ?: ConnectionState.DISCONNECTED
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.DISCONNECTED)

    fun formattedPublicKey(device: KnownDevice?): String {
        if (device == null || device.publicKey.isEmpty()) {
            val raw = deviceId.uppercase()
            if (raw.length <= 16) return raw.chunked(2).joinToString(":")
            val head = raw.take(8).chunked(2).joinToString(":")
            val tail = raw.takeLast(4).chunked(2).joinToString(":")
            return "$head:...:$tail"
        }
        val hex = device.publicKey.joinToString("") { "%02X".format(it) }
        val head = hex.take(8).chunked(2).joinToString(":")
        val tail = hex.takeLast(4).chunked(2).joinToString(":")
        return "$head:...:$tail"
    }

    fun verifyPeer(device: KnownDevice) {
        viewModelScope.launch {
            deviceRepository.updateVerificationStatus(device.deviceId, VerificationStatus.VERIFIED)
        }
    }

    fun disconnect() {
        val endpoint = nearbyRepository.currentEndpointForName(deviceName) ?: deviceId
        nearbyRepository.disconnect(endpoint)
    }
}
