package com.lasalle.mercadosaludable.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lasalle.mercadosaludable.databinding.ActivityLoginBinding
import com.lasalle.mercadosaludable.ui.viewmodel.AuthViewModel

/**
 * Activity para el inicio de sesión de usuarios.
 * Implementa autenticación con Firebase y validación de campos.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    /**
     * Configura los observers del ViewModel
     */
    private fun setupObservers() {
        // Observer para el estado de autenticación
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> {
                    showLoading(true)
                }
                is AuthViewModel.AuthState.Authenticated -> {
                    showLoading(false)
                    // Guardar sesión
                    MainActivity.saveUserId(this, state.userId)
                    // Ir a MainActivity
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is AuthViewModel.AuthState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    showLoading(false)
                }
            }
        }

        // Observer para mensajes de error
        viewModel.errorMessage.observe(this) { message ->
            if (message.isNotBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Configura los listeners de los botones
     */
    private fun setupClickListeners() {
        // Botón de login
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }

        // Link para ir a registro
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Muestra u oculta el indicador de carga
     */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }
}