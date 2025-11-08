package com.example.whopaid.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.databinding.ItemBalanceBinding

/**
 * Adapter for displaying detailed balances:
 * shows expense title, user name, currency, and converted RON equivalent.
 */
class BalanceAdapter(
    private val items: MutableList<BalanceActivity.BalanceItem>,
    private val onToggle: (BalanceActivity.BalanceItem) -> Unit
) : RecyclerView.Adapter<BalanceAdapter.VH>() {

    inner class VH(private val binding: ItemBalanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BalanceActivity.BalanceItem) {
            // Example:
            // "You owe John 45.00 EUR (≈ 223.50 RON) for Dinner"
            val label = if (item.type == "debt") {
                "You owe ${item.name} %.2f %s (≈ %.2f RON)".format(
                    item.amountRaw, item.currencyCode, item.amountInGroupCurrency
                )
            } else {
                "${item.name} owes you %.2f %s (≈ %.2f RON)".format(
                    item.amountRaw, item.currencyCode, item.amountInGroupCurrency
                )
            }
            binding.tvName.text = label
            binding.tvExpense.text = "For: ${item.expenseTitle}"
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
