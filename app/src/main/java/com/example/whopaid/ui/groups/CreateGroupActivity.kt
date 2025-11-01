package com.example.whopaid.ui.groups

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityCreateGroupBinding
import com.example.whopaid.models.User
import com.example.whopaid.repo.GroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simple screen to create a group. Creator becomes admin and is added to members.
 */
class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val groupRepo = GroupRepository()
    private val authRepo = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreate.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val desc = binding.etDescription.text.toString().trim()
            if (name.isEmpty()) {
                binding.etName.error = "Enter a name"
                return@setOnClickListener
            }

            val current = authRepo.currentUser()
            if (current == null) {
                Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE

            // Build a simple User model using data stored in Firestore user doc may contain more fields.
            val user = User(uid = current.uid, email = current.email ?: "")

            CoroutineScope(Dispatchers.Main).launch {
                val result = groupRepo.createGroup(name, desc, user)
                binding.progressBar.visibility = View.GONE
                if (result.isSuccess) {
                    Toast.makeText(this@CreateGroupActivity, "Group created", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@CreateGroupActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
