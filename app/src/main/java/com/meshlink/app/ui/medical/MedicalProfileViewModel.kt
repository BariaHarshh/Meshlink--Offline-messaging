package com.meshlink.app.ui.medical

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.data.repository.UserProfileManagerImpl
import com.meshlink.app.domain.model.EmergencyContact
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.UserProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

data class MedicalProfileState(
    val fullName:          String                = "",
    val bloodGroup:        String                = "",
    val allergies:         String                = "",
    val medications:       String                = "",
    val emergencyContacts: List<EmergencyContact> = listOf(
        EmergencyContact(name = "Sarah Miller", relation = "Spouse",  phone = "+1 (555) 012-3456"),
        EmergencyContact(name = "David Vance",  relation = "Father",  phone = "+1 (555) 098-7654")
    ),
    val isSaved: Boolean = false
)

val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

@HiltViewModel
class MedicalProfileViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val nearbyRepository:   NearbyRepository,
    private val userProfileManager: UserProfileManager
) : ViewModel() {

    private val prefs: SharedPreferences by lazy {
        UserProfileManagerImpl.getEncryptedProfilePrefs(context)
    }

    /** Emits Unit when profile is saved and the screen should navigate back. */
    private val _profileSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val profileSaved: SharedFlow<Unit> = _profileSaved.asSharedFlow()

    private val _state = MutableStateFlow(loadProfile())
    val state: StateFlow<MedicalProfileState> = _state

    fun onNameChanged(name: String)        { _state.update { it.copy(fullName    = name,        isSaved = false) } }
    fun onBloodGroupSelected(bg: String)   { _state.update { it.copy(bloodGroup  = bg,          isSaved = false) } }
    fun onAllergiesChanged(text: String)   { _state.update { it.copy(allergies   = text,        isSaved = false) } }
    fun onMedicationsChanged(text: String) { _state.update { it.copy(medications = text,        isSaved = false) } }

    fun addContact(name: String, relation: String, phone: String) {
        _state.update { s ->
            s.copy(
                emergencyContacts = s.emergencyContacts + EmergencyContact(
                    id = UUID.randomUUID().toString(),
                    name = name, relation = relation, phone = phone
                ),
                isSaved = false
            )
        }
    }

    fun removeContact(id: String) {
        _state.update { s ->
            s.copy(emergencyContacts = s.emergencyContacts.filterNot { it.id == id }, isSaved = false)
        }
    }

    fun saveProfile() {
        val current = _state.value

        // Update single source of truth UserProfileManager
        if (current.fullName.isNotBlank()) {
            userProfileManager.setDisplayName(current.fullName)
        }

        // Serialize emergency contacts to JSON
        val contactsJson = JSONArray().apply {
            current.emergencyContacts.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("relation", c.relation)
                    put("phone", c.phone)
                })
            }
        }.toString()

        // Persist to EncryptedSharedPreferences (including emergency contacts)
        prefs.edit()
            .putString(UserProfileManagerImpl.KEY_FULL_NAME,          current.fullName)
            .putString(UserProfileManagerImpl.KEY_BLOOD_GROUP,        current.bloodGroup)
            .putString(UserProfileManagerImpl.KEY_ALLERGIES,          current.allergies)
            .putString(UserProfileManagerImpl.KEY_MEDICATIONS,        current.medications)
            .putString(UserProfileManagerImpl.KEY_EMERGENCY_CONTACTS, contactsJson)
            .apply()

        _state.update { it.copy(isSaved = true) }

        // Restart advertising only (not discovery)
        nearbyRepository.restartAdvertisingOnly()

        // Emit navigation-back signal after a short delay so user sees "Profile Saved"
        viewModelScope.launch {
            delay(800)
            _profileSaved.emit(Unit)
        }
    }

    private fun loadProfile(): MedicalProfileState {
        val savedName = userProfileManager.getDisplayName()

        // Deserialize emergency contacts from JSON
        val contacts = prefs.getString(UserProfileManagerImpl.KEY_EMERGENCY_CONTACTS, null)?.let { json ->
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    EmergencyContact(
                        id       = obj.optString("id", UUID.randomUUID().toString()),
                        name     = obj.getString("name"),
                        relation = obj.getString("relation"),
                        phone    = obj.getString("phone")
                    )
                }
            } catch (_: Exception) { null }
        } ?: listOf(
            EmergencyContact(name = "Sarah Miller", relation = "Spouse",  phone = "+1 (555) 012-3456"),
            EmergencyContact(name = "David Vance",  relation = "Father",  phone = "+1 (555) 098-7654")
        )

        return MedicalProfileState(
            fullName          = savedName,
            bloodGroup        = prefs.getString(UserProfileManagerImpl.KEY_BLOOD_GROUP, "") ?: "",
            allergies         = prefs.getString(UserProfileManagerImpl.KEY_ALLERGIES, "")   ?: "",
            medications       = prefs.getString(UserProfileManagerImpl.KEY_MEDICATIONS, "") ?: "",
            emergencyContacts = contacts,
            isSaved           = true
        )
    }
}
