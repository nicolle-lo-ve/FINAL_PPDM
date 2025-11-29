package com.lasalle.mercadosaludable.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lasalle.mercadosaludable.data.local.AppDatabase
import com.lasalle.mercadosaludable.data.model.User
import com.lasalle.mercadosaludable.data.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * ViewModel para manejar la autenticación de usuarios
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // LiveData para el estado de autenticación
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    // LiveData para mensajes de error
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
        _authState.value = AuthState.Initial
    }

    /**
     * Registra un nuevo usuario
     */
    fun register(
        name: String,
        email: String,
        password: String,
        age: Int,
        gender: String,
        weight: Double,
        height: Double,
        medicalConditions: List<String>,
        allergies: List<String>,
        nutritionalGoal: String,
        monthlyBudget: Double
    ) {
        // Validaciones
        if (name.isBlank()) {
            _errorMessage.value = "El nombre es requerido"
            return
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _errorMessage.value = "Email inválido"
            return
        }
        if (password.length < 6) {
            _errorMessage.value = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        if (age < 18 || age > 100) {
            _errorMessage.value = "Edad inválida"
            return
        }
        if (weight <= 0 || height <= 0) {
            _errorMessage.value = "Peso y altura deben ser mayores a 0"
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                // Calcular IMC
                val bmi = User.calculateBMI(weight, height)

                // Crear objeto User
                val user = User(
                    id = "", // Se asignará en el repository
                    name = name,
                    email = email,
                    age = age,
                    gender = gender,
                    weight = weight,
                    height = height,
                    bmi = bmi,
                    medicalConditions = medicalConditions.joinToString(","),
                    allergies = allergies.joinToString(","),
                    nutritionalGoal = nutritionalGoal,
                    monthlyBudget = monthlyBudget
                )

                // Registrar en el repository
                val result = repository.registerUser(email, password, user)

                if (result.isSuccess) {
                    _authState.value = AuthState.Authenticated(result.getOrNull()!!)
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al registrar")
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Error al registrar"
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
                _errorMessage.value = e.message ?: "Error desconocido"
            }
        }
    }

    /**
     * Inicia sesión con email y contraseña
     */
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email y contraseña son requeridos"
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val result = repository.loginUser(email, password)

                if (result.isSuccess) {
                    _authState.value = AuthState.Authenticated(result.getOrNull()!!)
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al iniciar sesión")
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Error al iniciar sesión"
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
                _errorMessage.value = e.message ?: "Error desconocido"
            }
        }
    }

    /**
     * Estados posibles de autenticación
     */
    sealed class AuthState {
        object Initial : AuthState()
        object Loading : AuthState()
        data class Authenticated(val userId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}