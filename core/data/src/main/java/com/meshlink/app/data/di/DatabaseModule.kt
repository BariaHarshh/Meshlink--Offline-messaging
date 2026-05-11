package com.meshlink.app.data.di

import android.content.Context
import androidx.room.Room
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.dao.PendingMessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "meshlink.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun providePendingMessageDao(db: AppDatabase): PendingMessageDao = db.pendingMessageDao()
}
