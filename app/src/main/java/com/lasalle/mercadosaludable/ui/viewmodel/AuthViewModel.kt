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
 * ViewModel para manejar la autenticación de usuarios.
 * MODIFICADO: Sincroniza automáticamente recetas desde Firebase al hacer login.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // LiveData para el estado de autenticación
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    // LiveData para mensajes de error
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // NUEVO: LiveData para progreso de sincronización
    private val _syncProgress = MutableLiveData<String>()
    val syncProgress: LiveData<String> = _syncProgress

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
        _syncProgress.value = "Creando cuenta..."

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
                    val userId = result.getOrNull()!!

                    // NUEVO: Sincronizar recetas después del registro
                    _syncProgress.value = "Cargando recetas saludables..."
                    repository.syncRecipesFromFirebase()

                    _authState.value = AuthState.Authenticated(userId)
                    _syncProgress.value = ""
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al registrar")
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Error al registrar"
                    _syncProgress.value = ""
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
                _errorMessage.value = e.message ?: "Error desconocido"
                _syncProgress.value = ""
            }
        }
    }

    /**
     * Inicia sesión con email y contraseña.
     * MODIFICADO: Sincroniza automáticamente recetas desde Firebase.
     */
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email y contraseña son requeridos"
            return
        }

        _authState.value = AuthState.Loading
        _syncProgress.value = "Iniciando sesión..."

        viewModelScope.launch {
            try {
                // Login en repository (ya incluye sincronización de recetas)
                val result = repository.loginUser(email, password)

                if (result.isSuccess) {
                    val userId = result.getOrNull()!!

                    // El repository ya sincronizó las recetas automáticamente
                    _syncProgress.value = "Cargando datos..."

                    _authState.value = AuthState.Authenticated(userId)
                    _syncProgress.value = ""
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al iniciar sesión")
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Error al iniciar sesión"
                    _syncProgress.value = ""
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
                _errorMessage.value = e.message ?: "Error desconocido"
                _syncProgress.value = ""
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