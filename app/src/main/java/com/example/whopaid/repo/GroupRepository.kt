package com.example.whopaid.repo

import com.example.whopaid.models.Group
import com.example.whopaid.models.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class GroupRepository {

    private val db = FirebaseFirestore.getInstance()
    private val groupsCol = db.collection("groups")
    private val usersCol = db.collection("users")

    suspend fun createGroup(name: String, description: String, admin: User): Result<Group> {
        return try {
            val docRef = groupsCol.document()
            val groupId = docRef.id

            // Generează payload-ul QR automat
            val qrPayload = "JOIN_GROUP:$groupId"

            val group = Group(
                id = groupId,
                name = name,
                description = description,
                adminUid = admin.uid,
                members = listOf(admin.uid),
                qrPayload = qrPayload
            )

            // Scrie documentul grupului
            docRef.set(group).await()

            // Actualizează array-ul groups al utilizatorului
            usersCol.document(admin.uid)
                .update("groups", FieldValue.arrayUnion(groupId))
                .addOnFailureListener {
                    usersCol.document(admin.uid).set(
                        mapOf("groups" to listOf(groupId)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }.await()

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMemberByEmail(groupId: String, userEmail: String): Result<Unit> {
        return try {
            val querySnapshot = usersCol.whereEqualTo("email", userEmail).get().await()
            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("No user found with that email"))
            }
            val uid = querySnapshot.documents[0].id

            groupsCol.document(groupId).update("members", FieldValue.arrayUnion(uid)).await()
            usersCol.document(uid).update("groups", FieldValue.arrayUnion(groupId)).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String, userUid: String): Result<Unit> {
        return try {
            groupsCol.document(groupId).update("members", FieldValue.arrayRemove(userUid)).await()
            usersCol.document(userUid).update("groups", FieldValue.arrayRemove(groupId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(groupId: String): Result<Unit> {
        return try {
            val snapshot = groupsCol.document(groupId).get().await()
            if (!snapshot.exists()) return Result.failure(Exception("Group not found"))

            val members = snapshot.get("members") as? List<*> ?: emptyList<Any>()

            for (m in members) {
                val uid = m as? String ?: continue
                usersCol.document(uid).update("groups", FieldValue.arrayRemove(groupId)).await()
            }

            groupsCol.document(groupId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeMember(groupId: String, memberUid: String): Result<Unit> {
        return try {
            groupsCol.document(groupId).update("members", FieldValue.arrayRemove(memberUid)).await()
            usersCol.document(memberUid).update("groups", FieldValue.arrayRemove(groupId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
