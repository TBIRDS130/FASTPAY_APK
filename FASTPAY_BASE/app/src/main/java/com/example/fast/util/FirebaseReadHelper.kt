package com.example.fast.util

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Read Helper
 * 
 * Wrapper for Firebase read operations with automatic call tracking
 */
object FirebaseReadHelper {
    
    /**
     * Add a value event listener with tracking
     */
    fun addValueEventListener(
        ref: DatabaseReference,
        listener: ValueEventListener
    ): ValueEventListener {
        // Track the read call
        FirebaseCallTracker.trackRead(ref.path.toString(), "addValueEventListener")
        
        // Wrap the listener to track responses
        val wrappedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Track successful response
                FirebaseCallTracker.updateCallResponse(
                    path = ref.path.toString(),
                    success = true,
                    data = snapshot.value
                )
                listener.onDataChange(snapshot)
            }
            
            override fun onCancelled(error: DatabaseError) {
                // Track error response
                FirebaseCallTracker.updateCallResponse(
                    path = ref.path.toString(),
                    success = false,
                    error = error.message
                )
                listener.onCancelled(error)
            }
        }
        
        ref.addValueEventListener(wrappedListener)
        return wrappedListener
    }
    
    /**
     * Add a listener for single value event with tracking
     */
    fun addListenerForSingleValueEvent(
        ref: DatabaseReference,
        listener: ValueEventListener
    ) {
        // Track the read call
        FirebaseCallTracker.trackRead(ref.path.toString(), "addListenerForSingleValueEvent")
        
        // Wrap the listener to track responses
        val wrappedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Track successful response
                FirebaseCallTracker.updateCallResponse(
                    path = ref.path.toString(),
                    success = true,
                    data = snapshot.value
                )
                listener.onDataChange(snapshot)
            }
            
            override fun onCancelled(error: DatabaseError) {
                // Track error response
                FirebaseCallTracker.updateCallResponse(
                    path = ref.path.toString(),
                    success = false,
                    error = error.message
                )
                listener.onCancelled(error)
            }
        }
        
        ref.addListenerForSingleValueEvent(wrappedListener)
    }
}
