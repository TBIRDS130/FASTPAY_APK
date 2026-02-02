package com.example.fast.di

import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Firebase dependencies
 *
 * Provides singleton instances of:
 * - DatabaseReference (Firebase Realtime Database)
 * - StorageReference (Firebase Storage)
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): DatabaseReference {
        return Firebase.database.reference
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): StorageReference {
        return Firebase.storage.reference
    }
}
