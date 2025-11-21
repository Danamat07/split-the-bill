package com.example.whopaid.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

data class SharedLocation(
    val uid: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val updatedAt: Timestamp? = null,
    val isSharing: Boolean = false
)

class LocationRepository {
    private val db = FirebaseFirestore.getInstance()

    /**
     * Write or update the user's current location for a specific group.
     * groupId: group document id
     * uid: user id
     */
    suspend fun setLocationForGroup(groupId: String, uid: String, name: String, lat: Double, lng: Double) {
        val docRef = db.collection("groups")
            .document(groupId)
            .collection("locations")
            .document(uid)

        val payload = mapOf(
            "uid" to uid,
            "name" to name,
            "lat" to lat,
            "lng" to lng,
            "updatedAt" to Timestamp.now(),
            "isSharing" to true
        )

        docRef.set(payload).await()
    }

    /**
     * Mark the user as not sharing (keeps doc but sets isSharing=false and clears coords)
     */
    suspend fun stopSharingForGroup(groupId: String, uid: String) {
        val docRef = db.collection("groups")
            .document(groupId)
            .collection("locations")
            .document(uid)

        val payload = mapOf(
            "isSharing" to false,
            "updatedAt" to Timestamp.now()
        )
        docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    /**
     * Observe live locations for a group. Returns ListenerRegistration that caller must remove.
     * callback receives the list of SharedLocation
     */
    fun observeGroupLocations(groupId: String, callback: (List<SharedLocation>) -> Unit): ListenerRegistration {
        val colRef = db.collection("groups")
            .document(groupId)
            .collection("locations")

        val listener = colRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                callback(emptyList())
                return@addSnapshotListener
            }

            val list = snap.documents.mapNotNull { doc ->
                try {
                    val uid = doc.getString("uid") ?: doc.id
                    val name = doc.getString("name") ?: ""
                    val lat = doc.getDouble("lat") ?: 0.0
                    val lng = doc.getDouble("lng") ?: 0.0
                    val updatedAt = doc.getTimestamp("updatedAt")
                    val isSharing = doc.getBoolean("isSharing") ?: false
                    SharedLocation(uid, name, lat, lng, updatedAt, isSharing)
                } catch (e: Exception) {
                    null
                }
            }
            callback(list)
        }
        return listener
    }
}
