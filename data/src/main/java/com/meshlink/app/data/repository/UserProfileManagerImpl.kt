package com.meshlink.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.meshlink.app.domain.repository.UserProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserProfileManager {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meshlink_profile", Context.MODE_PRIVATE)

    override fun getDisplayName(): String {
        val savedName = prefs.getString("full_name", null)
        return if (!savedName.isNullOrBlank()) savedName else Build.MODEL
    }
}
