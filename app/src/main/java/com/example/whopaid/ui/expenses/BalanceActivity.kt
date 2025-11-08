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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Displays automatic calculation of debts and credits for the logged-in user.
 * Each item shows:
 *  - Expense title
 *  - Name of the person owed or owing
 *  - Amount
 *  - Checkbox to mark as settled
 *
 * Settlements are stored in Firestore so all group members share state.
 * Admins can reset all settlements.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBalanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            finish()
            return
        }

        adapter = BalanceAdapter(balances) { item ->
            toggleSettlement(item)
        }
        binding.recyclerBalances.layoutManager = LinearLayoutManager(this)
        binding.recyclerBalances.adapter = adapter

        binding.btnDeleteSelected.setOnClickListener { removeSettled() }
        binding.btnResetAll.setOnClickListener { confirmResetAll() }

        loadBalances()
    }

    /**
     * Fetches expenses, user data, and settlements to calculate balances.
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

                // Load user names for all members
                val userIds = expenses.flatMap { it.participants + it.payerUid }.distinct()
                val usersMap = mutableMapOf<String, String>()
                if (userIds.isNotEmpty()) {
                    val usersSnap =
                        db.collection("users").whereIn("__name__", userIds).get().await()
                    for (doc in usersSnap.documents) {
                        usersMap[doc.id] =
                            doc.getString("name") ?: doc.getString("email") ?: doc.id
                    }
                }

                // Load existing settlements
                val settledSnap =
                    db.collection("groups").document(gid).collection("settlements").get().await()
                val settledKeys = settledSnap.documents.map { it.id }.toSet()

                calculateUserBalances(currentUser.uid, expenses, usersMap, settledKeys)
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
     * Calculates all debts/credits for current user.
     */
    private fun calculateUserBalances(
        currentUid: String,
        expenses: List<Expense>,
        names: Map<String, String>,
        settledKeys: Set<String>
    ) {
        val temp = mutableListOf<BalanceItem>()

        for (e in expenses) {
            val share = if (e.participants.isNotEmpty()) e.amount / e.participants.size else 0.0
            for (p in e.participants) {
                if (p == e.payerUid) continue
                val key = "${e.id}_${p}_${e.payerUid}"

                if (p == currentUid) {
                    temp.add(
                        BalanceItem(
                            id = key,
                            expenseTitle = e.title,
                            name = names[e.payerUid] ?: e.payerUid,
                            amount = share,
                            type = "debt",
                            settled = settledKeys.contains(key)
                        )
                    )
                } else if (e.payerUid == currentUid) {
                    temp.add(
                        BalanceItem(
                            id = key,
                            expenseTitle = e.title,
                            name = names[p] ?: p,
                            amount = share,
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

        binding.textEmpty.visibility = if (balances.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Called when a checkbox is toggled.
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
     * Remove all settled balances locally (keeps Firestore data).
     */
    private fun removeSettled() {
        val remaining = balances.filterNot { it.settled }.toMutableList()
        balances.clear()
        balances.addAll(remaining)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Removed paid settlements from view", Toast.LENGTH_SHORT).show()
    }

    /**
     * Reset all settlements (admin only).
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
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
                Toast.makeText(this@BalanceActivity, "All settlements cleared", Toast.LENGTH_SHORT)
                    .show()
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
     * Data structure for one balance line.
     */
    data class BalanceItem(
        val id: String,
        val expenseTitle: String,
        val name: String,
        val amount: Double,
        val type: String, // "debt" or "credit"
        var settled: Boolean = false
    )
}
