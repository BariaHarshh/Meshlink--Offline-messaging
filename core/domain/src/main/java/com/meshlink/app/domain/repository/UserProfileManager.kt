package com.meshlink.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Provides access to the local user's display name.
 * Implementation reads from and persists to SharedPreferences.
 */
interface UserProfileManager {
    /** Returns the current user display name. Re-reads from storage each call. */
    fun getDisplayName(): String

    /** Reactive StateFlow emitting the current user display name and subsequent updates. */
    val displayNameFlow: StateFlow<String>

    /** Updates the user's display name and persists it safely. */
    fun setDisplayName(name: String)
}
