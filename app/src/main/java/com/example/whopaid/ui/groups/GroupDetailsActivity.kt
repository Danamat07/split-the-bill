package com.example.whopaid.ui.groups

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whopaid.AuthRepository
import com.example.whopaid.R
import com.example.whopaid.databinding.ActivityGroupDetailsBinding
import com.example.whopaid.models.Group
import com.example.whopaid.models.User
import com.example.whopaid.repo.GroupRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Details screen for a single group.
 * Shows group name, description and members (names resolved from Firestore).
 *
 * Admin can:
 *  - Add members by email
 *  - Remove existing members
 *  - Delete the group
 *  - Generate QR code for others to join
 * Non-admin members can leave the group.
 *
 * All members can:
 *  - View and add expenses
 */
class GroupDetailsActivity : AppCompatActivity(), MemberAdapter.OnMemberLongClickListener {

    private lateinit var binding: ActivityGroupDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()
    private val groupRepo = GroupRepository()

    private var groupId: String? = null
    private var currentGroup: Group? = null
    private lateinit var adapter: MemberAdapter
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            finish()
            return
        }

        adapter = MemberAdapter(listOf(), this)
        binding.recyclerMembers.layoutManager = LinearLayoutManager(this)
        binding.recyclerMembers.adapter = adapter

        // --- BUTTON HANDLERS ---
        binding.btnAddMember.setOnClickListener { showAddMemberDialog() }
        binding.btnDeleteGroup.setOnClickListener { confirmAndDelete() }
        binding.btnLeaveGroup.setOnClickListener { leaveGroup() }

        // open expenses screen
        binding.btnViewExpenses.setOnClickListener {
            val intent = Intent(this, com.example.whopaid.ui.expenses.ExpensesActivity::class.java)
            intent.putExtra("groupId", groupId)
            startActivity(intent)
        }

        // new: open QR generator screen (admin only)
        binding.btnShowQr.setOnClickListener {
            val gid = groupId ?: return@setOnClickListener
            val intent = Intent(this, ShowGroupQrActivity::class.java)
            intent.putExtra("groupId", gid)
            startActivity(intent)
        }

        // Load group and listen for real-time updates
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

    /**
     * Renders group data (name, description, members).
     */
    private fun renderGroup() {
        val group = currentGroup ?: return
        binding.tvGroupName.text = group.name
        binding.tvDescription.text = group.description
        val current = authRepo.currentUser()
        isAdmin = (current != null && current.uid == group.adminUid)

        // Show/hide buttons based on role
        binding.btnAddMember.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.btnDeleteGroup.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.btnShowQr.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.btnLeaveGroup.visibility = if (isAdmin) View.GONE else View.VISIBLE

        // Load members' names
        if (group.members.isEmpty()) {
            adapter.updateData(listOf())
            return
        }

        db.collection("users")
            .whereIn("__name__", group.members)
            .get()
            .addOnSuccessListener { snap ->
                val members = snap.documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)
                }
                adapter.updateData(members)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load members", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Shows a dialog allowing the admin to add a member by email.
     */
    private fun showAddMemberDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_member, null)
        val edit = view.findViewById<EditText>(R.id.etMemberEmail)
        AlertDialog.Builder(this)
            .setTitle("Add member by email")
            .setView(view)
            .setPositiveButton("Add") { dialog, _ ->
                val email = edit.text.toString().trim()
                if (email.isNotEmpty()) addMemberByEmail(email)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Adds a new member (admin-only operation).
     */
    private fun addMemberByEmail(email: String) {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.addMemberByEmail(gid, email)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "Member added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@GroupDetailsActivity,
                    "Error: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Leaves the group (for non-admin members).
     */
    private fun leaveGroup() {
        val gid = groupId ?: return
        val current = authRepo.currentUser() ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.leaveGroup(gid, current.uid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "You left the group", Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                Toast.makeText(
                    this@GroupDetailsActivity,
                    "Error: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Confirms deletion of the group (admin-only).
     */
    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete group")
            .setMessage("Are you sure you want to permanently delete this group for everyone?")
            .setPositiveButton("Delete") { _, _ -> deleteGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Deletes the group completely (admin-only).
     */
    private fun deleteGroup() {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.deleteGroup(gid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "Group deleted", Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                Toast.makeText(
                    this@GroupDetailsActivity,
                    "Error: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Called when an admin long-presses a member in the list.
     */
    override fun onMemberLongClick(user: User) {
        if (!isAdmin) return
        if (user.uid == currentGroup?.adminUid) {
            Toast.makeText(this, "Admin cannot remove themselves", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Remove member")
            .setMessage("Remove ${user.name} from the group?")
            .setPositiveButton("Remove") { _, _ -> removeMember(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Removes a member from the group (admin-only).
     */
    private fun removeMember(user: User) {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.removeMember(gid, user.uid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "Member removed", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(
                    this@GroupDetailsActivity,
                    "Error: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
