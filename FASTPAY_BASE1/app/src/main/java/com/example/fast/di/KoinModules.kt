package com.example.fast.di

import android.content.Context
import com.example.fast.domain.usecase.ActivateDeviceUseCase
import com.example.fast.domain.usecase.FetchDeviceInfoUseCase
import com.example.fast.domain.usecase.SendSmsUseCase
import com.example.fast.domain.usecase.SyncContactsUseCase
import com.example.fast.repository.ContactRepository
import com.example.fast.repository.DeviceRepository
import com.example.fast.repository.FirebaseRepository
import com.example.fast.repository.SmsRepository
import com.example.fast.repository.impl.ContactRepositoryImpl
import com.example.fast.repository.impl.DeviceRepositoryImpl
import com.example.fast.repository.impl.FirebaseRepositoryImpl
import com.example.fast.repository.impl.SmsRepositoryImpl
import com.example.fast.ui.activated.ActivatedViewModel
import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.VersionChecker
import com.example.fast.util.firebase.FirebaseListenerManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin modules for FastPay (replaces Hilt).
 * - appModule: context, Firebase, repositories, use cases, utilities, ViewModels
 */
val appModule = module {

    // Context & Application (single)
    single<Context> { androidContext() }
    single<android.app.Application> { androidContext() as android.app.Application }

    // Firebase
    single<DatabaseReference> { FirebaseDatabase.getInstance().reference }
    single<StorageReference> { FirebaseStorage.getInstance().reference }

    // Utilities (injectable for testability)
    single { DjangoApiHelper }
    single { VersionChecker }
    single { FirebaseListenerManager.getInstance(get()) }

    // SharedPreferences (named for different prefs)
    single(named("activation_prefs")) {
        get<Context>().getSharedPreferences("activation_prefs", Context.MODE_PRIVATE)
    }
    single(named("activation_retry_prefs")) {
        get<Context>().getSharedPreferences("activation_retry_prefs", Context.MODE_PRIVATE)
    }

    // Repositories
    single<FirebaseRepository> { FirebaseRepositoryImpl() }
    single<SmsRepository> { SmsRepositoryImpl(get()) }
    single<ContactRepository> { ContactRepositoryImpl(get()) }
    single<DeviceRepository> { DeviceRepositoryImpl(get(), get()) }

    // Dispatchers (named, for optional use)
    single<CoroutineDispatcher>(named("Default")) { Dispatchers.Default }
    single<CoroutineDispatcher>(named("IO")) { Dispatchers.IO }
    single<CoroutineDispatcher>(named("Main")) { Dispatchers.Main }

    // Use cases
    single { SendSmsUseCase(get()) }
    single { SyncContactsUseCase(get(), get()) }
    single { ActivateDeviceUseCase(get(), get()) }
    single { FetchDeviceInfoUseCase(get(), get()) }

    // ViewModels (Application + deps from get())
    viewModel { ActivatedViewModel() }
}
