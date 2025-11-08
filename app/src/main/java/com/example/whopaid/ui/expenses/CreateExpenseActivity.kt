package com.example.whopaid.ui.expenses

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityCreateExpenseBinding
import com.example.whopaid.models.Expense
import com.example.whopaid.repo.ExpenseRepository
import com.example.whopaid.service.CurrencyService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen to create or edit an expense with full currency selection.
 * - Loads all currencies from ExchangeRate-API dynamically.
 * - Converts the entered amount to RON (group standard currency).
 * - Any group member can create, edit, or delete an expense.
 */
class CreateExpenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateExpenseBinding
    private val repo = ExpenseRepository()
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()

    private var groupId: String? = null
    private var expenseId: String? = null
    private var membersMap = mutableMapOf<String, String>() // name -> uid
    private var selectedParticipants = mutableListOf<String>()
    private var currentPayerUid: String? = null
    private var allCurrencies: List<String> = listOf("RON", "EUR", "USD", "GBP") // fallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        expenseId = intent.getStringExtra("expenseId")
        if (groupId == null) {
            finish(); return
        }

        // Step 1: Load currencies (async)
        setupCurrencySpinner()

        // Step 2: Load members and, if editing, existing expense
        loadMembers {
            if (expenseId != null) loadExistingExpense()
        }

        binding.btnAddExpense.setOnClickListener { saveExpense() }
        binding.btnSelectParticipants.setOnClickListener { showParticipantDialog() }
    }

    /**
     * Loads all available currencies from ExchangeRate-API.
     */
    private fun setupCurrencySpinner() {
        val loadingAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Loading currencies...")
        )
        binding.spinnerCurrency.adapter = loadingAdapter

        CoroutineScope(Dispatchers.Main).launch {
            try {
                allCurrencies = CurrencyService.getAvailableCurrencies()
                val adapter = ArrayAdapter(
                    this@CreateExpenseActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    allCurrencies
                )
                binding.spinnerCurrency.adapter = adapter
                val ronIndex = allCurrencies.indexOf("RON")
                if (ronIndex >= 0) binding.spinnerCurrency.setSelection(ronIndex)
            } catch (e: Exception) {
                Toast.makeText(
                    this@CreateExpenseActivity,
                    "Currency list failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                val fallbackAdapter = ArrayAdapter(
                    this@CreateExpenseActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    allCurrencies
                )
                binding.spinnerCurrency.adapter = fallbackAdapter
            }
        }
    }

    /**
     * Loads group members to populate payer spinner.
     */
    private fun loadMembers(onLoaded: (() -> Unit)? = null) {
        val gid = groupId ?: return
        db.collection("groups").document(gid).get().addOnSuccessListener { g ->
            val memberIds = g.get("members") as? List<String> ?: return@addOnSuccessListener
            db.collection("users").whereIn("__name__", memberIds).get()
                .addOnSuccessListener { users ->
                    val names = mutableListOf<String>()
                    membersMap.clear()
                    for (doc in users.documents) {
                        val name = doc.getString("name") ?: doc.getString("email") ?: "Unknown"
                        membersMap[name] = doc.id
                        names.add(name)
                    }
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )
                    binding.spinnerPayer.adapter = adapter
                    currentPayerUid?.let { uid ->
                        val payerName = membersMap.entries.find { it.value == uid }?.key
                        if (payerName != null) {
                            val index = names.indexOf(payerName)
                            if (index >= 0) binding.spinnerPayer.setSelection(index)
                        }
                    }
                    onLoaded?.invoke()
                }
        }
    }

    /**
     * Loads existing expense (for editing mode).
     */
    private fun loadExistingExpense() {
        val gid = groupId ?: return
        val eid = expenseId ?: return
        binding.progressBar.visibility = View.VISIBLE
        db.collection("groups").document(gid)
            .collection("expenses").document(eid)
            .get().addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                val exp = doc.toObject(Expense::class.java) ?: return@addOnSuccessListener
                binding.etTitle.setText(exp.title)
                binding.etAmount.setText(exp.amountRaw.toString())
                selectedParticipants = exp.participants.toMutableList()
                currentPayerUid = exp.payerUid

                // Set payer spinner if members already loaded
                if (membersMap.isNotEmpty()) {
                    val payerName = membersMap.entries.find { it.value == exp.payerUid }?.key
                    if (payerName != null) {
                        val names = membersMap.keys.toList()
                        val index = names.indexOf(payerName)
                        if (index >= 0) binding.spinnerPayer.setSelection(index)
                    }
                }

                // Set currency spinner
                val curIndex = allCurrencies.indexOf(exp.currencyCode)
                if (curIndex >= 0) binding.spinnerCurrency.setSelection(curIndex)
            }
    }

    /**
     * Show dialog with checkboxes for group members → choose participants.
     */
    private fun showParticipantDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val checkboxes = mutableListOf<Pair<String, CheckBox>>()

        membersMap.forEach { (name, uid) ->
            val cb = CheckBox(this)
            cb.text = name
            cb.isChecked = selectedParticipants.contains(uid)
            container.addView(cb)
            checkboxes.add(name to cb)
        }

        AlertDialog.Builder(this)
            .setTitle("Select participants")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                selectedParticipants.clear()
                checkboxes.forEach { (name, cb) ->
                    if (cb.isChecked) membersMap[name]?.let { selectedParticipants.add(it) }
                }
                Toast.makeText(
                    this,
                    "Selected ${selectedParticipants.size} participants",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Creates or updates an expense, with currency conversion via ExchangeRate-API.
     */
    private fun saveExpense() {
        val gid = groupId ?: return
        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val payerName = binding.spinnerPayer.selectedItem?.toString()
        val currencyCode = binding.spinnerCurrency.selectedItem?.toString() ?: "RON"

        if (title.isEmpty() || amountStr.isEmpty() || payerName == null) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show(); return
        }
        val amountRaw = amountStr.toDoubleOrNull()
        if (amountRaw == null || amountRaw <= 0) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show(); return
        }

        val payerUid = membersMap[payerName] ?: return
        currentPayerUid = payerUid
        if (selectedParticipants.isEmpty()) {
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Convert to RON using ExchangeRate-API
                val convertedAmount = if (currencyCode == "RON") {
                    amountRaw
                } else {
                    CurrencyService.convert(amountRaw, currencyCode, "RON")
                }

                val expense = Expense(
                    id = expenseId ?: "",
                    title = title,
                    amountRaw = amountRaw,
                    currencyCode = currencyCode,
                    amountInGroupCurrency = convertedAmount,
                    payerUid = payerUid,
                    participants = selectedParticipants,
                    createdAt = System.currentTimeMillis()
                )

                val result = if (expenseId == null) {
                    repo.addExpenseWithCurrency(gid, expense)
                } else {
                    repo.updateExpenseWithCurrency(gid, expense)
                }

                binding.progressBar.visibility = View.GONE
                if (result.isSuccess) {
                    Toast.makeText(this@CreateExpenseActivity, "Expense saved", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                } else {
                    Toast.makeText(
                        this@CreateExpenseActivity,
                        "Error: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@CreateExpenseActivity,
                    "Conversion error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
