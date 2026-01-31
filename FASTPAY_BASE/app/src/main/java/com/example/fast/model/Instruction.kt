package com.example.fast.model

/**
 * Data class for instruction items
 */
data class Instruction(
    val id: String,
    val title: String,
    val text: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null
)
