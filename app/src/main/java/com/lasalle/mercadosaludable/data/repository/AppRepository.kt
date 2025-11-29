package com.lasalle.mercadosaludable.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lasalle.mercadosaludable.data.local.AppDatabase
import com.lasalle.mercadosaludable.data.model.User
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.data.model.MenuPlan
import kotlinx.coroutines.tasks.await

/**
 * Repositorio que maneja todas las operaciones de datos.
 * Implementa sincronización bidireccional entre Room (local) y Firebase (remoto).
 *
 * ESTRATEGIA DE SINCRONIZACIÓN:
 * - Recetas: Se guardan en Firestore y se cachean en Room
 * - Al iniciar app: Se cargan recetas desde Firestore
 * - Menús: Se generan usando recetas de Room (más rápido)
 */
class AppRepository(private val database: AppDatabase) {

    // DAOs locales
    private val userDao = database.userDao()
    private val recipeDao = database.recipeDao()
    private val menuPlanDao = database.menuPlanDao()

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Constantes
    companion object {
        private const val TAG = "AppRepository"
        private const val USERS_COLLECTION = "users"
        private const val RECIPES_COLLECTION = "recipes"
        private const val MENU_PLANS_COLLECTION = "menu_plans"
    }

    // ==================== USER OPERATIONS ====================

    /**
     * Registra un nuevo usuario en Firebase Auth y guarda su perfil
     */
    suspend fun registerUser(email: String, password: String, user: User): Result<String> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("Error al crear usuario")

            // Actualizar el user con el ID de Firebase
            val updatedUser = user.copy(id = userId)

            // Guardar localmente
            userDao.insertUser(updatedUser)

            // Sincronizar con Firebase
            syncUserToFirebase(updatedUser)

