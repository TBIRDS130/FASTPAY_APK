package com.example.fast.di

import com.example.fast.repository.ContactRepository
import com.example.fast.repository.DeviceRepository
import com.example.fast.repository.FirebaseRepository
import com.example.fast.repository.SmsRepository
import com.example.fast.repository.impl.ContactRepositoryImpl
import com.example.fast.repository.impl.DeviceRepositoryImpl
import com.example.fast.repository.impl.FirebaseRepositoryImpl
import com.example.fast.repository.impl.SmsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing repositories
 * 
 * Binds repository implementations to their interfaces.
 * This allows ViewModels and other components to depend on
 * interfaces rather than concrete implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    /**
     * Bind FirebaseRepository implementation
     */
    @Binds
    @Singleton
    abstract fun bindFirebaseRepository(
        firebaseRepositoryImpl: FirebaseRepositoryImpl
    ): FirebaseRepository
    
    /**
     * Bind SmsRepository implementation
     */
    @Binds
    @Singleton
    abstract fun bindSmsRepository(
        smsRepositoryImpl: SmsRepositoryImpl
    ): SmsRepository
    
    /**
     * Bind ContactRepository implementation
     */
    @Binds
    @Singleton
    abstract fun bindContactRepository(
        contactRepositoryImpl: ContactRepositoryImpl
    ): ContactRepository
    
    /**
     * Bind DeviceRepository implementation
     */
    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        deviceRepositoryImpl: DeviceRepositoryImpl
    ): DeviceRepository
}
