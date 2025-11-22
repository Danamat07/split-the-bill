package com.example.whopaid.ui.groups

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityGroupDetailsBinding
import com.example.whopaid.models.Group
import com.example.whopaid.models.User
import com.example.whopaid.repo.GroupRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

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
        binding.btnViewExpenses.setOnClickListener {
            val intent = android.content.Intent(this, com.example.whopaid.ui.expenses.ExpensesActivity::class.java)
            intent.putExtra("groupId", groupId)
            startActivity(intent)
        }

        // NEW: open live map
        binding.fabLiveMap.setOnClickListener {
            val intent = Intent(this, GroupMapActivity::class.java)
            intent.putExtra("groupId", groupId)
            startActivity(intent)
        }

        // NEW: open location settings
        binding.fabLocationSettings.setOnClickListener {
            val intent = Intent(this, LocationSettingsActivity::class.java)
            intent.putExtra("groupId", groupId)
            startActivity(intent)
        }

        // --- Generate QR in same page ---
        binding.btnShowQr.setOnClickListener {
            val group = currentGroup
            val gid = group?.id

            if (gid == null) {
                Toast.makeText(this, "Grupul nu este încă încărcat, încearcă din nou.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val payload = group.qrPayload ?: "JOIN_GROUP:$gid"

            if (group.qrPayload == null) {
                val updates = mapOf("qrPayload" to payload)
                db.collection("groups").document(gid)
                    .update(updates)
                    .addOnSuccessListener {
                        group.qrPayload = payload
                        showQrInView(payload)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Eroare salvare QR: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                showQrInView(payload)
            }
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

        // Load members
        if (group.members.isEmpty()) {
            adapter.updateData(listOf())
            return
        }
        db.collection("users")
            .whereIn("__name__", group.members)
            .get()
            .addOnSuccessListener { snap ->
                val members = snap.documents.mapNotNull { it.toObject(User::class.java) }
                adapter.updateData(members)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load members", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddMemberDialog() {
        val view = LayoutInflater.from(this).inflate(com.example.whopaid.R.layout.dialog_add_member, null)
        val edit = view.findViewById<EditText>(com.example.whopaid.R.id.etMemberEmail)
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
            .setMessage("Are you sure you want to permanently delete this group for everyone?")
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

    private fun removeMember(user: User) {
        val gid = groupId ?: return
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val result = groupRepo.removeMember(gid, user.uid)
            binding.progressBar.visibility = View.GONE
            if (result.isSuccess) {
                Toast.makeText(this@GroupDetailsActivity, "Member removed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@GroupDetailsActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- QR generation & display ---
    private fun generateQrBitmap(text: String, width: Int = 800, height: Int = 800): Bitmap? {
        return try {
            val hints = Hashtable<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H

            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
            )

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun showQrInView(payload: String) {
        binding.progressQr.visibility = View.VISIBLE
        binding.imageGroupQr.visibility = View.GONE

        val bitmap = generateQrBitmap(payload)

        binding.progressQr.visibility = View.GONE
        if (bitmap != null) {
            binding.imageGroupQr.setImageBitmap(bitmap)
            binding.imageGroupQr.visibility = View.VISIBLE
        } else {
            Toast.makeText(this, "Eroare generare QR", Toast.LENGTH_SHORT).show()
        }
    }
}
