package com.example.whopaid.models

/**
 * Firestore model for a group expense.
 * Each expense belongs to a specific group (in its "expenses" subcollection).
 */
data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val payerUid: String = "",
    val participants: List<String> = emptyList(),
    val createdAt: Long = 0L
)
