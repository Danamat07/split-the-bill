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
     * Creates a new user account and stores user profile in Firestore.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String
    ): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("User creation failed")

            // Build User model
            val user = User(
                uid = firebaseUser.uid,
                name = name,
                email = email,
                phone = phone,
                shareLocation = false,          // implicit nu împarte locația
                locationLat = null,
                locationLng = null
            )

            // Save in Firestore
            db.collection("users")
                .document(firebaseUser.uid)
                .set(user)
                .await()

            Result.success(user)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logs in a user.
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
     * Logs out the user.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Update user's location (used for live map tracking)
     */
    suspend fun updateUserLocation(uid: String, lat: Double, lng: Double): Result<Unit> {
        return try {
            val updates = mapOf(
                "locationLat" to lat,
                "locationLng" to lng
            )

            db.collection("users")
                .document(uid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Enable/disable location sharing
     */
    suspend fun updateShareLocation(uid: String, enabled: Boolean): Result<Unit> {
        return try {
            db.collection("users")
                .document(uid)
                .update("shareLocation", enabled)
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
