package com.example.fast.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.fast.viewmodel.ChatActivityViewModel
import com.example.fast.viewmodel.MainActivityViewModel
import com.example.fast.ui.activated.ActivatedViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import javax.inject.Provider

/**
 * Hilt module for providing ViewModels
 * 
 * This module binds ViewModelFactory and provides ViewModels
 * that require constructor injection.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class ViewModelModule {
    
    /**
     * Bind ViewModelFactory for creating ViewModels with dependencies
     */
    @Binds
    abstract fun bindViewModelFactory(factory: FastPayViewModelFactory): ViewModelProvider.Factory
}

/**
 * Custom ViewModelFactory that uses Hilt to inject dependencies
 */
class FastPayViewModelFactory @Inject constructor(
    private val creators: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass] ?: creators.entries.firstOrNull {
            modelClass.isAssignableFrom(it.key)
        }?.value ?: throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        
        return try {
            creator.get() as T
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
