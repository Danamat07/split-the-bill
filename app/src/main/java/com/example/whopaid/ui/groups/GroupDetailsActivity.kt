package com.example.whopaid.ui.groups

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityGroupDetailsBinding
import com.example.whopaid.models.Group
import com.example.whopaid.repo.GroupRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Details screen for a single group.
 * Shows group name, description and members (emails resolved from users collection).
 * Admin can add members by email and delete the group.
 * Non-admin members can leave the group.
 */
class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()
    private val groupRepo = GroupRepository()

    private var groupId: String? = null
    private var currentGroup: Group? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            finish()
            return
        }

        binding.btnAddMember.setOnClickListener { showAddMemberDialog() }
        binding.btnDeleteGroup.setOnClickListener { confirmAndDelete() }
        binding.btnLeaveGroup.setOnClickListener { leaveGroup() }

        // Load group and watch for changes
        db.collection("groups").document(groupId!!).addSnapshotListener { snapshot, error ->
            if (error != null) {
                binding.tvGroupName.text = "Error: ${error.message}"
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                Toast.makeText(this, "Group no longer exists", Toast.LENGTH_SHORT).show()
                finish()
                return@addSnapshotListener
            }
            currentGroup = snapshot.toObject(Group::class.java)
            renderGroup()
        }
    }

    private fun renderGroup() {
        val group = currentGroup ?: return
        binding.tvGroupName.text = group.name
        binding.tvDescription.text = group.description
        val current = authRepo.currentUser()
        val isAdmin = (current != null && current.uid == group.adminUid)

        // Control buttons visibility based on role
        binding.btnAddMember.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.btnDeleteGroup.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.btnLeaveGroup.visibility = if (isAdmin) View.GONE else View.VISIBLE

        // Resolve member UIDs to emails for display
        if (group.members.isEmpty()) {
            binding.tvMembers.text = "No members"
            return
        }

        // Get emails of each member from users collection
        db.collection("users")
            .whereIn("__name__", group.members) // __name__ is document id in Firestore query
            .get()
            .addOnSuccessListener { snap ->
                val emails = snap.documents.mapNotNull { it.getString("email") }
                binding.tvMembers.text = emails.joinToString(separator = "\n")
            }
            .addOnFailureListener {
                binding.tvMembers.text = "Failed to load member emails"
            }
    }

    private fun showAddMemberDialog() {
        // Simple input dialog asking for email to add
        val view = LayoutInflater.from(this).inflate(com.example.whopaid.R.layout.dialog_add_member, null)
        val edit = view.findViewById<EditText>(com.example.whopaid.R.id.etMemberEmail)
        AlertDialog.Builder(this)
            .setTitle("Add member by email")
            .setView(view)
            .setPositiveButton("Add") { dialog, _ ->
                val email = edit.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, "Enter an email", Toast.LENGTH_SHORT).show()
                } else {
                    addMemberByEmail(email)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addMemberByEmail(email: String) {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.addMemberByEmail(gid, email)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "Member added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@GroupDetailsActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun leaveGroup() {
        val gid = groupId ?: return
        val current = authRepo.currentUser() ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.leaveGroup(gid, current.uid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "You left the group", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@GroupDetailsActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete group")
            .setMessage("Are you sure you want to permanently delete this group? This will remove it for all members.")
            .setPositiveButton("Delete") { _, _ -> deleteGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteGroup() {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.deleteGroup(gid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "Group deleted", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@GroupDetailsActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
