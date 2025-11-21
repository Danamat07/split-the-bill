package com.example.whopaid.ui.groups

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whopaid.databinding.ItemMemberBinding
import com.example.whopaid.models.User

/**
 * Adapter for listing members of a group by name.
 * Long-press on an item triggers OnMemberLongClickListener (used by admin to remove member).
 */
class MemberAdapter(
    private var items: List<User>,
    private val listener: OnMemberLongClickListener
) : RecyclerView.Adapter<MemberAdapter.VH>() {

    interface OnMemberLongClickListener {
        fun onMemberLongClick(user: User)
    }

    inner class VH(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvMemberName.text = user.name?.ifEmpty { user.email } ?: user.email
            binding.root.setOnLongClickListener {
                listener.onMemberLongClick(user)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<User>) {
        items = newList
        notifyDataSetChanged()
    }
}