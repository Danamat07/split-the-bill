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

    // fallback list to prevent crash
    private var allCurrencies: List<String> = listOf("RON", "EUR", "USD", "GBP")
    private var currenciesLoaded = false
    private var expenseLoaded = false
    private var loadedExpenseData: Expense? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        expenseId = intent.getStringExtra("expenseId")
        if (groupId == null) {
            finish(); return
        }

        setupCurrencySpinner()
        loadMembers {
            if (expenseId != null) loadExistingExpense()
        }

        binding.btnAddExpense.setOnClickListener { saveExpense() }
        binding.btnSelectParticipants.setOnClickListener { showParticipantDialog() }
    }

    /**
     * Loads currencies from API safely.
     */
    private fun setupCurrencySpinner() {
        val loadingAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Loading...")
        )
        binding.spinnerCurrency.adapter = loadingAdapter

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val fetched = CurrencyService.getAvailableCurrencies()
                if (fetched.isNotEmpty()) {
                    allCurrencies = fetched
                }

                val adapter = ArrayAdapter(
                    this@CreateExpenseActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    allCurrencies
                )
                binding.spinnerCurrency.adapter = adapter

                val ronIndex = allCurrencies.indexOf("RON")
                if (ronIndex >= 0) binding.spinnerCurrency.setSelection(ronIndex)

                currenciesLoaded = true
                syncExpenseUI()

            } catch (e: Exception) {
                val fallbackAdapter = ArrayAdapter(
                    this@CreateExpenseActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    allCurrencies
                )
                binding.spinnerCurrency.adapter = fallbackAdapter

                currenciesLoaded = true
                syncExpenseUI()
            }
        }
    }

    /**
     * Loads group members.
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
                        names.add(name)
                        membersMap[name] = doc.id
                    }

                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )
                    binding.spinnerPayer.adapter = adapter

                    syncExpenseUI()

                    onLoaded?.invoke()
                }
        }
    }

    /**
     * Loads existing expense for editing.
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
                loadedExpenseData = exp
                expenseLoaded = true

                syncExpenseUI()
            }
    }

    /**
     * Synchronizes UI only when resources are ready.
     */
    private fun syncExpenseUI() {
        if (!expenseLoaded) return

        val exp = loadedExpenseData ?: return

        // Title + Amount
        binding.etTitle.setText(exp.title)
        binding.etAmount.setText(exp.amountRaw.toString())
        selectedParticipants = exp.participants.toMutableList()
        currentPayerUid = exp.payerUid

        // Currency spinner
        if (currenciesLoaded) {
            val curIndex = allCurrencies.indexOf(exp.currencyCode)
            if (curIndex >= 0) {
                binding.spinnerCurrency.setSelection(curIndex)
            }
        }

        // Payer spinner
        if (membersMap.isNotEmpty()) {
            val payerName = membersMap.entries.find { it.value == exp.payerUid }?.key
            if (payerName != null) {
                val names = membersMap.keys.toList()
                val index = names.indexOf(payerName)
                if (index >= 0) binding.spinnerPayer.setSelection(index)
            }
        }
    }

    /**
     * Participant selection dialog.
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Save expense (new or update).
     */
    private fun saveExpense() {
        val gid = groupId ?: return
        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val currencyCode = binding.spinnerCurrency.selectedItem?.toString() ?: "RON"

        val payerName = binding.spinnerPayer.selectedItem?.toString()

        if (title.isEmpty() || amountStr.isEmpty() || payerName == null) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
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
                val converted = if (currencyCode == "RON") {
                    amountRaw
                } else {
                    CurrencyService.convert(amountRaw, currencyCode, "RON")
                }

                val expense = Expense(
                    id = expenseId ?: "",
                    title = title,
                    amountRaw = amountRaw,
                    currencyCode = currencyCode,
                    amountInGroupCurrency = converted,
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
                    Toast.makeText(this@CreateExpenseActivity, "Expense saved", Toast.LENGTH_SHORT).show()
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
