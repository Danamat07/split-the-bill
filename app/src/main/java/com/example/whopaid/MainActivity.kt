package com.example.whopaid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.databinding.ActivityMainBinding
import com.example.whopaid.ui.auth.LoginActivity
import com.example.whopaid.ui.auth.RegisterActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force logout every time the app starts
        FirebaseAuth.getInstance().signOut()

        // Navigate to Login
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Navigate to Register
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Nu există btnShareLocation aici → șterge orice referire la el
    }
}
