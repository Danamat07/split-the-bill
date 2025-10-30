package com.example.whopaid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.databinding.ActivityMainBinding
import com.example.whopaid.ui.auth.LoginActivity

/**
 * Main screen shown after successful login or registration.
 * Displays user info and allows logout.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repo = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get current Firebase user
        val current = repo.currentUser()

        // Display user's email
        binding.tvWelcome.text = if (current != null) {
            "Welcome, ${current.email}"
        } else {
            "Welcome!"
        }

        // Logout button handler
        binding.btnLogout.setOnClickListener {
            repo.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
