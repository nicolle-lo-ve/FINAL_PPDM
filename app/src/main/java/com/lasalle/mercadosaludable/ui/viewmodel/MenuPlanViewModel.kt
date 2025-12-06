package com.lasalle.mercadosaludable.ui.viewmodel

import android.app.Application
import android.util.Log
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
 * ViewModel para gestionar planes de menú semanales.
 * CORREGIDO: Mejor manejo de errores y validaciones.
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

    companion object {
        private const val TAG = "MenuPlanViewModel"
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
        _generationState.value = GenerationState.Idle
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
                Log.e(TAG, "Error loading active menu plan", e)
                _generationState.value = GenerationState.Error(e.message ?: "Error al cargar plan")
            }
        }
    }

    /**
     * Carga las recetas de un plan de menú
     */
    private suspend fun loadMenuRecipes(menuPlan: MenuPlan) {
        try {
            val recipesMap = mutableMapOf<Int, List<Recipe>>()

            for (day in 0..6) {
                val recipeIds = menuPlan.getRecipesForDay(day)
                if (recipeIds.isNotEmpty()) {
                    val recipes = repository.getRecipesByIds(recipeIds)
                    recipesMap[day] = recipes
                }
            }

            _menuRecipes.value = recipesMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading menu recipes", e)
        }
    }

    /**
     * Genera un nuevo plan de menú semanal.
     * CORREGIDO: Usa getAllRecipesDirect() en lugar de LiveData.value
     */
    fun generateWeeklyMenu(user: User) {
        _generationState.value = GenerationState.Loading

        viewModelScope.launch {
            try {
                Log.d(TAG, "=== INICIO GENERACIÓN DE MENÚ ===")
                Log.d(TAG, "Usuario: ${user.name}")
                Log.d(TAG, "Condiciones: ${user.getMedicalConditionsList()}")
                Log.d(TAG, "Alergias: ${user.getAllergiesList()}")

                // CORREGIDO: Obtener recetas de forma directa (no desde LiveData.value)
                val allRecipes = repository.getAllRecipesDirect()

                Log.d(TAG, "Total recetas obtenidas: ${allRecipes.size}")

                if (allRecipes.isEmpty()) {
                    Log.e(TAG, "❌ NO HAY RECETAS DISPONIBLES")
                    _generationState.value = GenerationState.Error(
                        "No hay recetas disponibles. Por favor, espera mientras se sincronizan desde Firebase."
                    )
                    return@launch
                }

                // Filtrar recetas compatibles con el usuario
                val userConditions = user.getMedicalConditionsList()
                val userAllergies = user.getAllergiesList()

                val compatibleRecipes = allRecipes.filter { recipe ->
                    val isCompatible = recipe.isCompatibleWith(userConditions)
                    val hasNoAllergens = !recipe.hasAllergens(userAllergies)
                    isCompatible && hasNoAllergens
                }

                Log.d(TAG, "Recetas compatibles: ${compatibleRecipes.size}")

                if (compatibleRecipes.isEmpty()) {
                    _generationState.value = GenerationState.Error(
                        "No se encontraron recetas compatibles con tu perfil de salud."
                    )
                    return@launch
                }

                // Separar recetas por categoría
                val breakfastRecipes = compatibleRecipes.filter { it.category == "Desayuno" }
                val lunchRecipes = compatibleRecipes.filter { it.category == "Almuerzo" }
                val dinnerRecipes = compatibleRecipes.filter { it.category == "Cena" }

                Log.d(TAG, "Desayunos: ${breakfastRecipes.size}")
                Log.d(TAG, "Almuerzos: ${lunchRecipes.size}")
                Log.d(TAG, "Cenas: ${dinnerRecipes.size}")

                // Validar que hay al menos una receta de cada categoría
                if (breakfastRecipes.isEmpty()) {
                    _generationState.value = GenerationState.Error("No hay desayunos disponibles para tu perfil")
                    return@launch
                }
                if (lunchRecipes.isEmpty()) {
                    _generationState.value = GenerationState.Error("No hay almuerzos disponibles para tu perfil")
                    return@launch
                }
                if (dinnerRecipes.isEmpty()) {
                    _generationState.value = GenerationState.Error("No hay cenas disponibles para tu perfil")
                    return@launch
                }

                // Generar menú para 7 días
                val weekMenu = mutableListOf<String>()
                var totalCalories = 0
                var totalCost = 0.0

                for (day in 0..6) {
                    val dayRecipes = mutableListOf<Long>()

                    // Seleccionar desayuno aleatorio
                    val breakfast = breakfastRecipes.random()
                    dayRecipes.add(breakfast.id)
                    totalCalories += breakfast.calories
                    totalCost += breakfast.estimatedCost

                    // Seleccionar almuerzo aleatorio
                    val lunch = lunchRecipes.random()
                    dayRecipes.add(lunch.id)
                    totalCalories += lunch.calories
                    totalCost += lunch.estimatedCost

                    // Seleccionar cena aleatoria
                    val dinner = dinnerRecipes.random()
                    dayRecipes.add(dinner.id)
                    totalCalories += dinner.calories
                    totalCost += dinner.estimatedCost

                    weekMenu.add(dayRecipes.joinToString(","))
                    Log.d(TAG, "Día $day: Desayuno=${breakfast.name}, Almuerzo=${lunch.name}, Cena=${dinner.name}")
                }

                // Calcular fechas
                val calendar = Calendar.getInstance()
                val startDate = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val endDate = calendar.timeInMillis

                // Calcular promedios
                val avgDailyCalories = totalCalories / 7
                val avgDailyCost = totalCost / 7

                Log.d(TAG, "Total calorías: $totalCalories")
                Log.d(TAG, "Total costo: S/ $totalCost")
                Log.d(TAG, "Promedio diario calorías: $avgDailyCalories kcal")
                Log.d(TAG, "Promedio diario costo: S/ $avgDailyCost")

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
                val savedPlan = menuPlan.copy(id = menuPlanId)

                _activeMenuPlan.value = savedPlan

                // Cargar recetas del nuevo plan
                loadMenuRecipes(savedPlan)

                Log.d(TAG, "✅ Menú creado exitosamente con ID: $menuPlanId")
                Log.d(TAG, "=== FIN GENERACIÓN DE MENÚ ===")

                _generationState.value = GenerationState.Success(savedPlan)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generando menú", e)
                _generationState.value = GenerationState.Error(
                    "Error al generar menú: ${e.message ?: "Error desconocido"}"
                )
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