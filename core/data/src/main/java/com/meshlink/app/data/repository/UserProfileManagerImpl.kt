package com.meshlink.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.meshlink.app.domain.repository.UserProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserProfileManager {

    companion object {
        const val LEGACY_PREFS_NAME = "meshlink_profile"
        const val ENCRYPTED_PREFS_NAME = "meshlink_profile_v1"
        const val KEY_FULL_NAME = "full_name"
        const val KEY_BLOOD_GROUP = "blood_group"
        const val KEY_ALLERGIES = "allergies"
        const val KEY_MEDICATIONS = "medications"
        const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"

        /**
         * Safely creates or retrieves Keystore-backed EncryptedSharedPreferences
         * and performs a one-time migration from legacy plaintext storage if present.
         */
        fun getEncryptedProfilePrefs(context: Context): SharedPreferences {
            val encryptedPrefs = createEncryptedPrefs(context)
            migrateLegacyDataIfNeeded(context, encryptedPrefs)
            return encryptedPrefs
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    ENCRYPTED_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Timber.e(e, "EncryptedSharedPreferences failed for profile, retrying")
                context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    ENCRYPTED_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }

        private fun migrateLegacyDataIfNeeded(context: Context, targetPrefs: SharedPreferences) {
            try {
                val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                val allLegacy = legacyPrefs.all
                if (allLegacy.isNotEmpty()) {
                    val editor = targetPrefs.edit()
                    for ((key, value) in allLegacy) {
                        if (value is String) {
                            editor.putString(key, value)
                        }
                    }
                    val committed = editor.commit()
                    if (committed) {
                        legacyPrefs.edit().clear().commit()
                        Timber.i("Migrated legacy medical profile data to encrypted storage")
                    } else {
                        Timber.w("Failed to commit migrated profile data — legacy data preserved")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Migration of legacy profile data encountered an error — legacy data preserved")
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        getEncryptedProfilePrefs(context)
    }

    private val _displayNameFlow: MutableStateFlow<String> by lazy {
        MutableStateFlow(getDisplayName())
    }

    override val displayNameFlow: StateFlow<String>
        get() = _displayNameFlow.asStateFlow()

    override fun getDisplayName(): String {
        val savedName = prefs.getString(KEY_FULL_NAME, null)
        return if (!savedName.isNullOrBlank()) {
            savedName
        } else {
            val model = Build.MODEL
            if (!model.isNullOrBlank() && model != "unknown") model else "Harsh"
        }
    }

    override fun setDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            prefs.edit().putString(KEY_FULL_NAME, trimmed).apply()
            _displayNameFlow.value = trimmed
        }
    }
}
