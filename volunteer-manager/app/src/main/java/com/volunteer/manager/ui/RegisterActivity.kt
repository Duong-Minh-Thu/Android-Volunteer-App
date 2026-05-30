package com.volunteer.manager.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.volunteer.manager.databinding.ActivityRegisterBinding
import com.volunteer.manager.models.User

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val roles = arrayOf("Student", "ORG")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = adapter

        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString()
            val email = binding.etEmail.text.toString()
            val pass = binding.etPassword.text.toString()
            val role = binding.spinnerRole.selectedItem.toString()

            if (email.isNotEmpty() && pass.isNotEmpty() && name.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: ""
                        val user = User(uid, name, email, role)
                        FirebaseDatabase.getInstance().getReference("users").child(uid).setValue(user)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            }
                    } else {
                        Toast.makeText(this, "Registration Failed: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.tvToLogin.setOnClickListener {
            finish()
        }
    }
}
