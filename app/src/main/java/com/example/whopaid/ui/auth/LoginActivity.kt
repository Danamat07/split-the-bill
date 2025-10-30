package com.example.whopaid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.AuthRepository
import com.example.whopaid.MainActivity
import com.example.whopaid.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen for user login.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val repo = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If user is already logged in, skip login screen
        if (repo.currentUser() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Login button click handler
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (!validate(email, password)) return@setOnClickListener

            binding.progressBar.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val result = repo.login(email, password)
                binding.progressBar.visibility = View.GONE
                if (result.isSuccess) {
                    Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Navigate to registration screen
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    /**
     * Validates email and password fields.
     */
    private fun validate(email: String, password: String): Boolean {
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.etEmail.error = "Invalid email"; return false }
        if (password.length < 6) { binding.etPassword.error = "Password must be at least 6 characters"; return false }
        return true
    }
}
