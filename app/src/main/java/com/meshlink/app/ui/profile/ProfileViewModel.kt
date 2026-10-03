package com.meshlink.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.UserProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileManager: UserProfileManager,
    private val nearbyRepository:   NearbyRepository,
    @Named("localDeviceId") val localDeviceId: String
) : ViewModel() {

    val userName: StateFlow<String> = userProfileManager.displayNameFlow

    private val _saveFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveFeedback: SharedFlow<String> = _saveFeedback.asSharedFlow()

    /**
     * Formats local deviceId into a clean, displayable fingerprint string
     * e.g., "8F:A2:3C:9D:...:91"
     */
    val formattedFingerprint: String
        get() {
            val raw = localDeviceId.uppercase()
            if (raw.length <= 16) {
                return raw.chunked(2).joinToString(":")
            }
            val head = raw.take(8).chunked(2).joinToString(":")
            val tail = raw.takeLast(4).chunked(2).joinToString(":")
            return "$head:...:$tail"
        }

    fun updateDisplayName(newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.length > 32) return false

        userProfileManager.setDisplayName(trimmed)
        nearbyRepository.restartAdvertisingOnly()

        viewModelScope.launch {
            _saveFeedback.emit("Name updated successfully")
        }
        return true
    }
}
