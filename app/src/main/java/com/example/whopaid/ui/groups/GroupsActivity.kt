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

class GroupsActivity : AppCompatActivity(), GroupAdapter.OnItemClickListener {

    private lateinit var binding: ActivityGroupsBinding
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()
    private var listener: ListenerRegistration? = null
    private lateinit var adapter: GroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = GroupAdapter(listOf(), this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabCreateGroup.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        binding.fabScanGroup.setOnClickListener {
            startActivity(Intent(this, ScanQrActivity::class.java))
        }

        loadUserGroups()
    }

    private fun loadUserGroups() {
        val currentUser = authRepo.currentUser()
        if (currentUser == null) {
            binding.textEmpty.visibility = View.VISIBLE
            binding.textEmpty.text = "Not logged in"
            return
        }

        binding.textEmpty.visibility = View.GONE

        listener = db.collection("groups")
            .whereArrayContains("members", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    binding.textEmpty.text = "Error loading groups: ${error.message}"
                    binding.textEmpty.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val groups = snapshot?.documents?.mapNotNull { doc ->
                    val group = doc.toObject(Group::class.java)
                    group?.id = doc.id  // id este var, nu val
                    group
                } ?: emptyList()

                adapter.updateData(groups)
                binding.textEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    override fun onItemClick(group: Group) {
        val intent = Intent(this, GroupDetailsActivity::class.java)
        intent.putExtra("groupId", group.id)
        startActivity(intent)
    }
}
