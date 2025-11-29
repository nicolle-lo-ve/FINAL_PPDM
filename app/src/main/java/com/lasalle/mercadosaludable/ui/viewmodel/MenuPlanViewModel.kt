package com.lasalle.mercadosaludable.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lasalle.mercadosaludable.data.local.AppDatabase
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.data.model.MenuPlan
import com.lasalle.mercadosaludable.data.model.User
import com.lasalle.mercadosaludable.data.repository.AppRepository
import kotlinx.coroutines.launch
import java.util.Calendar



/**
 * ViewModel para gestionar planes de menú semanales
 */
class MenuPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // LiveData para el plan activo
    private val _activeMenuPlan = MutableLiveData<MenuPlan?>()
    val activeMenuPlan: LiveData<MenuPlan?> = _activeMenuPlan

    // LiveData para recetas del plan
    private val _menuRecipes = MutableLiveData<Map<Int, List<Recipe>>>()
    val menuRecipes: LiveData<Map<Int, List<Recipe>>> = _menuRecipes

    // LiveData para el estado de generación
    private val _generationState = MutableLiveData<GenerationState>()
    val generationState: LiveData<GenerationState> = _generationState

    // LiveData para historial de planes
    private val _menuHistory = MutableLiveData<List<MenuPlan>>()
    val menuHistory: LiveData<List<MenuPlan>> = _menuHistory

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
    }

    /**
     * Carga el plan de menú activo
     */
    fun loadActiveMenuPlan(userId: String) {
        viewModelScope.launch {
            try {
                val plan = repository.getActiveMenuPlan(userId)
                _activeMenuPlan.value = plan

                // Cargar recetas del plan
                plan?.let { loadMenuRecipes(it) }
            } catch (e: Exception) {
                _generationState.value = GenerationState.Error(e.message ?: "Error al cargar plan")
            }
        }
    }

    /**
     * Carga las recetas de un plan de menú
     */
    private suspend fun loadMenuRecipes(menuPlan: MenuPlan) {
        val recipesMap = mutableMapOf<Int, List<Recipe>>()

        for (day in 0..6) {
            val recipeIds = menuPlan.getRecipesForDay(day)
            if (recipeIds.isNotEmpty()) {
                val recipes = repository.getRecipesByIds(recipeIds)
                recipesMap[day] = recipes
            }
        }

        _menuRecipes.value = recipesMap
    }

    /**
     * Genera un nuevo plan de menú semanal
     */
    fun generateWeeklyMenu(user: User) {
        _generationState.value = GenerationState.Loading

        viewModelScope.launch {
            try {
                // Obtener todas las recetas disponibles
                val allRecipes = repository.getAllRecipes().value ?: emptyList()

                if (allRecipes.isEmpty()) {
                    _generationState.value = GenerationState.Error("No hay recetas disponibles")
                    return@launch
                }

                // Filtrar recetas compatibles con el usuario
                val userConditions = user.getMedicalConditionsList()
                val userAllergies = user.getAllergiesList()

                val compatibleRecipes = allRecipes.filter { recipe ->
                    recipe.isCompatibleWith(userConditions) && !recipe.hasAllergens(userAllergies)
                }

                if (compatibleRecipes.isEmpty()) {
                    _generationState.value = GenerationState.Error("No hay recetas compatibles con tu perfil")
                    return@launch
                }

                // Separar recetas por categoría
                val breakfastRecipes = compatibleRecipes.filter { it.category == "Desayuno" }
                val lunchRecipes = compatibleRecipes.filter { it.category == "Almuerzo" }
                val dinnerRecipes = compatibleRecipes.filter { it.category == "Cena" }

                // Generar menú para 7 días
                val weekMenu = mutableListOf<String>()
                var totalCalories = 0
                var totalCost = 0.0

                for (day in 0..6) {
                    val dayRecipes = mutableListOf<Long>()

                    // Seleccionar desayuno
                    if (breakfastRecipes.isNotEmpty()) {
                        val breakfast = breakfastRecipes.random()
                        dayRecipes.add(breakfast.id)
                        totalCalories += breakfast.calories
                        totalCost += breakfast.estimatedCost
                    }

                    // Seleccionar almuerzo
                    if (lunchRecipes.isNotEmpty()) {
                        val lunch = lunchRecipes.random()
                        dayRecipes.add(lunch.id)
                        totalCalories += lunch.calories
                        totalCost += lunch.estimatedCost
                    }

                    // Seleccionar cena
                    if (dinnerRecipes.isNotEmpty()) {
                        val dinner = dinnerRecipes.random()
                        dayRecipes.add(dinner.id)
                        totalCalories += dinner.calories
                        totalCost += dinner.estimatedCost
                    }

                    weekMenu.add(dayRecipes.joinToString(","))
                }

                // Calcular fechas
                val calendar = Calendar.getInstance()
                val startDate = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val endDate = calendar.timeInMillis

                // Calcular promedios
                val avgDailyCalories = totalCalories / 7
                val avgDailyCost = totalCost / 7

                // Crear el plan de menú
                val menuPlan = MenuPlan(
                    userId = user.id,
                    name = "Menú Semanal - ${formatDate(startDate)}",
                    startDate = startDate,
                    endDate = endDate,
                    monday = weekMenu.getOrElse(0) { "" },
                    tuesday = weekMenu.getOrElse(1) { "" },
                    wednesday = weekMenu.getOrElse(2) { "" },
                    thursday = weekMenu.getOrElse(3) { "" },
                    friday = weekMenu.getOrElse(4) { "" },
                    saturday = weekMenu.getOrElse(5) { "" },
                    sunday = weekMenu.getOrElse(6) { "" },
                    totalCalories = totalCalories,
                    totalCost = totalCost,
                    averageDailyCalories = avgDailyCalories,
                    averageDailyCost = avgDailyCost,
                    isActive = true
                )

                // Guardar el plan
                val menuPlanId = repository.createMenuPlan(menuPlan)
                _activeMenuPlan.value = menuPlan.copy(id = menuPlanId)

                // Cargar recetas del nuevo plan
                loadMenuRecipes(menuPlan.copy(id = menuPlanId))

                _generationState.value = GenerationState.Success(menuPlan.copy(id = menuPlanId))

            } catch (e: Exception) {
                _generationState.value = GenerationState.Error(e.message ?: "Error al generar menú")
            }
        }
    }

    /**
     * Carga el historial de planes del usuario
     */
    fun loadMenuHistory(userId: String) {
        viewModelScope.launch {
            repository.getMenuPlansByUser(userId).observeForever { plans ->
                _menuHistory.value = plans
            }
        }
    }

    /**
     * Marca un plan como favorito
     */
    fun toggleFavorite(menuPlan: MenuPlan) {
        viewModelScope.launch {
            val updatedPlan = menuPlan.copy(isFavorite = !menuPlan.isFavorite)
            repository.updateMenuPlan(updatedPlan)
        }
    }

    /**
     * Formatea una fecha en formato legible
     */
    private fun formatDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        return "$day/$month"
    }

    /**
     * Estados de generación de menú
     */
    sealed class GenerationState {
        object Idle : GenerationState()
        object Loading : GenerationState()
        data class Success(val menuPlan: MenuPlan) : GenerationState()
        data class Error(val message: String) : GenerationState()
    }
}