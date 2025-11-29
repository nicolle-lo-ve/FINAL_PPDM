package com.lasalle.mercadosaludable.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lasalle.mercadosaludable.data.local.AppDatabase
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.data.model.User
import com.lasalle.mercadosaludable.data.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * ViewModel para gestionar el catálogo de recetas.
 * MODIFICADO: Incluye sincronización automática con Firebase.
 */
class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // LiveData para las recetas
    val allRecipes: LiveData<List<Recipe>>

    // LiveData para recetas filtradas
    private val _filteredRecipes = MutableLiveData<List<Recipe>>()
    val filteredRecipes: LiveData<List<Recipe>> = _filteredRecipes

    // LiveData para una receta individual
    private val _selectedRecipe = MutableLiveData<Recipe?>()
    val selectedRecipe: LiveData<Recipe?> = _selectedRecipe

    // LiveData para estado de sincronización
    private val _syncState = MutableLiveData<SyncState>()
    val syncState: LiveData<SyncState> = _syncState

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
        allRecipes = repository.getAllRecipes()

        // NUEVO: Sincronizar recetas desde Firebase al iniciar
        syncRecipesFromFirebase()
    }

    /**
     * NUEVO: Sincroniza recetas desde Firebase
     */
    fun syncRecipesFromFirebase() {
        _syncState.value = SyncState.Loading

        viewModelScope.launch {
            try {
                val result = repository.syncRecipesFromFirebase()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    _syncState.value = SyncState.Success(count)
                } else {
                    _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Error al sincronizar")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * NUEVO: Guarda una nueva receta en Firebase
     */
    fun saveRecipeToFirebase(recipe: Recipe) {
        _syncState.value = SyncState.Loading

        viewModelScope.launch {
            try {
                val result = repository.saveRecipeToFirebase(recipe)

                if (result.isSuccess) {
                    _syncState.value = SyncState.Success(1)
                } else {
                    _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Error al guardar")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Busca recetas por texto
     */
    fun searchRecipes(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _filteredRecipes.value = allRecipes.value ?: emptyList()
            } else {
                // Aplicar búsqueda localmente
                val results = allRecipes.value?.filter { recipe ->
                    recipe.name.contains(query, ignoreCase = true) ||
                            recipe.ingredients.contains(query, ignoreCase = true) ||
                            recipe.description.contains(query, ignoreCase = true)
                } ?: emptyList()
                _filteredRecipes.value = results
            }
        }
    }

    /**
     * Filtra recetas por condición médica
     */
    fun filterByCondition(condition: String) {
        viewModelScope.launch {
            val results = allRecipes.value?.filter { recipe ->
                recipe.suitableFor.contains(condition, ignoreCase = true)
            } ?: emptyList()
            _filteredRecipes.value = results
        }
    }

    /**
     * Filtra recetas por categoría
     */
    fun filterByCategory(category: String) {
        viewModelScope.launch {
            val results = allRecipes.value?.filter { recipe ->
                recipe.category.equals(category, ignoreCase = true)
            } ?: emptyList()
            _filteredRecipes.value = results
        }
    }

    /**
     * Filtra recetas que no contengan alergenos del usuario
     */
    fun filterByAllergies(userAllergies: List<String>) {
        viewModelScope.launch {
            val results = allRecipes.value?.filter { recipe ->
                !recipe.hasAllergens(userAllergies)
            } ?: emptyList()
            _filteredRecipes.value = results
        }
    }

    /**
     * Obtiene recetas compatibles con el usuario
     */
    fun getCompatibleRecipes(user: User) {
        viewModelScope.launch {
            val userConditions = user.getMedicalConditionsList()
            val userAllergies = user.getAllergiesList()

            val results = allRecipes.value?.filter { recipe ->
                recipe.isCompatibleWith(userConditions) && !recipe.hasAllergens(userAllergies)
            } ?: emptyList()

            _filteredRecipes.value = results
        }
    }

    /**
     * Carga una receta específica por ID
     */
    fun loadRecipe(recipeId: Long) {
        viewModelScope.launch {
            val recipe = repository.getRecipeById(recipeId)
            _selectedRecipe.value = recipe
        }
    }

    /**
     * Obtiene las recetas más populares
     */
    fun loadPopularRecipes() {
        viewModelScope.launch {
            val popular = allRecipes.value?.sortedByDescending { it.rating }?.take(10) ?: emptyList()
            _filteredRecipes.value = popular
        }
    }

    /**
     * Limpia los filtros
     */
    fun clearFilters() {
        _filteredRecipes.value = allRecipes.value
    }

    /**
     * Estados de sincronización con Firebase
     */
    sealed class SyncState {
        object Idle : SyncState()
        object Loading : SyncState()
        data class Success(val count: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }
}