package com.example.whopaid.ui.groups

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityGroupsBinding
import com.example.whopaid.models.Group
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Displays a list of groups the current user belongs to.
 * Provides "Create Group" button to go to CreateGroupActivity.
 *
 * Uses a Firestore real-time listener on groups collection with a query:
 *    whereArrayContains("members", currentUid)
 */
class GroupsActivity : AppCompatActivity(), GroupAdapter.OnItemClickListener {

    private lateinit var binding: ActivityGroupsBinding
    private val db = FirebaseFirestore.getInstance()
    private val repo = AuthRepository()
    private var listener: ListenerRegistration? = null
    private lateinit var adapter: GroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fabCreateGroup.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        adapter = GroupAdapter(listOf(), this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        loadUserGroups()
    }

    private fun loadUserGroups() {
        val current = repo.currentUser()
        if (current == null) {
            // if not logged in, show empty state and return
            binding.textEmpty.visibility = View.VISIBLE
            return
        }
        binding.textEmpty.visibility = View.GONE

        // real-time query for groups where the current user is a member
        listener = db.collection("groups")
            .whereArrayContains("members", current.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    binding.textEmpty.text = "Error loading groups: ${error.message}"
                    binding.textEmpty.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Group::class.java)
                } ?: emptyList()
                adapter.updateData(groups)
                binding.textEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    /**
     * When an item is clicked, navigate to GroupDetailsActivity with groupId.
     */
    override fun onItemClick(group: Group) {
        val intent = Intent(this, GroupDetailsActivity::class.java)
        intent.putExtra("groupId", group.id)
        startActivity(intent)
    }
}
