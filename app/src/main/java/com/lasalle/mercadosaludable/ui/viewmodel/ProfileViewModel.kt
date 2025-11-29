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
 * ViewModel para manejar el perfil del usuario
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // LiveData del usuario actual
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    // LiveData para el estado de actualización
    private val _updateState = MutableLiveData<UpdateState>()
    val updateState: LiveData<UpdateState> = _updateState

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
    }

    /**
     * Carga el perfil del usuario
     */
    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                _currentUser.value = user
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Error al cargar perfil")
            }
        }
    }

    /**
     * Obtiene el usuario como LiveData
     */
    fun getUserLiveData(userId: String): LiveData<User?> {
        return repository.getUserLiveData(userId)
    }

    /**
     * Actualiza el perfil del usuario
     */
    fun updateProfile(
        user: User,
        weight: Double? = null,
        height: Double? = null,
        medicalConditions: List<String>? = null,
        allergies: List<String>? = null,
        nutritionalGoal: String? = null,
        monthlyBudget: Double? = null
    ) {
        _updateState.value = UpdateState.Loading

        viewModelScope.launch {
            try {
                // Actualizar campos si se proporcionan
                var updatedUser = user.copy(updatedAt = System.currentTimeMillis())

                weight?.let {
                    updatedUser = updatedUser.copy(
                        weight = it,
                        bmi = User.calculateBMI(it, updatedUser.height)
                    )
                }

                height?.let {
                    updatedUser = updatedUser.copy(
                        height = it,
                        bmi = User.calculateBMI(updatedUser.weight, it)
                    )
                }

                medicalConditions?.let {
                    updatedUser = updatedUser.copy(medicalConditions = it.joinToString(","))
                }

                allergies?.let {
                    updatedUser = updatedUser.copy(allergies = it.joinToString(","))
                }

                nutritionalGoal?.let {
                    updatedUser = updatedUser.copy(nutritionalGoal = it)
                }

                monthlyBudget?.let {
                    updatedUser = updatedUser.copy(monthlyBudget = it)
                }

                // Actualizar en el repository
                repository.updateUser(updatedUser)
                _currentUser.value = updatedUser
                _updateState.value = UpdateState.Success

            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Error al actualizar perfil")
            }
        }
    }

    /**
     * Estados de actualización
     */
    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        object Success : UpdateState()
        data class Error(val message: String) : UpdateState()
    }
}