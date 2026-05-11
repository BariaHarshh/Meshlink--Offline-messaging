package com.meshlink.app.ui.medical

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.EmergencyContact
import com.meshlink.app.domain.repository.NearbyRepository
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
        // Pre-populated demo contacts matching the Figma design
        EmergencyContact(name = "Sarah Miller", relation = "Spouse",  phone = "+1 (555) 012-3456"),
        EmergencyContact(name = "David Vance",  relation = "Father",  phone = "+1 (555) 098-7654")
    ),
    val isSaved: Boolean = false
)

val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

@HiltViewModel
class MedicalProfileViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val nearbyRepository: NearbyRepository
) : ViewModel() {

    companion object {
        const val PREFS_NAME = "meshlink_profile"
        const val KEY_FULL_NAME          = "full_name"
        const val KEY_BLOOD_GROUP        = "blood_group"
        const val KEY_ALLERGIES          = "allergies"
        const val KEY_MEDICATIONS        = "medications"
        const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

        // Persist to SharedPreferences (including emergency contacts)
        prefs.edit()
            .putString(KEY_FULL_NAME,          current.fullName)
            .putString(KEY_BLOOD_GROUP,        current.bloodGroup)
            .putString(KEY_ALLERGIES,          current.allergies)
            .putString(KEY_MEDICATIONS,        current.medications)
            .putString(KEY_EMERGENCY_CONTACTS, contactsJson)
            .apply()

        _state.update { it.copy(isSaved = true) }

        // Restart advertising only (not discovery) so the updated display name
        // takes effect without killing existing peer connections.
        nearbyRepository.restartAdvertisingOnly()

        // Emit navigation-back signal after a short delay so user sees "Profile Saved"
        viewModelScope.launch {
            delay(800)
            _profileSaved.emit(Unit)
        }
    }

    private fun loadProfile(): MedicalProfileState {
        val savedName = prefs.getString(KEY_FULL_NAME, null)

        // Deserialize emergency contacts from JSON
        val contacts = prefs.getString(KEY_EMERGENCY_CONTACTS, null)?.let { json ->
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
            // Default demo contacts (only used on first launch)
            EmergencyContact(name = "Sarah Miller", relation = "Spouse",  phone = "+1 (555) 012-3456"),
            EmergencyContact(name = "David Vance",  relation = "Father",  phone = "+1 (555) 098-7654")
        )

        return MedicalProfileState(
            fullName          = savedName ?: "ALEXANDER VANCE",
            bloodGroup        = prefs.getString(KEY_BLOOD_GROUP, "") ?: "",
            allergies         = prefs.getString(KEY_ALLERGIES, "")   ?: "",
            medications       = prefs.getString(KEY_MEDICATIONS, "") ?: "",
            emergencyContacts = contacts,
            isSaved           = savedName != null  // mark as saved if loaded from prefs
        )
    }
}
