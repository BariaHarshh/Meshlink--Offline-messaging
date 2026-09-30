package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.VerificationStatus
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun getAllDevices(): Flow<List<KnownDevice>>
    suspend fun upsertDevice(device: KnownDevice)
    /** Returns the [KnownDevice] for [deviceId], or null if not yet encountered. */
    suspend fun getDeviceById(deviceId: String): KnownDevice?

    /** Update the human-readable display name for a known device (rename feature). */
    suspend fun updateDisplayName(deviceId: String, newName: String)

    /** Update peer verification / trust status (e.g., VERIFIED, UNVERIFIED, REVOKED). */
    suspend fun updateVerificationStatus(deviceId: String, status: VerificationStatus)
}
