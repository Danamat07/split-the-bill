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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen to create or edit an expense.
 * Any member can create, edit or delete.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        expenseId = intent.getStringExtra("expenseId")

        if (groupId == null) {
            finish()
            return
        }

        loadMembers()

        binding.btnAddExpense.setOnClickListener { saveExpense() }
        binding.btnSelectParticipants.setOnClickListener { showParticipantDialog() }

        if (expenseId != null) loadExistingExpense()
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
                binding.etTitle.setText(exp.title)
                binding.etAmount.setText(exp.amount.toString())
                selectedParticipants = exp.participants.toMutableList()
            }
    }

    /**
     * Loads group members to populate payer spinner.
     */
    private fun loadMembers() {
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
                }
        }
    }

    /**
     * Shows a dialog with checkboxes for all members to pick participants.
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
     * Adds or updates an expense.
     */
    private fun saveExpense() {
        val gid = groupId ?: return
        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val payerName = binding.spinnerPayer.selectedItem?.toString()

        if (title.isEmpty() || amountStr.isEmpty() || payerName == null) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val payerUid = membersMap[payerName] ?: return
        if (selectedParticipants.isEmpty()) {
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT).show()
            return
        }

        val expense = Expense(
            id = expenseId ?: "",
            title = title,
            amount = amount,
            payerUid = payerUid,
            participants = selectedParticipants,
            createdAt = System.currentTimeMillis()
        )

        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = if (expenseId == null) {
                repo.addExpense(gid, expense)
            } else {
                repo.updateExpense(gid, expense)
            }
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@CreateExpenseActivity, "Saved", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(
                    this@CreateExpenseActivity,
                    "Error: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
