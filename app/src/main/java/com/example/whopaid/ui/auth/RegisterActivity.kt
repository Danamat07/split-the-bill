package com.example.whopaid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityRegisterBinding
import com.example.whopaid.ui.groups.GroupsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen for user registration.
 * Handles validation and Firebase signup.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val repo = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register button click
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirmPassword.text.toString()

            if (!validate(name, email, phone, password, confirm)) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val result = repo.register(name, email, password, phone)
                binding.progressBar.visibility = View.GONE
                if (result.isSuccess) {
                    Toast.makeText(this@RegisterActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegisterActivity, GroupsActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Navigate to LoginActivity if user already has an account
        binding.tvHaveAccount.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    /**
     * Validates all user input fields.
     */
    private fun validate(name: String, email: String, phone: String, password: String, confirm: String): Boolean {
        var isValid = true

        // Name
        if (name.isEmpty()) {
            binding.etName.error = "Please enter your name"
            isValid = false
        }

        // Email
        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Invalid email format"
            isValid = false
        }

        // Phone
        if (phone.isEmpty()) {
            binding.etPhone.error = "Please enter your phone number"
            isValid = false
        } else if (!phone.matches(Regex("^[0-9]{10}$"))) {
            binding.etPhone.error = "Phone number must contain exactly 10 digits"
            isValid = false
        }

        // Password
        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter a password"
            isValid = false
        } else if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            isValid = false
        }

        // Confirm password
        if (confirm.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (password != confirm) {
            binding.etConfirmPassword.error = "Passwords do not match"
            isValid = false
        }

        return isValid
    }
}
