package com.lasalle.mercadosaludable.ui.viewmodel

import android.app.Application
import android.util.Log
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
 * MODIFICADO: Logs detallados para debugging
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

    companion object {
        private const val TAG = "AuthViewModel"
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
        _authState.value = AuthState.Initial
    }

    /**
     * Registra un nuevo usuario - CON LOGS DETALLADOS
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
        Log.d(TAG, "=== INICIO REGISTRO ===")
        Log.d(TAG, "Nombre: $name")
        Log.d(TAG, "Email: $email")
        Log.d(TAG, "Edad: $age")
        Log.d(TAG, "Peso: $weight, Altura: $height")

        // Validaciones
        if (name.isBlank()) {
            Log.e(TAG, "ERROR: Nombre vacío")
            _errorMessage.value = "El nombre es requerido"
            return
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Log.e(TAG, "ERROR: Email inválido: $email")
            _errorMessage.value = "Email inválido"
            return
        }
        if (password.length < 6) {
            Log.e(TAG, "ERROR: Contraseña muy corta")
            _errorMessage.value = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        if (age < 18 || age > 100) {
            Log.e(TAG, "ERROR: Edad inválida: $age")
            _errorMessage.value = "Edad inválida"
            return
        }
        if (weight <= 0 || height <= 0) {
            Log.e(TAG, "ERROR: Peso o altura inválidos: $weight, $height")
            _errorMessage.value = "Peso y altura deben ser mayores a 0"
            return
        }

        Log.d(TAG, "Validaciones pasadas, iniciando registro...")
        _authState.value = AuthState.Loading
        _syncProgress.value = "Creando cuenta..."

        viewModelScope.launch {
            try {
                Log.d(TAG, "Calculando IMC...")
                // Calcular IMC
                val bmi = User.calculateBMI(weight, height)
                Log.d(TAG, "IMC calculado: $bmi")

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

                Log.d(TAG, "User object creado: ${user.name}, ${user.email}")
                Log.d(TAG, "Llamando a repository.registerUser()...")

                // Registrar en el repository
                val result = repository.registerUser(email, password, user)

                Log.d(TAG, "Resultado del registro: ${result.isSuccess}")

                if (result.isSuccess) {
                    val userId = result.getOrNull()!!
                    Log.d(TAG, "✅ Usuario registrado exitosamente con ID: $userId")

                    // Sincronizar recetas
                    Log.d(TAG, "Sincronizando recetas...")
                    _syncProgress.value = "Cargando recetas saludables..."
                    repository.syncRecipesFromFirebase()
                    Log.d(TAG, "Recetas sincronizadas")

                    _authState.value = AuthState.Authenticated(userId)
                    _syncProgress.value = ""
                    Log.d(TAG, "=== REGISTRO COMPLETADO ===")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "❌ Error en el registro: ${error?.message}", error)
                    _authState.value = AuthState.Error(error?.message ?: "Error al registrar")
                    _errorMessage.value = error?.message ?: "Error al registrar"
                    _syncProgress.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción durante el registro", e)
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
                _errorMessage.value = e.message ?: "Error desconocido"
                _syncProgress.value = ""
            }
        }
    }

    /**
     * Inicia sesión con email y contraseña.
     */
    fun login(email: String, password: String) {
        Log.d(TAG, "=== INICIO LOGIN ===")
        Log.d(TAG, "Email: $email")

        if (email.isBlank() || password.isBlank()) {
            Log.e(TAG, "ERROR: Email o contraseña vacíos")
            _errorMessage.value = "Email y contraseña son requeridos"
            return
        }

        _authState.value = AuthState.Loading
        _syncProgress.value = "Iniciando sesión..."

        viewModelScope.launch {
            try {
                Log.d(TAG, "Llamando a repository.loginUser()...")
                val result = repository.loginUser(email, password)

                Log.d(TAG, "Resultado del login: ${result.isSuccess}")

                if (result.isSuccess) {
                    val userId = result.getOrNull()!!
                    Log.d(TAG, "✅ Login exitoso con ID: $userId")

                    _syncProgress.value = "Cargando datos..."
                    _authState.value = AuthState.Authenticated(userId)
                    _syncProgress.value = ""
                    Log.d(TAG, "=== LOGIN COMPLETADO ===")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "❌ Error en el login: ${error?.message}", error)
                    _authState.value = AuthState.Error(error?.message ?: "Error al iniciar sesión")
                    _errorMessage.value = error?.message ?: "Error al iniciar sesión"
                    _syncProgress.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción durante el login", e)
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