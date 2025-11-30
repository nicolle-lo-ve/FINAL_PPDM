package com.lasalle.mercadosaludable.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String = "", // ID de Firebase Auth

    // Datos personales
    val name: String = "",
    val email: String = "",
    val age: Int = 0,
    val gender: String = "", // "Masculino", "Femenino", "Otro"

    // Datos de salud
    val weight: Double = 0.0,
    val height: Double = 0.0,
    val bmi: Double = 0.0,

    // Condiciones médicas (separadas por comas)
    val medicalConditions: String = "", // "diabetes,hipertension,obesidad"

    // Alergias alimentarias (separadas por comas)
    val allergies: String = "", // "lacteos,gluten,mariscos"

    // Objetivos nutricionales
    val nutritionalGoal: String = "", // "perder_peso", "mantener", "ganar_musculo"
    val monthlyBudget: Double = 0.0, // Presupuesto mensual en soles

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("", "", "", 0, "", 0.0, 0.0, 0.0, "", "", "", 0.0, System.currentTimeMillis(), System.currentTimeMillis())

    /**
     * Calcula el IMC basado en peso y altura
     */
    companion object {
        fun calculateBMI(weight: Double, height: Double): Double {
            val heightInMeters = height / 100.0
            return weight / (heightInMeters * heightInMeters)
        }

        /**
         * Clasifica el IMC en categorías
         */
        fun classifyBMI(bmi: Double): String {
            return when {
                bmi < 18.5 -> "Bajo peso"
                bmi < 25.0 -> "Peso normal"
                bmi < 30.0 -> "Sobrepeso"
                else -> "Obesidad"
            }
        }
    }

    /**
     * Obtiene la lista de condiciones médicas como lista
     */
    fun getMedicalConditionsList(): List<String> {
        return if (medicalConditions.isBlank()) emptyList()
        else medicalConditions.split(",").map { it.trim() }
    }

    /**
     * Obtiene la lista de alergias como lista
     */
    fun getAllergiesList(): List<String> {
        return if (allergies.isBlank()) emptyList()
        else allergies.split(",").map { it.trim() }
    }
}