package com.example.whopaid.models

/**
 * Data model representing a user in Firestore.
 */


data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = ""
)
