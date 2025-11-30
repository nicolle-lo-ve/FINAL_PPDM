package com.lasalle.mercadosaludable.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Información básica
    val name: String = "",
    val description: String = "",
    val category: String = "", // "Desayuno", "Almuerzo", "Cena", "Snack"
    val imageUrl: String = "", // URL o nombre del drawable

    // Información nutricional (por porción)
    val calories: Int = 0, // Calorías
    val protein: Double = 0.0, // Proteínas en gramos
    val carbohydrates: Double = 0.0, // Carbohidratos en gramos
    val fats: Double = 0.0, // Grasas en gramos
    val fiber: Double = 0.0, // Fibra en gramos
    val sodium: Double = 0.0, // Sodio en mg
    val sugar: Double = 0.0, // Azúcar en gramos

    // Clasificación por condición médica (separadas por comas)
    val suitableFor: String = "", // "diabetes,hipertension,obesidad"

    // Ingredientes (separados por punto y coma)
    val ingredients: String = "", // "2 huevos;1 tomate;sal al gusto"

    // Preparación (separada por punto y coma para cada paso)
    val instructions: String = "", // "Batir los huevos;Picar el tomate;Cocinar por 5 min"

    // Tiempo y dificultad
    val preparationTime: Int = 0, // Tiempo en minutos
    val difficulty: String = "", // "Fácil", "Media", "Difícil"
    val servings: Int = 1, // Número de porciones

    // Costo estimado
    val estimatedCost: Double = 0.0, // Costo en soles

    // Restricciones
    val allergens: String = "", // "lacteos,gluten,huevo" (separados por comas)

    // Popularidad
    val rating: Double = 0.0, // Calificación de 0 a 5
    val timesUsed: Int = 0, // Veces que se ha usado en menús

    // Metadata
    val createdAt: Long = System.currentTimeMillis()
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this(0, "", "", "", "", 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "", "", "", 0, "", 1, 0.0, "", 0.0, 0, System.currentTimeMillis())

    /**
     * Obtiene la lista de condiciones para las que es adecuada
     */
    fun getSuitableForList(): List<String> {
        return if (suitableFor.isBlank()) emptyList()
        else suitableFor.split(",").map { it.trim() }
    }

    /**
     * Obtiene la lista de ingredientes
     */
    fun getIngredientsList(): List<String> {
        return if (ingredients.isBlank()) emptyList()
        else ingredients.split(";").map { it.trim() }
    }

    /**
     * Obtiene la lista de pasos de preparación
     */
    fun getInstructionsList(): List<String> {
        return if (instructions.isBlank()) emptyList()
        else instructions.split(";").map { it.trim() }
    }

    /**
     * Obtiene la lista de alérgenos
     */
    fun getAllergensList(): List<String> {
        return if (allergens.isBlank()) emptyList()
        else allergens.split(",").map { it.trim() }
    }

    /**
     * Verifica si la receta es compatible con las condiciones médicas del usuario
     */
    fun isCompatibleWith(userConditions: List<String>): Boolean {
        val suitableConditions = getSuitableForList()
        return userConditions.any { it in suitableConditions }
    }

    /**
     * Verifica si la receta contiene algún alérgeno del usuario
     */
    fun hasAllergens(userAllergies: List<String>): Boolean {
        val recipeAllergens = getAllergensList()
        return userAllergies.any { it in recipeAllergens }
    }

    /**
     * Calcula el costo por porción
     */
    fun getCostPerServing(): Double {
        return estimatedCost / servings
    }
}