package com.example.whopaid

import com.example.whopaid.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Handles all authentication and user-related Firebase operations.
 */
class AuthRepository {

    // Firebase Authentication instance
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Firestore instance for saving user data
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Returns the currently logged-in Firebase user, or null if no one is logged in.
     */
    fun currentUser(): FirebaseUser? = auth.currentUser

    /**
     * Creates a new user in Firebase Authentication and stores their profile in Firestore.
     */
    suspend fun register(name: String, email: String, password: String, phone: String): Result<User> {
        return try {
            // Create user with Firebase Authentication
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("User creation failed")

            // Build the User object for Firestore
            val user = User(
                uid = firebaseUser.uid,
                name = name,
                email = email,
                phone = phone
            )

            // Save user data in "users" collection
            db.collection("users").document(firebaseUser.uid).set(user).await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logs in an existing user using Firebase Authentication.
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("Authentication failed")
            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logs out the current Firebase user.
     */
    fun logout() {
        auth.signOut()
    }
}