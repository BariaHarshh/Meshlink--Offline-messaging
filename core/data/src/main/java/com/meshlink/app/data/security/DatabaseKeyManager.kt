package com.meshlink.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — Secure Local Storage.
 *
 * Manages the SQLCipher database encryption key.
 *
 * Key lifecycle:
 *  1. On first launch a 32-byte random passphrase is generated via [SecureRandom].
 *  2. The raw bytes are Base64-encoded and written to [EncryptedSharedPreferences].
 *     The wrapping master key lives inside the Android Keystore (AES-256-GCM).
 *     The passphrase NEVER touches disk as plaintext.
 *  3. On every subsequent launch the same passphrase is retrieved and returned
 *     so SQLCipher can reopen the encrypted database.
 *
 * The passphrase is NOT hardcoded, NOT stored in plain SharedPreferences,
 * NOT stored in BuildConfig or source constants.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE  = "meshlink_db_key_v1"
        private const val KEY_DB_PASS = "db_passphrase"
        private const val KEY_LENGTH  = 32 // bytes → 256-bit passphrase
    }

    /**
     * Returns the database passphrase as a [ByteArray].
     * Generates and persists a new one if this is the first call.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val prefs = createEncryptedPrefs()
        val stored = prefs.getString(KEY_DB_PASS, null)
        return if (stored != null) {
            Base64.decode(stored, Base64.NO_WRAP)
        } else {
            val passphrase = ByteArray(KEY_LENGTH).also { SecureRandom().nextBytes(it) }
            prefs.edit()
                .putString(KEY_DB_PASS, Base64.encodeToString(passphrase, Base64.NO_WRAP))
                .apply()
            Timber.d("DatabaseKeyManager: generated new database passphrase")
            passphrase
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Some devices have a broken Keystore; wipe corrupted state and retry once.
            // This resets the DB key — the encrypted database will become inaccessible,
            // which is the safe failure mode (data is not readable without the key).
            Timber.e(e, "DatabaseKeyManager: EncryptedSharedPreferences failed, clearing and retrying")
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
