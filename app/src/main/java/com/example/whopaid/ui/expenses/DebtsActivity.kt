package com.example.whopaid.ui.expenses

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityDebtsBinding
import com.example.whopaid.models.Expense
import com.example.whopaid.repo.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Displays debts for the logged-in user:
 * - Total to pay
 * - Total to receive
 * - Detailed lists for both sides
 */
class DebtsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebtsBinding
    private val repo = ExpenseRepository()
    private val authRepo = AuthRepository()
    private var groupId: String? = null
    private var userUid: String? = null

    private val toPay = mutableListOf<DebtItem>()
    private val toReceive = mutableListOf<DebtItem>()
    private lateinit var adapterPay: DebtsAdapter
    private lateinit var adapterReceive: DebtsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebtsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        userUid = authRepo.currentUser()?.uid

        if (groupId == null || userUid == null) {
            finish()
            return
        }

        adapterPay = DebtsAdapter(toPay) { updatePaidState(it) }
        adapterReceive = DebtsAdapter(toReceive, null)

        binding.recyclerToPay.layoutManager = LinearLayoutManager(this)
        binding.recyclerToPay.adapter = adapterPay
        binding.recyclerToReceive.layoutManager = LinearLayoutManager(this)
        binding.recyclerToReceive.adapter = adapterReceive

        loadDebts()
    }

    private fun loadDebts() {
        val gid = groupId ?: return
        val uid = userUid ?: return
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            val result = repo.getExpenses(gid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                calculateDebts(uid, list)
            } else {
                Toast.makeText(
                    this@DebtsActivity,
                    "Error: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * For each expense: payer gets money from participants equally.
     */
    private fun calculateDebts(currentUid: String, expenses: List<Expense>) {
        toPay.clear()
        toReceive.clear()

        for (e in expenses) {
            val perPerson = e.amount / e.participants.size
            for (participant in e.participants) {
                if (participant == e.payerUid) continue
                val item = DebtItem(
                    title = e.title,
                    amount = perPerson,
                    fromUid = participant,
                    toUid = e.payerUid,
                    expenseId = e.id
                )
                if (participant == currentUid) {
                    toPay.add(item)
                } else if (e.payerUid == currentUid) {
                    toReceive.add(item)
                }
            }
        }

        val totalPay = toPay.sumOf { it.amount }
        val totalReceive = toReceive.sumOf { it.amount }

        binding.tvTotalToPay.text = String.format(Locale.getDefault(), "%.2f", totalPay)
        binding.tvTotalToReceive.text = String.format(Locale.getDefault(), "%.2f", totalReceive)

        adapterPay.updateList(toPay)
        adapterReceive.updateList(toReceive)
        binding.textEmpty.visibility =
            if (toPay.isEmpty() && toReceive.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Handles checkbox “paid”.
     * (Future improvement: save paid state to Firestore)
     */
    private fun updatePaidState(item: DebtItem) {
        Toast.makeText(
            this,
            "Marked '${item.title}' as paid (mock)",
            Toast.LENGTH_SHORT
        ).show()
    }

    data class DebtItem(
        val title: String,
        val amount: Double,
        val fromUid: String,
        val toUid: String,
        val expenseId: String,
        var isPaid: Boolean = false
    )
}
