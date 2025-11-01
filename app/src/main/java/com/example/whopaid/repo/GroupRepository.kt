package com.example.whopaid.repo

import com.example.whopaid.models.Group
import com.example.whopaid.models.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * GroupRepository encapsulates Firestore operations for groups:
 * - create group
 * - add member by email (admin-only operation)
 * - leave group (member)
 * - delete group (admin-only)
 *
 * Note: Many operations use FieldValue.arrayUnion / arrayRemove to keep membership arrays consistent
 * on each document. For large-scale production you may want batched writes or Cloud Functions.
 */
class GroupRepository {

    private val db = FirebaseFirestore.getInstance()

    private val groupsCol = db.collection("groups")
    private val usersCol = db.collection("users")

    /**
     * Create a new group. The creator becomes admin and is added as the first member.
     *
     * Returns Result<Group> with the created group (including generated id).
     */
    suspend fun createGroup(name: String, description: String, admin: User): Result<Group> {
        return try {
            // create empty doc to get id
            val docRef = groupsCol.document()
            val groupId = docRef.id

            val group = Group(
                id = groupId,
                name = name,
                description = description,
                adminUid = admin.uid,
                members = listOf(admin.uid),
                createdAt = System.currentTimeMillis()
            )

            // write group doc
            docRef.set(group).await()

            // update user's groups array to include this groupId
            usersCol.document(admin.uid)
                .update("groups", FieldValue.arrayUnion(groupId))
                .addOnFailureListener {
                    // If user doc didn't have 'groups' field (or user missing), we set the field
                    usersCol.document(admin.uid).set(mapOf("groups" to listOf(groupId)), com.google.firebase.firestore.SetOptions.merge())
                }.await()

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a member to a group by their email address. Only an admin should call this from UI.
     *
     * Steps:
     * 1. Query users collection for document with that email.
     * 2. If found, update groups/{groupId}.members with arrayUnion(uid)
     * 3. Update users/{uid}.groups with arrayUnion(groupId)
     */
    suspend fun addMemberByEmail(groupId: String, userEmail: String): Result<Unit> {
        return try {
            // find user by email
            val querySnapshot = usersCol.whereEqualTo("email", userEmail).get().await()
            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("No user found with that email"))
            }
            val userDoc = querySnapshot.documents[0]
            val uid = userDoc.id

            // Update group members and user groups
            groupsCol.document(groupId).update("members", FieldValue.arrayUnion(uid)).await()
            usersCol.document(uid).update("groups", FieldValue.arrayUnion(groupId)).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Member leaves a group: remove userUid from group's members and remove groupId from user's groups.
     */
    suspend fun leaveGroup(groupId: String, userUid: String): Result<Unit> {
        return try {
            groupsCol.document(groupId).update("members", FieldValue.arrayRemove(userUid)).await()
            usersCol.document(userUid).update("groups", FieldValue.arrayRemove(groupId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a group (admin-only).
     * Steps:
     * 1. Fetch group doc to obtain members list
     * 2. For each member, remove groupId from users/{uid}.groups
     * 3. Delete the group document
     *
     * Note: This function updates user documents sequentially; for many members consider Firestore batch or Cloud Function.
     */
    suspend fun deleteGroup(groupId: String): Result<Unit> {
        return try {
            val snapshot = groupsCol.document(groupId).get().await()
            if (!snapshot.exists()) return Result.failure(Exception("Group not found"))

            val members = snapshot.get("members") as? List<*> ?: emptyList<Any>()

            // Remove groupId from each member's groups array
            for (m in members) {
                val uid = m as? String ?: continue
                usersCol.document(uid).update("groups", FieldValue.arrayRemove(groupId)).await()
            }

            // Delete group doc
            groupsCol.document(groupId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes a member from a group (admin-only operation).
     * Steps:
     * 1. Remove memberUid from group's members array
     * 2. Remove groupId from user's groups array
     */
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
