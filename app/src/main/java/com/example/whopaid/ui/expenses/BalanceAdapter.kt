package com.example.whopaid.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.databinding.ItemBalanceBinding

/**
 * Adapter to display debts and credits with real-time Firestore sync.
 * Each row:
 *  - shows expense name
 *  - displays amount + currency + RON equivalent
 *  - supports checkbox for settlement (syncs via listener)
 */
class BalanceAdapter(
    private val items: MutableList<BalanceActivity.BalanceItem>,
    private val onToggle: (BalanceActivity.BalanceItem) -> Unit
) : RecyclerView.Adapter<BalanceAdapter.VH>() {

    inner class VH(private val binding: ItemBalanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BalanceActivity.BalanceItem) {
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
            binding.cbSettled.setOnCheckedChangeListener(null)
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
