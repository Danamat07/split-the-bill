package com.example.whopaid.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.databinding.ItemExpenseBinding
import com.example.whopaid.models.Expense
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter to display all expenses.
 * Single tap = edit; Long tap = delete.
 */
class ExpenseAdapter(
    private var items: List<Expense>,
    private val listener: OnExpenseClickListener
) : RecyclerView.Adapter<ExpenseAdapter.VH>() {

    interface OnExpenseClickListener {
        fun onExpenseClick(expense: Expense)
        fun onExpenseLongClick(expense: Expense)
    }

    inner class VH(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(e: Expense) {
            binding.tvTitle.text = e.title
            binding.tvAmount.text = String.format("%.2f", e.amount)
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvDate.text = sdf.format(Date(e.createdAt))

            binding.root.setOnClickListener { listener.onExpenseClick(e) }
            binding.root.setOnLongClickListener {
                listener.onExpenseLongClick(e)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<Expense>) {
        items = newList
        notifyDataSetChanged()
    }
}
