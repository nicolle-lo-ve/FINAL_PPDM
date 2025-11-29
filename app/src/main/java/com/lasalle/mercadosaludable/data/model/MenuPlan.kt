package com.lasalle.mercadosaludable.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que representa un plan de menú semanal generado para un usuario.
 * Contiene las recetas seleccionadas para cada día y comida de la semana.
 */
@Entity(tableName = "menu_plans")
data class MenuPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Relación con el usuario
    val userId: String, // ID del usuario propietario

    // Información del plan
    val name: String, // Nombre del plan (ej: "Semana del 1-7 Dic")
    val startDate: Long, // Timestamp de inicio de la semana
    val endDate: Long, // Timestamp de fin de la semana

    // Recetas asignadas por día y comida
    // Formato: "idReceta1,idReceta2,idReceta3" (desayuno, almuerzo, cena)
    val monday: String,
    val tuesday: String,
    val wednesday: String,
    val thursday: String,
    val friday: String,
    val saturday: String,
    val sunday: String,

    // Resumen nutricional semanal
    val totalCalories: Int,
    val totalCost: Double,
    val averageDailyCalories: Int,
    val averageDailyCost: Double,

    // Estado del plan
    val isActive: Boolean = true, // Si es el plan actual
    val isFavorite: Boolean = false,

    // Metadata
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Obtiene las recetas de un día específico
     * @param day Día de la semana (0=Lunes, 6=Domingo)
     * @return Lista de IDs de recetas [desayuno, almuerzo, cena]
     */
    fun getRecipesForDay(day: Int): List<Long> {
        val dayString = when (day) {
            0 -> monday
            1 -> tuesday
            2 -> wednesday
            3 -> thursday
            4 -> friday
            5 -> saturday
            6 -> sunday
            else -> ""
        }

        return if (dayString.isBlank()) emptyList()
        else dayString.split(",").map { it.trim().toLong() }
    }

    /**
     * Obtiene todas las recetas del plan como conjunto único
     */
    fun getAllRecipeIds(): Set<Long> {
        val allIds = mutableSetOf<Long>()

        listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday).forEach { day ->
            if (day.isNotBlank()) {
                allIds.addAll(day.split(",").map { it.trim().toLong() })
            }
        }

        return allIds
    }

    /**
     * Obtiene el nombre del día en español
     */
    fun getDayName(day: Int): String {
        return when (day) {
            0 -> "Lunes"
            1 -> "Martes"
            2 -> "Miércoles"
            3 -> "Jueves"
            4 -> "Viernes"
            5 -> "Sábado"
            6 -> "Domingo"
            else -> ""
        }
    }

    /**
     * Verifica si el plan está dentro del presupuesto
     */
    fun isWithinBudget(monthlyBudget: Double): Boolean {
        // Calculamos el presupuesto semanal (asumiendo 4.3 semanas por mes)
        val weeklyBudget = monthlyBudget / 4.3
        return totalCost <= weeklyBudget
    }
}