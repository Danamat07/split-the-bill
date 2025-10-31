package com.example.whopaid.models

/**
 * Simple data model representing a group stored in Firestore.
 *
 * - id: document id (also stored in the doc as convenience)
 * - name: group display name
 * - ownerUid: UID of user who created the group
 * - members: list of user UIDs who joined the group
 */
data class Group(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val members: List<String> = emptyList()
)
