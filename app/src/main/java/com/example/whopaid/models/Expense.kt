package com.example.whopaid.models

/**
 * Firestore model for a group expense, with currency support.
 */
data class Expense(
    val id: String = "",
    val title: String = "",
    val amountRaw: Double = 0.0,          // amount in the currency selected
    val currencyCode: String = "RON",     // ISO currency code, default RON
    val amountInGroupCurrency: Double = 0.0, // amount converted into group standard currency (RON)
    val payerUid: String = "",
    val participants: List<String> = emptyList(),
    val createdAt: Long = 0L
)
