package com.example.whopaid.repo

import com.example.whopaid.models.Expense
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository handling Firestore operations for expenses including currency conversion.
 */
class ExpenseRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun expensesCollection(groupId: String) =
        db.collection("groups").document(groupId).collection("expenses")

    /**
     * Add a new expense with currency conversion.
     */
    suspend fun addExpenseWithCurrency(
        groupId: String,
        expense: Expense
    ): Result<Unit> {
        return try {
            val doc = expensesCollection(groupId).document()
            val toSave = expense.copy(id = doc.id)
            doc.set(toSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update existing expense (including currency change).
     */
    suspend fun updateExpenseWithCurrency(
        groupId: String,
        expense: Expense
    ): Result<Unit> {
        return try {
            expensesCollection(groupId).document(expense.id).set(expense).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all expenses for a group.
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

    /**
     * Delete an expense
     */
    suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit> {
        return try {
            expensesCollection(groupId).document(expenseId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
