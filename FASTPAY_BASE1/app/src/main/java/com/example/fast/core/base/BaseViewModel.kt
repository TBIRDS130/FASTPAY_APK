package com.example.fast.core.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fast.core.error.FastPayException
import com.example.fast.core.result.Result
import com.example.fast.util.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base ViewModel class for all ViewModels in the FastPay app.
 * Provides common functionality:
 * - Loading state management
 * - Error handling
 * - Coroutine scope with exception handling
 * - Result wrapping utilities
 */
abstract class BaseViewModel : ViewModel() {

    protected open val TAG: String
        get() = this::class.java.simpleName

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error state
    private val _error = MutableLiveData<FastPayException?>()
    val error: LiveData<FastPayException?> = _error

    // Generic message for UI feedback
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    /**
     * Default coroutine exception handler.
     * Override to customize error handling.
     */
    protected open val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e(TAG, throwable as? Exception, "Coroutine exception")
        _isLoading.postValue(false)
        _error.postValue(
            when (throwable) {
                is FastPayException -> throwable
                else -> FastPayException(
                    message = throwable.message ?: "Unknown error",
                    cause = throwable
                )
            }
        )
    }

    /**
     * Launch a coroutine with loading state and error handling.
     */
    protected fun launchWithLoading(block: suspend () -> Unit): Job {
        return viewModelScope.launch(exceptionHandler) {
            try {
                _isLoading.value = true
                _error.value = null
                block()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Launch a coroutine without loading state (for background operations).
     */
    protected fun launchSilent(block: suspend () -> Unit): Job {
        return viewModelScope.launch(exceptionHandler) {
            block()
        }
    }

    /**
     * Handle a Result and update error state if needed.
     * Returns the data on success, null on error.
     */
    protected fun <T> handleResult(result: Result<T>): T? {
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> {
                _error.value = result.exception
                null
            }
        }
    }

    /**
     * Set loading state manually.
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    /**
     * Set error state manually.
     */
    protected fun setError(exception: FastPayException?) {
        _error.value = exception
    }

    /**
     * Set a UI message.
     */
    protected fun setMessage(msg: String?) {
        _message.value = msg
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clear message state.
     */
    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d(TAG, "onCleared")
    }
}
