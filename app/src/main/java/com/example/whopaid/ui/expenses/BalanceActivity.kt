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
import com.example.whopaid.service.EmailService
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * BalanceActivity – shows automatic calculation of debts and credits
 * for the current logged-in user.
 *
 * New functionality (v1.5):
 * - Button "Send Reminders" sends an email (via EmailJS) to each person who owes the current user.
 * - Each email contains the items (expense title + amount + currency + converted RON) and a total.
 *
 * Requirements:
 * - You must configure EmailJS and supply SERVICE_ID, TEMPLATE_ID, USER_ID in the code below (or store in secure config).
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

    private val EMAILJS_SERVICE_ID = "service_cnrayl8"
    private val EMAILJS_TEMPLATE_ID = "template_9cs13e2"
    private val EMAILJS_USER_ID = "jAJjHgTpqlrtw7F52"

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

        binding.btnSendReminders.setOnClickListener { sendReminders() }

        loadBalances()
    }

    override fun onDestroy() {
        super.onDestroy()
        // stop real-time listener when activity is destroyed
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

                // Get all involved user IDs (payer + participants)
                val userIds = expenses.flatMap { it.participants + it.payerUid }.distinct()
                val names = mutableMapOf<String, String>()
                val emails = mutableMapOf<String, String>()

                if (userIds.isNotEmpty()) {
                    // Use document IDs to fetch user documents
                    val usersSnap = db.collection("users")
                        .whereIn(FieldPath.documentId(), userIds)
                        .get()
                        .await()

                    for (doc in usersSnap.documents) {
                        val uid = doc.id
                        names[uid] = doc.getString("name") ?: doc.getString("email") ?: uid
                        emails[uid] = doc.getString("email") ?: ""
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

                // Save locally the emails map so sendReminders can use it
                cachedNames = names
                cachedEmails = emails
                cachedGroupName = groupSnap.getString("name") ?: "Group"
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

    // caches filled in loadBalances to avoid fetching again during sendReminders
    private var cachedNames: Map<String, String> = emptyMap()
    private var cachedEmails: Map<String, String> = emptyMap()
    private var cachedGroupName: String = "Group"

    /**
     * Calculates per-user debts & credits and updates the list.
     * Each item’s "settled" state is now controlled by real-time Firestore data.
     *
     * NOTE: BalanceItem now includes userId (the other user's uid), so we can map to email.
     */
    private fun calculateUserBalances(
        currentUid: String,
        expenses: List<Expense>,
        names: Map<String, String>,
        settledKeys: Set<String>
    ) {
        val temp = mutableListOf<BalanceItem>()

        for (e in expenses) {
            val shareRON = if (e.participants.isNotEmpty())
                e.amountInGroupCurrency / e.participants.size
            else 0.0
            val shareRaw = if (e.participants.isNotEmpty())
                e.amountRaw / e.participants.size
            else 0.0

            for (p in e.participants) {
                if (p == e.payerUid) continue
                val key = "${e.id}_${p}_${e.payerUid}"

                if (p == currentUid) {
                    temp.add(
                        BalanceItem(
                            id = key,
                            expenseTitle = e.title,
                            name = names[e.payerUid] ?: e.payerUid,
                            userId = e.payerUid,
                            amountInGroupCurrency = shareRON,
                            amountRaw = shareRaw,
                            currencyCode = e.currencyCode,
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
                            userId = p,
                            amountInGroupCurrency = shareRON,
                            amountRaw = shareRaw,
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
     * Send reminder emails to all users who owe the current user (type == "credit" and not settled).
     *
     * For each debtor (unique userId), builds:
     *  - items_html: <ul><li>Expense — 10.00 EUR (≈ 50.00 RON)</li>...</ul>
     *  - total_formatted: e.g. "30.00 EUR"
     *
     * Uses EmailService to POST to EmailJS endpoint. Must configure EMAILJS_* constants above.
     */
    private fun sendReminders() {
        val gid = groupId ?: return
        val currentUser = authRepo.currentUser() ?: return
        val currentUid = currentUser.uid
        val currentName = cachedNames[currentUid] ?: currentUser.email ?: "You"

        // collect only CREDIT items (others owe current user) and not settled
        val creditItems = balances.filter { it.type == "credit" && !it.settled }

        if (creditItems.isEmpty()) {
            Toast.makeText(this, "No unpaid credits to remind about", Toast.LENGTH_SHORT).show()
            return
        }

        // Group by debtor userId (the person who owes the current user)
        val grouped = creditItems.groupBy { it.userId }

        // Run network calls in IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            val successes = mutableListOf<String>()
            val failures = mutableListOf<String>()

            for ((debtorUid, items) in grouped) {
                // get email and name for debtor
                val toEmail = cachedEmails[debtorUid] ?: run {
                    // try to fetch (fallback)
                    val doc = try {
                        db.collection("users").document(debtorUid).get().await()
                    } catch (e: Exception) { null }
                    val email = doc?.getString("email") ?: ""
                    if (email.isEmpty()) {
                        failures.add("No email for user $debtorUid")
                        continue
                    } else {
                        // cache
                        cachedEmails = cachedEmails + (debtorUid to email)
                        email
                    }
                }

                val toName = cachedNames[debtorUid] ?: toEmail

                // Build items_html and total (display amounts in expense currency + RON)
                val itemsHtml = StringBuilder()
                var totalByCurrencyMap = mutableMapOf<String, Double>() // map currency->sum (raw)
                for (it in items) {
                    // e.g. "<li>Dinner — 45.00 EUR (≈ 223.50 RON)</li>"
                    itemsHtml.append(
                        "<li>${escapeHtml(it.expenseTitle)} — " +
                                String.format(Locale.getDefault(), "%.2f %s (≈ %.2f RON)",
                                    it.amountRaw, it.currencyCode, it.amountInGroupCurrency) +
                                "</li>"
                    )
                    totalByCurrencyMap[it.currencyCode] =
                        (totalByCurrencyMap[it.currencyCode] ?: 0.0) + it.amountRaw
                }

                // Build total_formatted string: if multiple currencies present join with commas
                val totalFormatted = totalByCurrencyMap.entries.joinToString(separator = ", ") { e ->
                    String.format(Locale.getDefault(), "%.2f %s", e.value, e.key)
                }

                val htmlList = "<ul>${itemsHtml.toString()}</ul>"

                // Build template params for EmailJS
                val templateParams = mapOf<String, Any>(
                    "to_name" to toName,
                    "to_email" to toEmail,
                    "from_name" to currentName,
                    "group_name" to cachedGroupName,
                    "items_html" to htmlList,
                    "total_formatted" to totalFormatted,
                    "subject" to "Reminder: outstanding payments in $cachedGroupName"
                )

                // Call EmailService (synchronous call inside IO coroutine)
                try {
                    val ok = EmailService.sendReminder(
                        EMAILJS_SERVICE_ID,
                        EMAILJS_TEMPLATE_ID,
                        EMAILJS_USER_ID,
                        templateParams
                    )
                    if (ok) successes.add(toEmail) else failures.add(toEmail)
                } catch (e: Exception) {
                    failures.add("${toEmail}: ${e.message}")
                }
            }

            // back to main thread -> show summary
            CoroutineScope(Dispatchers.Main).launch {
                val sb = StringBuilder()
                sb.append("Sent: ${successes.size}\nFailed: ${failures.size}")
                if (successes.isNotEmpty()) sb.append("\n\nSuccess: ${successes.joinToString(", ")}")
                if (failures.isNotEmpty()) sb.append("\n\nFailed: ${failures.joinToString(", ")}")
                AlertDialog.Builder(this@BalanceActivity)
                    .setTitle("Send Reminders")
                    .setMessage(sb.toString())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * Small helper to escape HTML for inserted text (simple).
     */
    private fun escapeHtml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
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
     *
     * Added userId: uid of the other person (debtor or creditor) so we can map to email.
     */
    data class BalanceItem(
        val id: String,
        val expenseTitle: String,
        val name: String,
        val userId: String,
        val amountInGroupCurrency: Double,
        val amountRaw: Double,
        val currencyCode: String,
        val type: String, // "debt" or "credit"
        var settled: Boolean = false
    )
}
