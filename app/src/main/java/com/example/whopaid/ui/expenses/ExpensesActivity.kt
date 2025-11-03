package com.example.whopaid.ui.expenses

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whopaid.databinding.ActivityExpensesBinding
import com.example.whopaid.models.Expense
import com.example.whopaid.repo.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Displays all expenses for a group.
 * Any member can add, edit, or delete an expense.
 */
class ExpensesActivity : AppCompatActivity(), ExpenseAdapter.OnExpenseClickListener {

    private lateinit var binding: ActivityExpensesBinding
    private val repo = ExpenseRepository()
    private lateinit var adapter: ExpenseAdapter
    private var groupId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            finish()
            return
        }

        adapter = ExpenseAdapter(listOf(), this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAddExpense.setOnClickListener {
            val intent = Intent(this, CreateExpenseActivity::class.java)
            intent.putExtra("groupId", groupId)
            startActivity(intent)
        }

        // 🔹 New button for DebtsActivity
        binding.btnViewDebts.setOnClickListener {
            val intent = Intent(this, DebtsActivity::class.java)
            intent.putExtra("groupId", groupId)
            startActivity(intent)
        }

        loadExpenses()
    }

    override fun onResume() {
        super.onResume()
        loadExpenses()
    }

    private fun loadExpenses() {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = repo.getExpenses(gid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                adapter.updateData(list)
                binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            } else {
                binding.textEmpty.text = "Error: ${result.exceptionOrNull()?.message}"
                binding.textEmpty.visibility = View.VISIBLE
            }
        }
    }

    override fun onExpenseClick(expense: Expense) {
        val intent = Intent(this, CreateExpenseActivity::class.java)
        intent.putExtra("groupId", groupId)
        intent.putExtra("expenseId", expense.id)
        startActivity(intent)
    }

    override fun onExpenseLongClick(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete this expense?")
            .setPositiveButton("Delete") { _, _ -> deleteExpense(expense) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExpense(expense: Expense) {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = repo.deleteExpense(gid, expense.id)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                loadExpenses()
            }
        }
    }
}
