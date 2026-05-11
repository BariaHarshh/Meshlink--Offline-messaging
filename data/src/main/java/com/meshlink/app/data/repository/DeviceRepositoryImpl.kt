package com.meshlink.app.data.repository

import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.mapper.toDomain
import com.meshlink.app.data.local.mapper.toEntity
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao
) : DeviceRepository {

    override fun getAllDevices(): Flow<List<KnownDevice>> =
        deviceDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertDevice(device: KnownDevice) {
        deviceDao.upsert(device.toEntity())
    }

    override suspend fun getDeviceById(deviceId: String): KnownDevice? =
        deviceDao.getById(deviceId)?.toDomain()

    override suspend fun updateDisplayName(deviceId: String, newName: String) {
        deviceDao.updateDisplayName(deviceId, newName)
    }
}
