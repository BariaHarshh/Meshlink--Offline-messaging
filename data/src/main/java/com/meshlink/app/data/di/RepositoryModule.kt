package com.meshlink.app.data.di

import com.meshlink.app.data.repository.DeviceRepositoryImpl
import com.meshlink.app.data.repository.MessageRepositoryImpl
import com.meshlink.app.data.repository.PendingMessageRepositoryImpl
import com.meshlink.app.data.repository.UserProfileManagerImpl
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    abstract fun bindPendingMessageRepository(impl: PendingMessageRepositoryImpl): PendingMessageRepository

    @Binds
    abstract fun bindUserProfileManager(impl: UserProfileManagerImpl): UserProfileManager
}