            Result.success(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user", e)
            Result.failure(e)
        }
    }

    /**
     * Inicia sesión con email y password
     */
    suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("Error al iniciar sesión")

            // Sincronizar datos desde Firebase
            syncUserFromFirebase(userId)

            // Cargar recetas desde Firebase al hacer login
            syncRecipesFromFirebase()

            Result.success(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene el usuario actual
     */
    suspend fun getCurrentUser(): User? {
        return userDao.getCurrentUser()
    }

    /**
     * Obtiene el usuario por ID como LiveData
     */
    fun getUserLiveData(userId: String): LiveData<User?> {
        return userDao.getUserByIdLiveData(userId)
    }

    /**
     * Actualiza el perfil del usuario
     */
    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
        syncUserToFirebase(user)
    }

    /**
     * Sincroniza el usuario local con Firebase
     */
    private suspend fun syncUserToFirebase(user: User) {
        try {
            firestore.collection(USERS_COLLECTION)
                .document(user.id)
                .set(user)
                .await()
            Log.d(TAG, "User synced to Firebase: ${user.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user to Firebase", e)
        }
    }

    /**
     * Descarga el usuario desde Firebase y lo guarda localmente
     */
    private suspend fun syncUserFromFirebase(userId: String) {
        try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            document.toObject(User::class.java)?.let { user ->
                userDao.insertUser(user)
                Log.d(TAG, "User synced from Firebase: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user from Firebase", e)
        }
    }

    // ==================== RECIPE OPERATIONS (CON FIREBASE) ====================

    /**
     * Obtiene todas las recetas como LiveData (desde Room)
     */
    fun getAllRecipes(): LiveData<List<Recipe>> {
        return recipeDao.getAllRecipes()
    }

    /**
     * Obtiene una receta por ID
     */
    suspend fun getRecipeById(recipeId: Long): Recipe? {
        return recipeDao.getRecipeById(recipeId)
    }

    /**
     * Obtiene múltiples recetas por sus IDs
     */
    suspend fun getRecipesByIds(recipeIds: List<Long>): List<Recipe> {
        return recipeDao.getRecipesByIds(recipeIds)
    }

    /**
     * Busca recetas por categoría
     */
    fun getRecipesByCategory(category: String): LiveData<List<Recipe>> {
        return recipeDao.getRecipesByCategory(category)
    }

    /**
     * Busca recetas apropiadas para una condición médica
     */
    fun getRecipesByCondition(condition: String): LiveData<List<Recipe>> {
        return recipeDao.getRecipesByCondition(condition)
    }

    /**
     * Busca recetas por texto
     */
    fun searchRecipes(query: String): LiveData<List<Recipe>> {
        return recipeDao.searchRecipes(query)
    }

    /**
     * Obtiene las recetas más populares
     */
    fun getPopularRecipes(limit: Int = 10): LiveData<List<Recipe>> {
        return recipeDao.getPopularRecipes(limit)
    }

    /**
     * NUEVO: Guarda una receta en Firebase y Room
     */
    suspend fun saveRecipeToFirebase(recipe: Recipe): Result<String> {
        return try {
            // 1. Guardar en Room primero (para obtener ID)
            val recipeId = recipeDao.insertRecipe(recipe)
            val recipeWithId = recipe.copy(id = recipeId)

            // 2. Guardar en Firebase con el ID de Room
            val recipeMap = recipeToMap(recipeWithId)
            firestore.collection(RECIPES_COLLECTION)
                .document(recipeId.toString())
                .set(recipeMap)
                .await()

            Log.d(TAG, "Recipe saved to Firebase: $recipeId")
            Result.success(recipeId.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recipe to Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * NUEVO: Sincroniza recetas desde Firebase a Room
     * Se ejecuta automáticamente al hacer login
     */
    suspend fun syncRecipesFromFirebase(): Result<Int> {
        return try {
            Log.d(TAG, "Syncing recipes from Firebase...")

            val snapshot = firestore.collection(RECIPES_COLLECTION)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No recipes in Firebase, inserting sample recipes")
                // Si no hay recetas en Firebase, insertar las de ejemplo
                insertSampleRecipes()
                return Result.success(0)
            }

            val recipes = mutableListOf<Recipe>()
            for (document in snapshot.documents) {
                try {
                    val recipe = mapToRecipe(document.data ?: continue)
                    recipes.add(recipe)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing recipe: ${document.id}", e)
                }
            }

            if (recipes.isNotEmpty()) {
                recipeDao.insertAllRecipes(recipes)
                Log.d(TAG, "Synced ${recipes.size} recipes from Firebase")
            }

            Result.success(recipes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing recipes from Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Inserta recetas de ejemplo en Room Y Firebase
     */
    suspend fun insertSampleRecipes() {
        try {
            val sampleRecipes = getSampleRecipes()

            // Guardar localmente
            recipeDao.insertAllRecipes(sampleRecipes)

            // Guardar en Firebase (batch write para eficiencia)
            val batch = firestore.batch()
            sampleRecipes.forEach { recipe ->
                val recipeMap = recipeToMap(recipe)
                val docRef = firestore.collection(RECIPES_COLLECTION).document(recipe.id.toString())
                batch.set(docRef, recipeMap)
            }
            batch.commit().await()

            Log.d(TAG, "Sample recipes inserted in Room and Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting sample recipes", e)
        }
    }

    /**
     * Convierte una receta a Map para Firebase
     */
    private fun recipeToMap(recipe: Recipe): Map<String, Any> {
        return hashMapOf(
            "id" to recipe.id,
            "name" to recipe.name,
            "description" to recipe.description,
            "category" to recipe.category,
            "imageUrl" to recipe.imageUrl,
            "calories" to recipe.calories,
            "protein" to recipe.protein,
            "carbohydrates" to recipe.carbohydrates,
            "fats" to recipe.fats,
            "fiber" to recipe.fiber,
            "sodium" to recipe.sodium,
            "sugar" to recipe.sugar,
            "suitableFor" to recipe.suitableFor,
            "ingredients" to recipe.ingredients,
            "instructions" to recipe.instructions,
            "preparationTime" to recipe.preparationTime,
            "difficulty" to recipe.difficulty,
            "servings" to recipe.servings,
            "estimatedCost" to recipe.estimatedCost,
            "allergens" to recipe.allergens,
            "rating" to recipe.rating,
            "timesUsed" to recipe.timesUsed,
            "createdAt" to recipe.createdAt
        )
    }

    /**
     * Convierte un Map de Firebase a Recipe
     */
    private fun mapToRecipe(map: Map<String, Any>): Recipe {
        return Recipe(
            id = (map["id"] as? Number)?.toLong() ?: 0L,
            name = map["name"] as? String ?: "",
            description = map["description"] as? String ?: "",
            category = map["category"] as? String ?: "",
            imageUrl = map["imageUrl"] as? String ?: "",
            calories = (map["calories"] as? Number)?.toInt() ?: 0,
            protein = (map["protein"] as? Number)?.toDouble() ?: 0.0,
            carbohydrates = (map["carbohydrates"] as? Number)?.toDouble() ?: 0.0,
            fats = (map["fats"] as? Number)?.toDouble() ?: 0.0,
            fiber = (map["fiber"] as? Number)?.toDouble() ?: 0.0,
            sodium = (map["sodium"] as? Number)?.toDouble() ?: 0.0,
            sugar = (map["sugar"] as? Number)?.toDouble() ?: 0.0,
            suitableFor = map["suitableFor"] as? String ?: "",
            ingredients = map["ingredients"] as? String ?: "",
            instructions = map["instructions"] as? String ?: "",
            preparationTime = (map["preparationTime"] as? Number)?.toInt() ?: 0,
            difficulty = map["difficulty"] as? String ?: "",
            servings = (map["servings"] as? Number)?.toInt() ?: 1,
            estimatedCost = (map["estimatedCost"] as? Number)?.toDouble() ?: 0.0,
            allergens = map["allergens"] as? String ?: "",
            rating = (map["rating"] as? Number)?.toDouble() ?: 0.0,
            timesUsed = (map["timesUsed"] as? Number)?.toInt() ?: 0,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    // ==================== MENU PLAN OPERATIONS ====================

    /**
     * Crea un nuevo plan de menú y lo sincroniza con Firebase
     */
    suspend fun createMenuPlan(menuPlan: MenuPlan): Long {
        try {
            // Desactivar planes anteriores
            menuPlanDao.deactivateAllMenuPlans(menuPlan.userId)

            // Insertar nuevo plan en Room
            val menuPlanId = menuPlanDao.insertMenuPlan(menuPlan)
            val menuPlanWithId = menuPlan.copy(id = menuPlanId)

            // Incrementar contador de uso de recetas
            menuPlan.getAllRecipeIds().forEach { recipeId ->
                recipeDao.incrementTimesUsed(recipeId)
            }

            // Sincronizar con Firebase
            syncMenuPlanToFirebase(menuPlanWithId)

            return menuPlanId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating menu plan", e)
            throw e
        }
    }

    /**
     * Obtiene el plan de menú activo del usuario
     */
    suspend fun getActiveMenuPlan(userId: String): MenuPlan? {
        return menuPlanDao.getActiveMenuPlan(userId)
    }

    /**
     * Obtiene el plan de menú activo como LiveData
     */
    fun getActiveMenuPlanLiveData(userId: String): LiveData<MenuPlan?> {
        return menuPlanDao.getActiveMenuPlanLiveData(userId)
    }

    /**
     * Obtiene todos los planes de menú del usuario
     */
    fun getMenuPlansByUser(userId: String): LiveData<List<MenuPlan>> {
        return menuPlanDao.getMenuPlansByUser(userId)
    }

    /**
     * Obtiene los planes favoritos del usuario
     */
    fun getFavoriteMenuPlans(userId: String): LiveData<List<MenuPlan>> {
        return menuPlanDao.getFavoriteMenuPlans(userId)
    }

    /**
     * Actualiza un plan de menú
     */
    suspend fun updateMenuPlan(menuPlan: MenuPlan) {
        menuPlanDao.updateMenuPlan(menuPlan)
        syncMenuPlanToFirebase(menuPlan)
    }

    /**
     * Elimina un plan de menú
     */
    suspend fun deleteMenuPlan(menuPlan: MenuPlan) {
        menuPlanDao.deleteMenuPlan(menuPlan)
        deleteMenuPlanFromFirebase(menuPlan.id)
    }

    /**
     * Sincroniza plan de menú con Firebase
     */
    private suspend fun syncMenuPlanToFirebase(menuPlan: MenuPlan) {
        try {
            val menuPlanMap = menuPlanToMap(menuPlan)
            firestore.collection(MENU_PLANS_COLLECTION)
                .document("${menuPlan.userId}_${menuPlan.id}")
                .set(menuPlanMap)
                .await()
            Log.d(TAG, "Menu plan synced to Firebase: ${menuPlan.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing menu plan to Firebase", e)
        }
    }

    /**
     * Elimina plan de menú de Firebase
     */
    private suspend fun deleteMenuPlanFromFirebase(menuPlanId: Long) {
        try {
            val query = firestore.collection(MENU_PLANS_COLLECTION)
                .whereEqualTo("id", menuPlanId)
                .get()
                .await()

            for (document in query.documents) {
                document.reference.delete().await()
            }
            Log.d(TAG, "Menu plan deleted from Firebase: $menuPlanId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting menu plan from Firebase", e)
        }
    }

    /**
     * Convierte MenuPlan a Map para Firebase
     */
    private fun menuPlanToMap(menuPlan: MenuPlan): Map<String, Any> {
        return hashMapOf(
            "id" to menuPlan.id,
            "userId" to menuPlan.userId,
            "name" to menuPlan.name,
            "startDate" to menuPlan.startDate,
            "endDate" to menuPlan.endDate,
            "monday" to menuPlan.monday,
            "tuesday" to menuPlan.tuesday,
            "wednesday" to menuPlan.wednesday,
            "thursday" to menuPlan.thursday,
            "friday" to menuPlan.friday,
            "saturday" to menuPlan.saturday,
            "sunday" to menuPlan.sunday,
            "totalCalories" to menuPlan.totalCalories,
            "totalCost" to menuPlan.totalCost,
            "averageDailyCalories" to menuPlan.averageDailyCalories,
            "averageDailyCost" to menuPlan.averageDailyCost,
            "isActive" to menuPlan.isActive,
            "isFavorite" to menuPlan.isFavorite,
            "createdAt" to menuPlan.createdAt
        )
    }

    // ==================== HELPER METHODS ====================

    /**
     * Genera recetas de ejemplo para la base de datos
     */
    private fun getSampleRecipes(): List<Recipe> {
        return listOf(
            Recipe(
                id = 1,
                name = "Ensalada de Quinoa con Verduras",
                description = "Ensalada nutritiva y balanceada con quinoa, vegetales frescos y aderezo ligero",
                category = "Almuerzo",
                imageUrl = "recipe_quinoa_salad",
                calories = 350,
                protein = 12.0,
                carbohydrates = 45.0,
                fats = 10.0,
                fiber = 8.0,
                sodium = 200.0,
                sugar = 3.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 taza de quinoa cocida;2 tomates picados;1 pepino en cubos;1/2 cebolla morada;Jugo de 1 limón;2 cdas de aceite de oliva;Sal y pimienta al gusto",
                instructions = "Cocinar la quinoa según instrucciones del paquete;Dejar enfriar la quinoa;Picar todas las verduras en cubos pequeños;Mezclar la quinoa con las verduras;Agregar aceite de oliva y jugo de limón;Sazonar con sal y pimienta;Refrigerar por 30 minutos antes de servir",
                preparationTime = 25,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 8.50,
                allergens = "",
                rating = 4.5
            ),
            Recipe(
                id = 2,
                name = "Pollo a la Plancha con Brócoli",
                description = "Pechuga de pollo magra acompañada de brócoli al vapor, ideal para diabéticos",
                category = "Almuerzo",
                imageUrl = "recipe_chicken_broccoli",
                calories = 280,
                protein = 35.0,
                carbohydrates = 12.0,
                fats = 8.0,
                fiber = 4.0,
                sodium = 150.0,
                sugar = 2.0,
                suitableFor = "diabetes,obesidad",
                ingredients = "200g de pechuga de pollo;2 tazas de brócoli;1 cda de aceite de oliva;Ajo en polvo;Limón;Sal baja en sodio",
                instructions = "Sazonar el pollo con ajo, sal y limón;Calentar una plancha o sartén;Cocinar el pollo 6-7 minutos por lado;Cocer el brócoli al vapor por 5 minutos;Servir el pollo con el brócoli;Agregar un chorrito de limón",
                preparationTime = 20,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 9.00,
                allergens = "",
                rating = 4.7
            ),
            Recipe(
                id = 3,
                name = "Avena con Frutas y Canela",
                description = "Desayuno energético con avena integral, frutas frescas y canela",
                category = "Desayuno",
                imageUrl = "recipe_oatmeal_fruits",
                calories = 320,
                protein = 10.0,
                carbohydrates = 55.0,
                fats = 7.0,
                fiber = 9.0,
                sodium = 50.0,
                sugar = 12.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1/2 taza de avena integral;1 taza de leche descremada;1 manzana verde picada;1/2 plátano en rodajas;Canela en polvo;1 cda de semillas de chía",
                instructions = "Cocinar la avena con la leche a fuego medio;Revolver constantemente por 5 minutos;Agregar la canela mientras cocina;Servir en un bowl;Decorar con las frutas picadas;Espolvorear semillas de chía por encima",
                preparationTime = 10,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 4.50,
                allergens = "lacteos",
                rating = 4.6
            ),
            Recipe(
                id = 4,
                name = "Sopa de Verduras Casera",
                description = "Sopa nutritiva y baja en sodio con vegetales frescos de estación",
                category = "Cena",
                imageUrl = "recipe_vegetable_soup",
                calories = 150,
                protein = 5.0,
                carbohydrates = 25.0,
                fats = 3.0,
                fiber = 6.0,
                sodium = 180.0,
                sugar = 8.0,
                suitableFor = "hipertension,obesidad,diabetes",
                ingredients = "2 zanahorias;1 zapallo;2 papas pequeñas;1 poro;1 rama de apio;4 tazas de agua;Hierbas aromáticas;Sal baja en sodio",
                instructions = "Lavar y pelar todas las verduras;Cortar las verduras en cubos medianos;Poner el agua a hervir;Agregar todas las verduras;Cocinar por 25 minutos;Sazonar con hierbas y sal baja en sodio;Licuar parcialmente si se desea textura cremosa",
                preparationTime = 35,
                difficulty = "Fácil",
                servings = 4,
                estimatedCost = 6.00,
                allergens = "",
                rating = 4.4
            ),
            Recipe(
                id = 5,
                name = "Pescado al Horno con Vegetales",
                description = "Filete de pescado blanco horneado con vegetales mediterráneos",
                category = "Cena",
                imageUrl = "recipe_baked_fish",
                calories = 300,
                protein = 32.0,
                carbohydrates = 15.0,
                fats = 12.0,
                fiber = 4.0,
                sodium = 200.0,
                sugar = 4.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "200g de filete de pescado blanco;1 pimiento rojo;1 calabacín;1 tomate;2 cdas de aceite de oliva;Limón;Hierbas aromáticas",
                instructions = "Precalentar el horno a 180°C;Cortar los vegetales en rodajas;Colocar los vegetales en una bandeja;Poner el pescado sobre los vegetales;Rociar con aceite de oliva y limón;Espolvorear hierbas aromáticas;Hornear por 20 minutos",
                preparationTime = 30,
                difficulty = "Media",
                servings = 1,
                estimatedCost = 12.00,
                allergens = "pescado",
                rating = 4.8
            ),
            Recipe(
                id = 6,
                name = "Batido Verde Energético",
                description = "Batido saludable con espinaca, frutas y semillas para un desayuno rápido",
                category = "Desayuno",
                imageUrl = "recipe_green_smoothie",
                calories = 200,
                protein = 8.0,
                carbohydrates = 35.0,
                fats = 4.0,
                fiber = 7.0,
                sodium = 40.0,
                sugar = 18.0,
                suitableFor = "obesidad,hipertension",
                ingredients = "1 taza de espinacas frescas;1 plátano maduro;1/2 manzana verde;1 taza de agua;1 cda de semillas de linaza;Hielo al gusto",
                instructions = "Lavar bien las espinacas;Pelar el plátano y cortar la manzana;Colocar todos los ingredientes en la licuadora;Agregar el agua y el hielo;Licuar hasta obtener consistencia suave;Servir inmediatamente",
                preparationTime = 5,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 3.50,
                allergens = "",
                rating = 4.3
            ),
            Recipe(
                id = 7,
                name = "Ensalada César Saludable",
                description = "Versión saludable de la ensalada césar con aderezo de yogurt",
                category = "Almuerzo",
                imageUrl = "recipe_caesar_salad",
                calories = 280,
                protein = 20.0,
                carbohydrates = 18.0,
                fats = 14.0,
                fiber = 5.0,
                sodium = 250.0,
                sugar = 3.0,
                suitableFor = "diabetes,obesidad",
                ingredients = "3 tazas de lechuga romana;100g de pechuga de pollo a la plancha;2 cdas de yogurt griego;1 cda de jugo de limón;1 cdta de mostaza Dijon;Queso parmesano rallado;Crotones integrales",
                instructions = "Cocinar el pollo y cortarlo en tiras;Lavar y cortar la lechuga;Mezclar yogurt, limón y mostaza para el aderezo;Colocar la lechuga en un bowl;Agregar el pollo en tiras;Verter el aderezo;Espolvorear queso parmesano y crotones",
                preparationTime = 15,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 10.00,
                allergens = "lacteos,gluten",
                rating = 4.5
            ),
            Recipe(
                id = 8,
                name = "Tortilla de Claras con Champiñones",
                description = "Tortilla proteica de claras de huevo con champiñones y espinacas",
                category = "Desayuno",
                imageUrl = "recipe_egg_white_omelette",
                calories = 180,
                protein = 18.0,
                carbohydrates = 8.0,
                fats = 6.0,
                fiber = 2.0,
                sodium = 220.0,
                sugar = 2.0,
                suitableFor = "diabetes,obesidad,hipertension",
                ingredients = "4 claras de huevo;1/2 taza de champiñones laminados;1 taza de espinacas frescas;1 cdta de aceite de oliva;Cebolla picada;Sal y pimienta",
                instructions = "Batir las claras con sal y pimienta;Saltear champiñones y cebolla en aceite;Agregar las espinacas hasta que se marchiten;Verter las claras batidas sobre los vegetales;Cocinar a fuego medio por 3 minutos;Doblar la tortilla y servir",
                preparationTime = 12,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 5.00,
                allergens = "huevo",
                rating = 4.4
            )
        )
    }
}