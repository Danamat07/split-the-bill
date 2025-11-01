package com.example.whopaid.ui.groups

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.databinding.ItemGroupBinding
import com.example.whopaid.models.Group

/**
 * Simple RecyclerView adapter for list of groups.
 */
class GroupAdapter(
    private var items: List<Group>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<GroupAdapter.VH>() {

    interface OnItemClickListener {
        fun onItemClick(group: Group)
    }

    inner class VH(private val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(g: Group) {
            binding.tvName.text = g.name
            binding.tvDescription.text = g.description
            binding.root.setOnClickListener { listener.onItemClick(g) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<Group>) {
        items = newList
        notifyDataSetChanged()
    }
}
