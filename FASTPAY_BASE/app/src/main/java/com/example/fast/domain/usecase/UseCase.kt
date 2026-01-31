package com.example.fast.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import com.example.fast.di.DefaultDispatcher
import javax.inject.Inject

/**
 * Base class for use cases
 * 
 * Use cases encapsulate business logic and coordinate between
 * repositories and other data sources.
 * 
 * @param P Input parameters type
 * @param R Result type
 */
abstract class UseCase<in P, R> {
    
    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher
    
    /**
     * Execute the use case
     * 
     * @param parameters Input parameters
     * @return Result of the operation
     */
    suspend operator fun invoke(parameters: P): R {
        return execute(parameters)
    }
    
    /**
     * Execute the use case (to be implemented by subclasses)
     */
    protected abstract suspend fun execute(parameters: P): R
}

/**
 * Base class for use cases that return Flow
 */
abstract class FlowUseCase<in P, R> {
    
    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher
    
    /**
     * Execute the use case
     */
    operator fun invoke(parameters: P): Flow<R> {
        return execute(parameters).flowOn(defaultDispatcher)
    }
    
    /**
     * Execute the use case (to be implemented by subclasses)
     */
    protected abstract fun execute(parameters: P): Flow<R>
}

/**
 * Base class for use cases with no parameters
 */
abstract class NoParamsUseCase<R> {
    
    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher
    
    suspend operator fun invoke(): R {
        return execute()
    }
    
    protected abstract suspend fun execute(): R
}
