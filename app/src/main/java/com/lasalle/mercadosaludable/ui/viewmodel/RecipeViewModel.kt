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
 * ViewModel para gestionar el catálogo de recetas
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

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
        allRecipes = repository.getAllRecipes()

        // Insertar recetas de ejemplo si la base está vacía
        viewModelScope.launch {
            repository.insertSampleRecipes()
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
}