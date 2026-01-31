package com.example.fast.repository

import com.example.fast.util.Result

/**
 * Base repository interface
 * 
 * All repositories should extend this interface to ensure consistency.
 * Provides common operations that all repositories might need.
 */
interface Repository {
    // Base interface - can be extended with common operations if needed
}

/**
 * Repository that supports CRUD operations
 */
interface CrudRepository<T, ID> : Repository {
    /**
     * Create or save an entity
     */
    suspend fun save(entity: T): Result<Unit>
    
    /**
     * Find an entity by ID
     */
    suspend fun findById(id: ID): Result<T?>
    
    /**
     * Find all entities
     */
    suspend fun findAll(): Result<List<T>>
    
    /**
     * Update an entity
     */
    suspend fun update(entity: T): Result<Unit>
    
    /**
     * Delete an entity by ID
     */
    suspend fun deleteById(id: ID): Result<Unit>
    
    /**
     * Delete all entities
     */
    suspend fun deleteAll(): Result<Unit>
}
