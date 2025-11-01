package com.example.whopaid.models

/**
 * Firestore representation of a Group.
 *
 * - id: document id for the group (same as Firestore doc id)
 * - name: human readable group name
 * - description: optional
 * - adminUid: uid of the user who is the admin/creator
 * - members: list of user UIDs that belong to this group
 * - createdAt: timestamp in milliseconds
 */
data class Group(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val adminUid: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = 0L
)
