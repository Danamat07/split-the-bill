package com.example.whopaid.ui.expenses

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityBalanceBinding
import com.example.whopaid.models.Expense
import com.example.whopaid.repo.ExpenseRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * BalanceActivity – shows automatic calculation of debts and credits
 * for the current logged-in user.
 *
 * Features:
 * - Displays per-expense debts and credits (with currency and RON conversion)
 * - Allows user to mark payments as settled (persistent in Firestore)
 * - Real-time updates: all members see check/uncheck changes instantly
 * - Admin can reset all settlements for the group
 */
class BalanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBalanceBinding
    private lateinit var adapter: BalanceAdapter
    private val repo = ExpenseRepository()
    private val authRepo = AuthRepository()
    private val db = FirebaseFirestore.getInstance()

    private var groupId: String? = null
    private val balances = mutableListOf<BalanceItem>()
    private var isAdmin = false
    private var settlementsListener: ListenerRegistration? = null
    private var lastSettledKeys = setOf<String>() // used to update checkboxes dynamically

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBalanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            finish()
            return
        }

        adapter = BalanceAdapter(balances) { item -> toggleSettlement(item) }
        binding.recyclerBalances.layoutManager = LinearLayoutManager(this)
        binding.recyclerBalances.adapter = adapter

        binding.btnDeleteSelected.setOnClickListener { removeSettled() }
        binding.btnResetAll.setOnClickListener { confirmResetAll() }

        loadBalances()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop real-time listener when activity is destroyed
        settlementsListener?.remove()
    }

    /**
     * Loads group and expenses, then sets up real-time listener for settlements.
     */
    private fun loadBalances() {
        val gid = groupId ?: return
        val currentUser = authRepo.currentUser() ?: return
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val groupSnap = db.collection("groups").document(gid).get().await()
                val adminUid = groupSnap.getString("adminUid")
                isAdmin = (currentUser.uid == adminUid)

                val expResult = repo.getExpenses(gid)
                if (!expResult.isSuccess) throw expResult.exceptionOrNull()!!
                val expenses = expResult.getOrNull() ?: emptyList()

                // Get all involved user names
                val userIds = expenses.flatMap { it.participants + it.payerUid }.distinct()
                val names = mutableMapOf<String, String>()
                if (userIds.isNotEmpty()) {
                    val snap = db.collection("users").whereIn("__name__", userIds).get().await()
                    for (doc in snap.documents) {
                        names[doc.id] = doc.getString("name") ?: doc.getString("email") ?: doc.id
                    }
                }

                // Listen in real-time for settlements updates
                settlementsListener?.remove()
                settlementsListener = db.collection("groups")
                    .document(gid)
                    .collection("settlements")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Toast.makeText(
                                this@BalanceActivity,
                                "Error in settlements listener: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@addSnapshotListener
                        }

                        val settledKeys = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                        lastSettledKeys = settledKeys
                        calculateUserBalances(currentUser.uid, expenses, names, settledKeys)
                    }

                binding.btnResetAll.visibility = if (isAdmin) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(
                    this@BalanceActivity,
                    "Error loading balances: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Calculates per-user debts & credits and updates the list.
     * Each item’s "settled" state is now controlled by real-time Firestore data.
     */
    private fun calculateUserBalances(
        currentUid: String,
        expenses: List<Expense>,
        names: Map<String, String>,
        settledKeys: Set<String>
    ) {
        val temp = mutableListOf<BalanceItem>()

        for (e in expenses) {
            val share = if (e.participants.isNotEmpty())
                e.amountInGroupCurrency / e.participants.size
            else 0.0

            for (p in e.participants) {
                if (p == e.payerUid) continue
                val key = "${e.id}_${p}_${e.payerUid}"

                if (p == currentUid) {
                    // Current user owes payer
                    temp.add(
                        BalanceItem(
                            id = key,
                            expenseTitle = e.title,
                            name = names[e.payerUid] ?: e.payerUid,
                            amountInGroupCurrency = share,
                            amountRaw = e.amountRaw / e.participants.size,
                            currencyCode = e.currencyCode,
                            type = "debt",
                            settled = settledKeys.contains(key)
                        )
                    )
                } else if (e.payerUid == currentUid) {
                    // Someone owes current user
                    temp.add(
                        BalanceItem(
                            id = key,
                            expenseTitle = e.title,
                            name = names[p] ?: p,
                            amountInGroupCurrency = share,
                            amountRaw = e.amountRaw / e.participants.size,
                            currencyCode = e.currencyCode,
                            type = "credit",
                            settled = settledKeys.contains(key)
                        )
                    )
                }
            }
        }

        balances.clear()
        balances.addAll(temp)
        adapter.notifyDataSetChanged()
        binding.textEmpty.visibility =
            if (balances.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Called when checkbox toggled → update Firestore state.
     * Firestore snapshot listener ensures all users’ UIs update in real-time.
     */
    private fun toggleSettlement(item: BalanceItem) {
        val gid = groupId ?: return
        val coll = db.collection("groups").document(gid).collection("settlements")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (item.settled) {
                    coll.document(item.id).set(mapOf("settled" to true)).await()
                } else {
                    coll.document(item.id).delete().await()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@BalanceActivity,
                    "Error updating settlement: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Remove all settled items from view (local only).
     */
    private fun removeSettled() {
        val remaining = balances.filterNot { it.settled }.toMutableList()
        balances.clear()
        balances.addAll(remaining)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Removed paid settlements from view", Toast.LENGTH_SHORT).show()
    }

    /**
     * Admin-only reset for all settlements (clear Firestore records).
     */
    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setTitle("Reset All Balances")
            .setMessage("This will clear all settlement records for everyone. Continue?")
            .setPositiveButton("Reset") { _, _ -> resetAll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAll() {
        val gid = groupId ?: return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val coll = db.collection("groups").document(gid).collection("settlements")
                val snap = coll.get().await()
                for (doc in snap.documents) doc.reference.delete().await()
                Toast.makeText(this@BalanceActivity, "All settlements cleared", Toast.LENGTH_SHORT)
                    .show()
                // Refresh after clearing all
                loadBalances()
            } catch (e: Exception) {
                Toast.makeText(
                    this@BalanceActivity,
                    "Error resetting balances: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Data model for a single debt/credit line.
     */
    data class BalanceItem(
        val id: String,
        val expenseTitle: String,
        val name: String,
        val amountInGroupCurrency: Double,
        val amountRaw: Double,
        val currencyCode: String,
        val type: String, // "debt" or "credit"
        var settled: Boolean = false
    )
}
