package com.example.whopaid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.databinding.ActivityMainBinding
import com.example.whopaid.ui.auth.LoginActivity
import com.example.whopaid.ui.auth.RegisterActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Landing screen (first page of the app).
 * Always shown when the app launches.
 * Allows the user to choose between Login or Register.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force logout every time the app starts.
        // This ensures the user always sees the login/register screen.
        FirebaseAuth.getInstance().signOut()

        // Handle "Login" button click → navigate to LoginActivity
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Handle "Register" button click → navigate to RegisterActivity
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
