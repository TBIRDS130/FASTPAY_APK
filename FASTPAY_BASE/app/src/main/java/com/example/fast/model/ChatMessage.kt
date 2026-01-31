package com.example.fast.model

data class ChatMessage(
    val id: String,
    val body: String,
    val timestamp: Long,
    val isReceived: Boolean, // true if received from contact, false if sent to contact
    val address: String // phone number of the other party
)

