package com.lasalle.mercadosaludable.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lasalle.mercadosaludable.R
import com.lasalle.mercadosaludable.databinding.ActivityRegisterBinding
import com.lasalle.mercadosaludable.ui.viewmodel.AuthViewModel

/**
 * Activity para el registro de nuevos usuarios.
 * Implementa un formulario completo con validación y creación de perfil de salud.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    // Listas para condiciones médicas y alergias seleccionadas
    private val selectedConditions = mutableListOf<String>()
    private val selectedAllergies = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupClickListeners()
    }

    /**
     * Configura los Spinners con sus opciones
     */
    private fun setupSpinners() {
        // Spinner de género
        val genderOptions = arrayOf("Masculino", "Femenino", "Otro")
        binding.spinnerGender.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genderOptions
        )

        // Spinner de objetivo nutricional
        val goalOptions = arrayOf("Perder peso", "Mantener peso", "Ganar músculo")
        binding.spinnerGoal.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            goalOptions
        )
    }

    /**
     * Configura los observers del ViewModel
     */
    private fun setupObservers() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> {
                    showLoading(true)
                }
                is AuthViewModel.AuthState.Authenticated -> {
                    showLoading(false)
                    MainActivity.saveUserId(this, state.userId)
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
    }

    /**
     * Configura los listeners de los botones y checkboxes
     */
    private fun setupClickListeners() {
        // Checkboxes de condiciones médicas
        binding.cbDiabetes.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedConditions, "diabetes", isChecked)
        }
        binding.cbHipertension.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedConditions, "hipertension", isChecked)
        }
        binding.cbObesidad.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedConditions, "obesidad", isChecked)
        }

        // Checkboxes de alergias
        binding.cbLacteos.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedAllergies, "lacteos", isChecked)
        }
        binding.cbGluten.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedAllergies, "gluten", isChecked)
        }
        binding.cbMariscos.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedAllergies, "mariscos", isChecked)
        }
        binding.cbHuevo.setOnCheckedChangeListener { _, isChecked ->
            updateList(selectedAllergies, "huevo", isChecked)
        }

        // Botón de registro
        binding.btnRegister.setOnClickListener {
            validateAndRegister()
        }

        // Link para volver a login
        binding.tvGoToLogin.setOnClickListener {
            finish()
        }
    }

    /**
     * Actualiza una lista según el estado del checkbox
     */
    private fun updateList(list: MutableList<String>, item: String, isChecked: Boolean) {
        if (isChecked) {
            if (!list.contains(item)) list.add(item)
        } else {
            list.remove(item)
        }
    }

    /**
     * Valida el formulario y registra al usuario
     */
    private fun validateAndRegister() {
        val name = binding.etName.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val ageStr = binding.etAge.text.toString()
        val weightStr = binding.etWeight.text.toString()
        val heightStr = binding.etHeight.text.toString()
        val budgetStr = binding.etBudget.text.toString()
        val gender = binding.spinnerGender.selectedItem.toString()
        val goal = binding.spinnerGoal.selectedItem.toString()

        // Validaciones locales
        if (password != confirmPassword) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return
        }

        if (ageStr.isBlank() || weightStr.isBlank() || heightStr.isBlank() || budgetStr.isBlank()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageStr.toIntOrNull() ?: 0
        val weight = weightStr.toDoubleOrNull() ?: 0.0
        val height = heightStr.toDoubleOrNull() ?: 0.0
        val budget = budgetStr.toDoubleOrNull() ?: 0.0

        // Convertir objetivo a formato interno
        val goalInternal = when (goal) {
            "Perder peso" -> "perder_peso"
            "Mantener peso" -> "mantener"
            "Ganar músculo" -> "ganar_musculo"
            else -> "mantener"
        }

        // Registrar usuario
        viewModel.register(
            name = name,
            email = email,
            password = password,
            age = age,
            gender = gender,
            weight = weight,
            height = height,
            medicalConditions = selectedConditions,
            allergies = selectedAllergies,
            nutritionalGoal = goalInternal,
            monthlyBudget = budget
        )
    }

    /**
     * Muestra u oculta el indicador de carga
     */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        binding.scrollView.isEnabled = !isLoading
    }
}