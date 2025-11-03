package com.example.whopaid.repo

import com.example.whopaid.models.Expense
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository handling Firestore operations for expenses inside a group.
 */
class ExpenseRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun expensesCollection(groupId: String) =
        db.collection("groups").document(groupId).collection("expenses")

    /**
     * Add a new expense to a group's expenses subcollection.
     */
    suspend fun addExpense(groupId: String, expense: Expense): Result<Unit> {
        return try {
            val doc = expensesCollection(groupId).document()
            val exp = expense.copy(id = doc.id, createdAt = System.currentTimeMillis())
            doc.set(exp).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all expenses for a specific group.
     */
    suspend fun getExpenses(groupId: String): Result<List<Expense>> {
        return try {
            val snapshot = expensesCollection(groupId)
                .orderBy("createdAt")
                .get().await()
            val expenses = snapshot.documents.mapNotNull { it.toObject(Expense::class.java) }
            Result.success(expenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Updates an existing expense. */
    suspend fun updateExpense(groupId: String, expense: Expense): Result<Unit> {
        return try {
            expensesCollection(groupId).document(expense.id).set(expense).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Deletes an expense. */
    suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit> {
        return try {
            expensesCollection(groupId).document(expenseId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
