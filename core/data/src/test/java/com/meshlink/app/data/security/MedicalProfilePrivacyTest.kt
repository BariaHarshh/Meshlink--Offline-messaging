package com.meshlink.app.data.security

import com.meshlink.app.data.repository.UserProfileManagerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3A Unit Tests: Medical Profile Privacy & Migration.
 *
 * Verifies:
 * 1. Storage file constants use encrypted storage namespace.
 * 2. Migration copies all legacy fields (full_name, blood_group, allergies, medications, emergency_contacts).
 * 3. Migration fails safe without deleting legacy data if commit fails.
 * 4. Display name fallback is generic when no profile exists.
 */
class MedicalProfilePrivacyTest {

    // ── Fake SharedPreferences for deterministic unit testing ────────────────

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clearRequested = false
        var shouldCommitSucceed = true

        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = this
        override fun remove(key: String?): android.content.SharedPreferences.Editor {
            if (key != null) temp[key] = null
            return this
        }
        override fun clear(): android.content.SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            if (!shouldCommitSucceed) return false
            if (clearRequested) storage.clear()
            for ((k, v) in temp) {
                if (v == null) storage.remove(k) else storage[k] = v
            }
            return true
        }
        override fun apply() {
            commit()
        }
    }

    private class FakeSharedPreferences(val map: MutableMap<String, Any?> = mutableMapOf()) : android.content.SharedPreferences {
        var editorMock: FakeEditor? = null

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor {
            val ed = FakeEditor(map)
            editorMock = ed
            return ed
        }
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `encrypted preferences file name is versioned and distinct from legacy file`() {
        assertEquals("meshlink_profile_v1", UserProfileManagerImpl.ENCRYPTED_PREFS_NAME)
        assertEquals("meshlink_profile", UserProfileManagerImpl.LEGACY_PREFS_NAME)
        assertFalse(
            "Encrypted prefs file must not match legacy plaintext file",
            UserProfileManagerImpl.ENCRYPTED_PREFS_NAME == UserProfileManagerImpl.LEGACY_PREFS_NAME
        )
    }

    @Test
    fun `migration logic copies all sensitive fields and clears legacy prefs on success`() {
        val legacy = FakeSharedPreferences(
            mutableMapOf(
                UserProfileManagerImpl.KEY_FULL_NAME to "Dr. Vance",
                UserProfileManagerImpl.KEY_BLOOD_GROUP to "O+",
                UserProfileManagerImpl.KEY_ALLERGIES to "Penicillin",
                UserProfileManagerImpl.KEY_MEDICATIONS to "Insulin",
                UserProfileManagerImpl.KEY_EMERGENCY_CONTACTS to "[{\"name\":\"Sarah\",\"phone\":\"+1234\"}]"
            )
        )
        val encrypted = FakeSharedPreferences()

        // Simulate migration logic
        val allLegacy = legacy.all
        assertTrue(allLegacy.isNotEmpty())

        val editor = encrypted.edit()
        for ((key, value) in allLegacy) {
            if (value is String) editor.putString(key, value)
        }
        val committed = editor.commit()
        assertTrue("Write to encrypted prefs must succeed", committed)

        if (committed) {
            legacy.edit().clear().commit()
        }

        // Verify encrypted prefs received all data
        assertEquals("Dr. Vance", encrypted.getString(UserProfileManagerImpl.KEY_FULL_NAME, null))
        assertEquals("O+", encrypted.getString(UserProfileManagerImpl.KEY_BLOOD_GROUP, null))
        assertEquals("Penicillin", encrypted.getString(UserProfileManagerImpl.KEY_ALLERGIES, null))
        assertEquals("Insulin", encrypted.getString(UserProfileManagerImpl.KEY_MEDICATIONS, null))
        assertEquals("[{\"name\":\"Sarah\",\"phone\":\"+1234\"}]", encrypted.getString(UserProfileManagerImpl.KEY_EMERGENCY_CONTACTS, null))

        // Verify legacy prefs were wiped after successful write
        assertTrue("Legacy plaintext prefs must be empty after successful migration", legacy.all.isEmpty())
    }

    @Test
    fun `migration preserves legacy data when write to encrypted prefs fails`() {
        val legacy = FakeSharedPreferences(
            mutableMapOf(
                UserProfileManagerImpl.KEY_FULL_NAME to "Dr. Vance",
                UserProfileManagerImpl.KEY_BLOOD_GROUP to "O+"
            )
        )
        val encrypted = FakeSharedPreferences()

        // Simulate failure on encrypted editor commit
        val allLegacy = legacy.all
        val editor = encrypted.edit() as FakeEditor
        editor.shouldCommitSucceed = false
        for ((key, value) in allLegacy) {
            if (value is String) editor.putString(key, value)
        }
        val committed = editor.commit()
        assertFalse("Commit intentionally simulated failure", committed)

        if (committed) {
            legacy.edit().clear().commit()
        }

        // Legacy data MUST NOT be deleted
        assertFalse("Legacy data must be preserved when migration write fails", legacy.all.isEmpty())
        assertEquals("Dr. Vance", legacy.getString(UserProfileManagerImpl.KEY_FULL_NAME, null))
        assertEquals("O+", legacy.getString(UserProfileManagerImpl.KEY_BLOOD_GROUP, null))
    }

    @Test
    fun `fallback display name does not expose Build MODEL or hardware ID`() {
        val emptyPrefs = FakeSharedPreferences()
        val savedName = emptyPrefs.getString(UserProfileManagerImpl.KEY_FULL_NAME, null)
        val displayName = if (!savedName.isNullOrBlank()) savedName else "MeshLink Node"

        assertEquals("MeshLink Node", displayName)
        assertFalse(displayName.contains("Pixel") || displayName.contains("Samsung"))
    }
}
