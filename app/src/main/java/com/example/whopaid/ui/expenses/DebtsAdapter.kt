package com.example.whopaid.ui.expenses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.R
import com.example.whopaid.ui.expenses.DebtsActivity.DebtItem
import java.util.*

class DebtsAdapter(
    private var items: List<DebtItem>,
    private val onChecked: ((DebtItem) -> Unit)?
) : RecyclerView.Adapter<DebtsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle: TextView = v.findViewById(R.id.tvDebtTitle)
        private val tvAmount: TextView = v.findViewById(R.id.tvDebtAmount)
        private val cbPaid: CheckBox = v.findViewById(R.id.cbPaid)

        fun bind(item: DebtItem) {
            tvTitle.text = "${item.title}"
            tvAmount.text = String.format(Locale.getDefault(), "%.2f", item.amount)
            cbPaid.isChecked = item.isPaid
            cbPaid.visibility = if (onChecked != null) View.VISIBLE else View.GONE

            cbPaid.setOnCheckedChangeListener { _, checked ->
                item.isPaid = checked
                onChecked?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debt, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<DebtItem>) {
        items = newList
        notifyDataSetChanged()
    }
}
