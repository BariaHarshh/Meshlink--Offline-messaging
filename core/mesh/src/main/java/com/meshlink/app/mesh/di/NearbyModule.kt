package com.meshlink.app.mesh.di

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.meshlink.app.crypto.identity.KeyManager
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.mesh.battery.AdaptiveScanController
import com.meshlink.app.mesh.repository.NearbyRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NearbyProvideModule {

    @Provides
    @Singleton
    fun provideConnectionsClient(@ApplicationContext context: Context): ConnectionsClient =
        Nearby.getConnectionsClient(context)

    /**
     * Phase 3: device identity is now derived from the EC P-256 public key.
     * deviceId = SHA-256(publicKey).take(16 hex chars)
     * This replaces the previous ANDROID_ID approach — identities are now
     * cryptographically bound to the key pair stored in EncryptedSharedPreferences.
     */
    @Provides
    @Singleton
    @Named("localDeviceId")
    fun provideLocalDeviceId(keyManager: KeyManager): String = keyManager.deviceId

    @Provides
    @Singleton
    fun provideAdaptiveScanController(): AdaptiveScanController = AdaptiveScanController()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyBindModule {

    @Binds
    @Singleton
    abstract fun bindNearbyRepository(impl: NearbyRepositoryImpl): NearbyRepository
}
