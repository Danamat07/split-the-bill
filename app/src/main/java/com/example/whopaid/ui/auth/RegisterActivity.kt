package com.example.whopaid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.AuthRepository
import com.example.whopaid.MainActivity
import com.example.whopaid.databinding.ActivityRegisterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen for user registration.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val repo = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // When the register button is clicked
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirmPassword.text.toString()

            // Validate input fields before proceeding
            if (!validate(name, email, phone, password, confirm)) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            // Use coroutine to handle async Firebase operations
            CoroutineScope(Dispatchers.Main).launch {
                val result = repo.register(name, email, password, phone)
                binding.progressBar.visibility = View.GONE
                if (result.isSuccess) {
                    Toast.makeText(this@RegisterActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
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
     * Validates user input fields.
     */
    private fun validate(name: String, email: String, phone: String, password: String, confirm: String): Boolean {
        if (name.isEmpty()) { binding.etName.error = "Enter your name"; return false }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.etEmail.error = "Invalid email"; return false }
        if (phone.isEmpty()) { binding.etPhone.error = "Enter phone number"; return false }
        if (password.length < 6) { binding.etPassword.error = "Password must be at least 6 characters"; return false }
        if (password != confirm) { binding.etConfirmPassword.error = "Passwords do not match"; return false }
        return true
    }
}
