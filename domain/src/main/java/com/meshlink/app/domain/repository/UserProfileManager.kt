package com.meshlink.app.domain.repository

/**
 * Provides access to the local user's display name.
 * Implementation reads from SharedPreferences with Build.MODEL fallback.
 */
interface UserProfileManager {
    /** Returns the current user display name. Re-reads from storage each call. */
    fun getDisplayName(): String
}
