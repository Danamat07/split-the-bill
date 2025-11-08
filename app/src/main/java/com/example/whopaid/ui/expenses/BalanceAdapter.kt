package com.example.whopaid.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.databinding.ItemBalanceBinding

/**
 * Adapter for balance items with name, expense title, amount, and checkbox.
 * Notifies parent when settlement state changes.
 */
class BalanceAdapter(
    private val items: MutableList<BalanceActivity.BalanceItem>,
    private val onToggle: (BalanceActivity.BalanceItem) -> Unit
) : RecyclerView.Adapter<BalanceAdapter.VH>() {

    inner class VH(private val binding: ItemBalanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BalanceActivity.BalanceItem) {
            val label = if (item.type == "debt") {
                "You owe ${item.name}"
            } else {
                "${item.name} owes you"
            }
            binding.tvName.text = label
            binding.tvExpense.text = "For: ${item.expenseTitle}"
            binding.tvAmount.text = String.format("%.2f", item.amount)
            binding.cbSettled.isChecked = item.settled

            binding.cbSettled.setOnCheckedChangeListener { _, isChecked ->
                item.settled = isChecked
                onToggle(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding =
            ItemBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}
