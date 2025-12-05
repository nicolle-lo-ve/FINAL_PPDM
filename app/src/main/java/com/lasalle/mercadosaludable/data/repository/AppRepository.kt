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
            Log.d(TAG, ">>> registerUser() iniciado")
            Log.d(TAG, "Email: $email")
            Log.d(TAG, "Usuario: ${user.name}")

            // Verificar que Firebase Auth esté inicializado
            if (auth == null) {
                Log.e(TAG, "❌ Firebase Auth es NULL!")
                return Result.failure(Exception("Firebase Auth no inicializado"))
            }

            Log.d(TAG, "Firebase Auth OK, creando usuario...")

            // Crear usuario en Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            Log.d(TAG, "createUserWithEmailAndPassword completado")

            val userId = authResult.user?.uid
            if (userId == null) {
                Log.e(TAG, "❌ userId es NULL después de crear usuario")
                return Result.failure(Exception("Error al obtener ID de usuario"))
            }

            Log.d(TAG, "✅ Usuario creado en Firebase Auth con ID: $userId")

            // Actualizar el user con el ID de Firebase
            val updatedUser = user.copy(id = userId)
            Log.d(TAG, "User actualizado con ID: ${updatedUser.id}")

            // Guardar localmente en Room
            Log.d(TAG, "Guardando usuario en Room...")
            userDao.insertUser(updatedUser)
            Log.d(TAG, "✅ Usuario guardado en Room")

            // Sincronizar con Firestore
            Log.d(TAG, "Sincronizando con Firestore...")
            syncUserToFirebase(updatedUser)
            Log.d(TAG, "✅ Usuario sincronizado con Firestore")

            Log.d(TAG, "<<< registerUser() completado exitosamente")
            Result.success(userId)
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Error en registerUser", e)
            Log.e(TAG, "Tipo de error: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensaje: ${e.message}")
            Log.e(TAG, "Stack trace:")
            e.printStackTrace()
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
            Log.d(TAG, ">>> syncUserToFirebase iniciado para user: ${user.id}")

            if (firestore == null) {
                Log.e(TAG, "❌ Firestore es NULL!")
                return
            }

            Log.d(TAG, "Guardando en colección: $USERS_COLLECTION")
            Log.d(TAG, "Documento ID: ${user.id}")

            firestore.collection(USERS_COLLECTION)
                .document(user.id)
                .set(user)
                .await()

            Log.d(TAG, "✅ Usuario sincronizado con Firestore: ${user.id}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando usuario con Firestore", e)
            Log.e(TAG, "Mensaje: ${e.message}")
            e.printStackTrace()
            // No lanzamos la excepción aquí porque el registro local ya funcionó
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
            ),

            Recipe(
                id = 9,
                name = "Gachas de Avena con Canela y Manzana",
                description = "Desayuno caliente y nutritivo con avena sin gluten, manzana y canela",
                category = "Desayuno",
                imageUrl = "recipe_oatmeal_apple",
                calories = 280,
                protein = 8.0,
                carbohydrates = 45.0,
                fats = 6.0,
                fiber = 9.0,
                sodium = 50.0,
                sugar = 8.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1/2 taza de avena sin gluten;1 taza de agua o bebida de almendra sin azúcar;1/2 manzana picada;1 cdta de canela en polvo;1 cda de nueces picadas (opcional)",
                instructions = "Hervir el agua o bebida vegetal;Agregar la avena y cocinar 5 minutos;Añadir la manzana picada y la canela;Cocinar 5 minutos más hasta espesar;Servir con nueces picadas",
                preparationTime = 15,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 3.50,
                allergens = "",
                rating = 4.5
            ),

            Recipe(
                id = 10,
                name = "Pudín de Chía con Frutos Rojos",
                description = "Desayuno frío tipo pudding con semillas de chía y frutos rojos",
                category = "Desayuno",
                imageUrl = "recipe_chia_pudding",
                calories = 220,
                protein = 7.0,
                carbohydrates = 25.0,
                fats = 10.0,
                fiber = 12.0,
                sodium = 30.0,
                sugar = 5.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1/4 taza de semillas de chía;1 taza de bebida de almendra sin azúcar;1/2 taza de frutos rojos frescos;Esencia de vainilla (opcional);Stevia al gusto",
                instructions = "Mezclar chía con bebida vegetal y vainilla;Refrigerar mínimo 4 horas (ideal toda la noche);Remover bien antes de servir;Agregar frutos rojos por encima",
                preparationTime = 5,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 4.00,
                allergens = "",
                rating = 4.7
            ),

            Recipe(
                id = 11,
                name = "Tostadas de Camote con Aguacate",
                description = "Tostadas saludables de camote con aguacate y vegetales frescos",
                category = "Desayuno",
                imageUrl = "recipe_sweet_potato_toast",
                calories = 320,
                protein = 5.0,
                carbohydrates = 40.0,
                fats = 15.0,
                fiber = 10.0,
                sodium = 80.0,
                sugar = 12.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 camote mediano;1/2 aguacate;Jugo de 1 limón;1 tomate picado;Cilantro fresco;1/4 cebolla morada en juliana",
                instructions = "Cortar el camote en rodajas de 1 cm;Tostar en horno a 200°C por 15 minutos;Aplastar aguacate con jugo de limón;Untar sobre el camote tostado;Decorar con tomate, cebolla y cilantro",
                preparationTime = 20,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 5.00,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 12,
                name = "Smoothie Verde Energético",
                description = "Batido verde revitalizante con espinacas, pepino y semillas",
                category = "Desayuno",
                imageUrl = "recipe_green_smoothie_veggie",
                calories = 180,
                protein = 6.0,
                carbohydrates = 25.0,
                fats = 5.0,
                fiber = 8.0,
                sodium = 40.0,
                sugar = 10.0,
                suitableFor = "obesidad,hipertension",
                ingredients = "1 taza de espinacas frescas;1/2 pepino;1/2 plátano congelado;1 taza de bebida vegetal sin azúcar;1 cda de semillas de linaza molidas",
                instructions = "Lavar las espinacas y pepino;Cortar el pepino en trozos;Colocar todos los ingredientes en la licuadora;Licuar hasta obtener textura suave;Servir inmediatamente",
                preparationTime = 5,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 3.50,
                allergens = "",
                rating = 4.4
            ),

            Recipe(
                id = 13,
                name = "Revuelto de Tofu a la Mexicana",
                description = "Alternativa vegana al huevo revuelto con tofu y vegetales",
                category = "Desayuno",
                imageUrl = "recipe_tofu_scramble",
                calories = 250,
                protein = 20.0,
                carbohydrates = 15.0,
                fats = 12.0,
                fiber = 5.0,
                sodium = 150.0,
                sugar = 4.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "200g de tofu firme;1/2 pimiento verde;1/4 cebolla;1 tomate;1 cdta de cúrcuma;Comino en polvo;1 cda de aceite de oliva",
                instructions = "Escurrir y desmenuzar el tofu;Picar todas las verduras;Saltear verduras en aceite de oliva;Añadir tofu desmenuzado y cúrcuma;Cocinar 5-7 minutos;Sazonar con comino al gusto",
                preparationTime = 15,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 6.00,
                allergens = "",
                rating = 4.5
            ),

            Recipe(
                id = 14,
                name = "Arepa de Maíz con Pollo Desmenuzado",
                description = "Arepa de maíz rellena de pollo desmenuzado y aguacate",
                category = "Desayuno",
                imageUrl = "recipe_arepa_chicken",
                calories = 380,
                protein = 30.0,
                carbohydrates = 35.0,
                fats = 14.0,
                fiber = 6.0,
                sodium = 120.0,
                sugar = 2.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1/2 taza harina de maíz precocida;100g pechuga de pollo cocida;1/4 aguacate;Hojas de lechuga;Agua para masa;Sal baja en sodio",
                instructions = "Formar masa con harina, agua y sal;Dar forma a la arepa;Cocinar en sartén hasta dorar ambos lados;Desmenuzar el pollo;Abrir arepa y rellenar con pollo, aguacate y lechuga",
                preparationTime = 25,
                difficulty = "Media",
                servings = 1,
                estimatedCost = 7.00,
                allergens = "",
                rating = 4.8
            ),

            Recipe(
                id = 15,
                name = "Bowl de Quinoa con Fruta",
                description = "Bowl nutritivo de quinoa con frutas frescas y frutos secos",
                category = "Desayuno",
                imageUrl = "recipe_quinoa_fruit_bowl",
                calories = 350,
                protein = 12.0,
                carbohydrates = 55.0,
                fats = 10.0,
                fiber = 9.0,
                sodium = 60.0,
                sugar = 15.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1/2 taza de quinoa cocida;1/2 taza de frutas mixtas (fresa, durazno);1 cda de almendras fileteadas;Canela en polvo;Bebida vegetal sin azúcar",
                instructions = "Cocinar quinoa según instrucciones;Enfriar la quinoa cocida;Picar las frutas en trozos pequeños;Mezclar quinoa con frutas;Agregar almendras y canela;Servir con bebida vegetal",
                preparationTime = 20,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 5.50,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 16,
                name = "Crepes de Garbanzo",
                description = "Crepes sin gluten de harina de garbanzo con mermelada sin azúcar",
                category = "Desayuno",
                imageUrl = "recipe_chickpea_crepes",
                calories = 290,
                protein = 15.0,
                carbohydrates = 40.0,
                fats = 8.0,
                fiber = 10.0,
                sodium = 100.0,
                sugar = 3.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1/2 taza harina de garbanzo;3/4 taza agua;Pizca de sal;Aceite de oliva para engrasar;2 cdas mermelada sin azúcar",
                instructions = "Mezclar harina con agua hasta crema fina;Calentar sartén antiadherente;Verter un poco de mezcla y esparcir;Cocinar 2 minutos por lado;Repetir con resto de masa;Rellenar con mermelada sin azúcar",
                preparationTime = 15,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 4.50,
                allergens = "",
                rating = 4.3
            ),

            Recipe(
                id = 17,
                name = "Salmón al Horno con Espárragos",
                description = "Filete de salmón horneado con espárragos y limón",
                category = "Cena",
                imageUrl = "recipe_baked_salmon",
                calories = 320,
                protein = 35.0,
                carbohydrates = 8.0,
                fats = 16.0,
                fiber = 3.0,
                sodium = 180.0,
                sugar = 2.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "150g filete de salmón;1 manojo de espárragos;Jugo de 1 limón;1 cda aceite de oliva;Eneldo fresco;Pimienta negra",
                instructions = "Precalentar horno a 200°C;Colocar salmón y espárragos en bandeja;Rociar con aceite y limón;Espolvorear eneldo y pimienta;Hornear 15-20 minutos;Servir caliente",
                preparationTime = 25,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 14.00,
                allergens = "pescado",
                rating = 4.9
            ),

            Recipe(
                id = 18,
                name = "Ensalada de Lentejas con Verduras Asadas",
                description = "Ensalada proteica con lentejas y verduras asadas al horno",
                category = "Almuerzo",
                imageUrl = "recipe_lentil_salad",
                calories = 380,
                protein = 18.0,
                carbohydrates = 45.0,
                fats = 12.0,
                fiber = 15.0,
                sodium = 160.0,
                sugar = 10.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 taza lentejas cocidas;1 berenjena;1 calabacín;1 pimiento rojo;Vinagre balsámico;Aceite de oliva;Hierbas frescas",
                instructions = "Cortar verduras en cubos;Asar verduras en horno 20 minutos;Mezclar con lentejas cocidas;Preparar vinagreta con aceite y vinagre;Mezclar todo y decorar con hierbas",
                preparationTime = 30,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 8.00,
                allergens = "",
                rating = 4.7
            ),

            Recipe(
                id = 19,
                name = "Pollo al Curry con Leche de Coco",
                description = "Pollo en salsa de curry con leche de coco light y espinacas",
                category = "Almuerzo",
                imageUrl = "recipe_curry_chicken",
                calories = 350,
                protein = 32.0,
                carbohydrates = 12.0,
                fats = 18.0,
                fiber = 4.0,
                sodium = 220.0,
                sugar = 3.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "200g pechuga de pollo;1/2 cebolla;2 dientes de ajo;1 cdta curry en polvo;1/2 taza leche de coco light;2 tazas espinacas;Aceite de oliva",
                instructions = "Cortar pollo en cubos;Saltear cebolla y ajo;Añadir pollo y dorar;Agregar curry en polvo;Incorporar leche de coco;Cocinar 10 minutos;Añadir espinacas al final",
                preparationTime = 30,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 11.00,
                allergens = "",
                rating = 4.8
            ),

            Recipe(
                id = 20,
                name = "Hamburguesas de Lentejas y Quinoa",
                description = "Hamburguesas vegetarianas de lentejas y quinoa sin gluten",
                category = "Almuerzo",
                imageUrl = "recipe_lentil_burgers",
                calories = 320,
                protein = 15.0,
                carbohydrates = 40.0,
                fats = 10.0,
                fiber = 12.0,
                sodium = 140.0,
                sugar = 2.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 taza lentejas cocidas;1/2 taza quinoa cocida;1/2 cebolla picada;1 diente de ajo;2 cdas harina de avena sin gluten;Pimentón ahumado",
                instructions = "Triturar lentejas con tenedor;Mezclar con quinoa y demás ingredientes;Formar hamburguesas;Refrigerar 30 minutos;Hornear a 180°C por 20 minutos;Voltear a mitad de cocción",
                preparationTime = 40,
                difficulty = "Media",
                servings = 4,
                estimatedCost = 9.00,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 21,
                name = "Pescado Blanco al Vapor con Jengibre",
                description = "Pescado blanco cocido al vapor con jengibre y salsa de soja baja en sodio",
                category = "Cena",
                imageUrl = "recipe_steamed_fish",
                calories = 220,
                protein = 28.0,
                carbohydrates = 5.0,
                fats = 8.0,
                fiber = 1.0,
                sodium = 150.0,
                sugar = 2.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "200g filete de merluza;1 rodaja de jengibre fresco;2 tallos de cebollín;1 cda salsa de soja baja en sodio;1 cdta aceite de sésamo",
                instructions = "Colocar pescado en vaporera;Agregar jengibre y cebollín;Cocinar al vapor 10-12 minutos;Calentar salsa de soja con aceite de sésamo;Verter sobre el pescado al servir",
                preparationTime = 20,
                difficulty = "Fácil",
                servings = 1,
                estimatedCost = 10.00,
                allergens = "pescado",
                rating = 4.7
            ),

            Recipe(
                id = 22,
                name = "Espaguetis de Calabacín con Lentejas",
                description = "Espaguetis vegetales con salsa boloñesa de lentejas",
                category = "Almuerzo",
                imageUrl = "recipe_zucchini_pasta",
                calories = 280,
                protein = 16.0,
                carbohydrates = 35.0,
                fats = 10.0,
                fiber = 12.0,
                sodium = 170.0,
                sugar = 8.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 calabacines medianos;1 taza lentejas cocidas;1/2 taza tomate triturado;1 zanahoria;1 tallo de apio;Especias italianas;Aceite de oliva",
                instructions = "Hacer espaguetis de calabacín con espiralizador;Saltear verduras picadas;Añadir tomate y lentejas;Cocinar salsa 15 minutos;Servir salsa sobre calabacín crudo o ligeramente salteado",
                preparationTime = 25,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 8.50,
                allergens = "",
                rating = 4.5
            ),

            Recipe(
                id = 23,
                name = "Guiso de Frijoles Negros con Calabaza",
                description = "Guiso reconfortante de frijoles negros y calabaza butternut",
                category = "Almuerzo",
                imageUrl = "recipe_black_bean_stew",
                calories = 320,
                protein = 14.0,
                carbohydrates = 55.0,
                fats = 5.0,
                fiber = 18.0,
                sodium = 120.0,
                sugar = 10.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 tazas frijoles negros cocidos;2 tazas calabaza en cubos;1 pimiento verde;1 cebolla;2 dientes de ajo;Comino en polvo;Caldo vegetal bajo en sodio",
                instructions = "Saltear cebolla, ajo y pimiento;Agregar calabaza y frijoles;Añadir caldo y comino;Cocinar 25 minutos hasta calabaza tierna;Majar ligeramente para espesar",
                preparationTime = 40,
                difficulty = "Media",
                servings = 4,
                estimatedCost = 7.50,
                allergens = "",
                rating = 4.8
            ),

            Recipe(
                id = 24,
                name = "Tacos de Lechuga con Pavo Molido",
                description = "Tacos saludables con hojas de lechuga rellenas de pavo molido",
                category = "Cena",
                imageUrl = "recipe_lettuce_tacos",
                calories = 290,
                protein = 30.0,
                carbohydrates = 12.0,
                fats = 12.0,
                fiber = 6.0,
                sodium = 140.0,
                sugar = 4.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "200g pavo molido;8 hojas grandes de lechuga;1/2 cebolla;1 taza champiñones;Pimentón en polvo;1/2 aguacate;Limón",
                instructions = "Saltear cebolla y pavo hasta dorar;Añadir champiñones picados;Sazonar con pimentón;Lavar y secar hojas de lechuga;Rellenar con mezcla de pavo;Agregar aguacate y limón",
                preparationTime = 20,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 9.50,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 25,
                name = "Berenjenas Rellenas de Carne y Quinoa",
                description = "Berenjenas horneadas rellenas de carne magra y quinoa",
                category = "Almuerzo",
                imageUrl = "recipe_stuffed_eggplant",
                calories = 380,
                protein = 28.0,
                carbohydrates = 40.0,
                fats = 14.0,
                fiber = 15.0,
                sodium = 180.0,
                sugar = 12.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 berenjenas medianas;200g carne magra molida;1/2 taza quinoa cocida;1 tomate picado;1/2 cebolla;Ajo en polvo;Perejil fresco",
                instructions = "Cortar berenjenas por la mitad y hornear 20 minutos;Retirar parte del centro;Saltear carne con cebolla y tomate;Mezclar con quinoa;Rellenar berenjenas;Hornear 15 minutos más",
                preparationTime = 50,
                difficulty = "Media",
                servings = 4,
                estimatedCost = 12.00,
                allergens = "",
                rating = 4.7
            ),

            Recipe(
                id = 26,
                name = "Estofado de Pollo con Verduras",
                description = "Estofado de pollo con verduras y cúrcuma antiinflamatoria",
                category = "Cena",
                imageUrl = "recipe_chicken_stew",
                calories = 320,
                protein = 35.0,
                carbohydrates = 25.0,
                fats = 10.0,
                fiber = 8.0,
                sodium = 160.0,
                sugar = 6.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "300g muslos de pollo sin piel;2 zanahorias;1 puerro;2 ramas de apio;1 calabacín;1 cdta cúrcuma;Caldo de pollo bajo en sodio",
                instructions = "Dorar el pollo en olla;Añadir verduras duras y cúrcuma;Agregar caldo y cocinar 30 minutos;Incorporar calabacín los últimos 10 minutos;Rectificar sazón",
                preparationTime = 45,
                difficulty = "Media",
                servings = 3,
                estimatedCost = 10.50,
                allergens = "",
                rating = 4.8
            ),

            Recipe(
                id = 27,
                name = "Pimientos Rellenos de Atún y Arroz Integral",
                description = "Pimientos rellenos de atún al natural y arroz integral",
                category = "Almuerzo",
                imageUrl = "recipe_stuffed_peppers",
                calories = 340,
                protein = 25.0,
                carbohydrates = 40.0,
                fats = 8.0,
                fiber = 10.0,
                sodium = 130.0,
                sugar = 8.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 pimientos rojos;1 lata atún al natural escurrido;1/2 taza arroz integral cocido;1 tomate picado;1/4 cebolla;Aceitunas negras picadas (pocas);Hierbas provenzales",
                instructions = "Cortar tapa de pimientos y vaciar;Mezclar atún con arroz y verduras;Rellenar pimientos con la mezcla;Hornear a 180°C por 25 minutos;Servir caliente",
                preparationTime = 45,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 9.00,
                allergens = "pescado",
                rating = 4.5
            ),

            Recipe(
                id = 28,
                name = "Wok de Tofu y Vegetales",
                description = "Salteado rápido de tofu firme con vegetales mixtos",
                category = "Cena",
                imageUrl = "recipe_tofu_stirfry",
                calories = 290,
                protein = 22.0,
                carbohydrates = 25.0,
                fats = 12.0,
                fiber = 8.0,
                sodium = 140.0,
                sugar = 6.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "200g tofu firme;1 pimiento rojo;1 zanahoria;1 taza brócoli;1/2 cebolla morada;Salsa de soja baja en sodio;Jengibre rallado",
                instructions = "Cortar tofu en cubos y dorar;Saltear vegetales en wok;Añadir tofu dorado;Incorporar salsa de soja y jengibre;Cocinar 5 minutos más;Servir inmediatamente",
                preparationTime = 20,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 8.00,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 29,
                name = "Chili Vegetariano Sin Carne",
                description = "Chili picante con frijoles, vegetales y especias",
                category = "Cena",
                imageUrl = "recipe_vegetarian_chili",
                calories = 310,
                protein = 16.0,
                carbohydrates = 50.0,
                fats = 6.0,
                fiber = 20.0,
                sodium = 150.0,
                sugar = 10.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 taza frijoles rojos cocidos;1 taza frijoles negros;1 pimiento verde;1 cebolla;2 dientes de ajo;Chile en polvo al gusto;Tomate triturado sin azúcar",
                instructions = "Saltear cebolla, ajo y pimiento;Agregar tomate y especias;Incorporar frijoles cocidos;Cocinar a fuego lento 25 minutos;Servir caliente",
                preparationTime = 35,
                difficulty = "Fácil",
                servings = 4,
                estimatedCost = 7.00,
                allergens = "",
                rating = 4.7
            ),

            Recipe(
                id = 30,
                name = "Sopa de Calabaza y Zanahoria",
                description = "Crema de calabaza y zanahoria sin lácteos",
                category = "Cena",
                imageUrl = "recipe_pumpkin_soup",
                calories = 180,
                protein = 4.0,
                carbohydrates = 30.0,
                fats = 6.0,
                fiber = 8.0,
                sodium = 100.0,
                sugar = 12.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 tazas calabaza en cubos;2 zanahorias;1/2 cebolla;2 tazas caldo vegetal bajo en sodio;Jengibre rallado;Nuez moscada;Aceite de oliva",
                instructions = "Saltear cebolla y zanahoria;Añadir calabaza y caldo;Cocinar 20 minutos hasta tierno;Licuar hasta cremoso;Sazonar con jengibre y nuez moscada",
                preparationTime = 30,
                difficulty = "Fácil",
                servings = 3,
                estimatedCost = 6.50,
                allergens = "",
                rating = 4.5
            ),

            Recipe(
                id = 31,
                name = "Ensalada de Garbanzos y Vegetales",
                description = "Ensalada fría de garbanzos con vegetales crujientes",
                category = "Almuerzo",
                imageUrl = "recipe_chickpea_salad",
                calories = 330,
                protein = 15.0,
                carbohydrates = 45.0,
                fats = 10.0,
                fiber = 14.0,
                sodium = 110.0,
                sugar = 8.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 taza garbanzos cocidos;1 pepino;1 tomate;1/4 cebolla roja;Perejil fresco;Jugo de limón;Aceite de oliva",
                instructions = "Escurrir y lavar garbanzos;Picar todos los vegetales;Mezclar garbanzos con vegetales;Aliñar con limón y aceite;Refrigerar 30 minutos antes de servir",
                preparationTime = 15,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 6.00,
                allergens = "",
                rating = 4.4
            ),

            Recipe(
                id = 32,
                name = "Albóndigas de Pavo en Salsa de Tomate",
                description = "Albóndigas de pavo magro en salsa de tomate casera",
                category = "Almuerzo",
                imageUrl = "recipe_turkey_meatballs",
                calories = 350,
                protein = 32.0,
                carbohydrates = 20.0,
                fats = 15.0,
                fiber = 6.0,
                sodium = 170.0,
                sugar = 5.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "300g pavo molido;1/2 cebolla picada;2 dientes de ajo;Perejil fresco;1 taza tomate triturado sin azúcar;Hierbas italianas;Harina de avena sin gluten (para ligar)",
                instructions = "Mezclar pavo con cebolla, ajo y perejil;Formar albóndigas;Dorar en sartén antiadherente;Agregar tomate y hierbas;Cocinar 20 minutos a fuego lento",
                preparationTime = 35,
                difficulty = "Media",
                servings = 3,
                estimatedCost = 10.00,
                allergens = "",
                rating = 4.7
            ),

            Recipe(
                id = 33,
                name = "Curry de Garbanzos y Espinacas",
                description = "Curry vegetariano con garbanzos y espinacas frescas",
                category = "Cena",
                imageUrl = "recipe_chickpea_curry",
                calories = 340,
                protein = 16.0,
                carbohydrates = 45.0,
                fats = 12.0,
                fiber = 16.0,
                sodium = 130.0,
                sugar = 6.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 taza garbanzos cocidos;2 tazas espinacas;1/2 cebolla;2 dientes de ajo;Curry en polvo;Leche de coco light;Aceite de oliva",
                instructions = "Saltear cebolla y ajo;Añadir curry en polvo;Incorporar garbanzos y leche de coco;Cocinar 10 minutos;Agregar espinacas al final;Cocinar hasta que se marchiten",
                preparationTime = 25,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 7.50,
                allergens = "",
                rating = 4.8
            ),

            Recipe(
                id = 34,
                name = "Ensalada de Aguacate y Tomate",
                description = "Ensalada simple y refrescante de aguacate y tomate",
                category = "Merienda",
                imageUrl = "recipe_avocado_salad",
                calories = 220,
                protein = 4.0,
                carbohydrates = 12.0,
                fats = 18.0,
                fiber = 8.0,
                sodium = 60.0,
                sugar = 4.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "1 aguacate maduro;2 tomates medianos;1/4 cebolla morada;Jugo de 1 limón;Cilantro fresco;Pimienta negra",
                instructions = "Cortar aguacate y tomate en cubos;Picar cebolla finamente;Mezclar todos los ingredientes;Aliñar con jugo de limón;Sazonar con pimienta y cilantro",
                preparationTime = 10,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 5.00,
                allergens = "",
                rating = 4.5
            ),

            Recipe(
                id = 35,
                name = "Palitos de Zanahoria y Apio con Hummus",
                description = "Verduras crudas con dip de hummus casero",
                category = "Merienda",
                imageUrl = "recipe_veggie_sticks",
                calories = 180,
                protein = 6.0,
                carbohydrates = 20.0,
                fats = 8.0,
                fiber = 8.0,
                sodium = 90.0,
                sugar = 6.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 zanahorias;2 tallos de apio;1 taza garbanzos cocidos;Jugo de limón;1 cda tahini (pasta de sésamo);Comino en polvo;Aceite de oliva",
                instructions = "Lavar y cortar zanahorias y apio en bastones;Licuar garbanzos con tahini y limón;Agregar aceite de oliva gradualmente;Sazonar con comino;Servir verduras con hummus",
                preparationTime = 15,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 5.50,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 36,
                name = "Manzanas Asadas con Canela",
                description = "Postre saludable de manzanas asadas con canela y nueces",
                category = "Postre",
                imageUrl = "recipe_baked_apples",
                calories = 160,
                protein = 2.0,
                carbohydrates = 30.0,
                fats = 4.0,
                fiber = 6.0,
                sodium = 10.0,
                sugar = 20.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 manzanas verdes;1 cdta canela en polvo;2 cdas nueces picadas;Agua para fondo de bandeja;Stevia al gusto",
                instructions = "Cortar manzanas por la mitad y vaciar centro;Colocar en bandeja con agua;Espolvorear con canela y stevia;Hornear a 180°C por 20 minutos;Decorar con nueces antes de servir",
                preparationTime = 25,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 4.00,
                allergens = "",
                rating = 4.4
            ),

            Recipe(
                id = 37,
                name = "Helado de Plátano y Frutos Rojos",
                description = "Helado cremoso sin lácteos a base de plátano congelado",
                category = "Postre",
                imageUrl = "recipe_banana_icecream",
                calories = 150,
                protein = 2.0,
                carbohydrates = 35.0,
                fats = 1.0,
                fiber = 6.0,
                sodium = 5.0,
                sugar = 20.0,
                suitableFor = "obesidad,hipertension",
                ingredients = "2 plátanos maduros congelados;1/2 taza frutos rojos congelados;Esencia de vainilla;Un chorrito de bebida vegetal",
                instructions = "Cortar plátanos en rodajas antes de congelar;Congelar por mínimo 4 horas;Procesar en procesador hasta cremoso;Añadir frutos rojos y vainilla;Servir inmediatamente",
                preparationTime = 10,
                difficulty = "Fácil",
                servings = 2,
                estimatedCost = 3.50,
                allergens = "",
                rating = 4.7
            ),

            Recipe(
                id = 38,
                name = "Gelatina de Frutas Natural",
                description = "Gelatina sin azúcar con frutas frescas",
                category = "Postre",
                imageUrl = "recipe_fruit_jelly",
                calories = 40,
                protein = 2.0,
                carbohydrates = 8.0,
                fats = 0.0,
                fiber = 1.0,
                sodium = 20.0,
                sugar = 5.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 sobres gelatina sin sabor;2 tazas jugo de fruta natural sin azúcar;1 taza frutas picadas (fresa, durazno);Agua",
                instructions = "Hidratar gelatina según instrucciones;Calentar jugo de fruta sin hervir;Disolver gelatina en jugo;Agregar frutas picadas;Refrigerar 4 horas hasta cuajar",
                preparationTime = 15,
                difficulty = "Fácil",
                servings = 4,
                estimatedCost = 4.50,
                allergens = "",
                rating = 4.3
            ),

            Recipe(
                id = 39,
                name = "Peras al Vino Tinto Sin Azúcar",
                description = "Peras cocidas en vino tinto con especias, sin azúcar añadido",
                category = "Postre",
                imageUrl = "recipe_pears_wine",
                calories = 120,
                protein = 1.0,
                carbohydrates = 25.0,
                fats = 0.0,
                fiber = 5.0,
                sodium = 10.0,
                sugar = 18.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "2 peras firmes;1 taza vino tinto;1 rama de canela;2 clavos de olor;Cáscara de naranja;Stevia al gusto",
                instructions = "Pelar peras dejando el rabo;Hervir vino con especias;Agregar peras y cocinar 20 minutos;Dejar enfriar en el líquido;Servir frías o a temperatura ambiente",
                preparationTime = 30,
                difficulty = "Media",
                servings = 2,
                estimatedCost = 6.00,
                allergens = "",
                rating = 4.6
            ),

            Recipe(
                id = 40,
                name = "Compota de Manzana Sin Azúcar",
                description = "Puré de manzana cocido sin azúcar añadido",
                category = "Postre",
                imageUrl = "recipe_apple_compote",
                calories = 80,
                protein = 0.0,
                carbohydrates = 20.0,
                fats = 0.0,
                fiber = 4.0,
                sodium = 5.0,
                sugar = 15.0,
                suitableFor = "diabetes,hipertension,obesidad",
                ingredients = "3 manzanas verdes;1 rama de canela;Jugo de 1/2 limón;Agua;Esencia de vainilla",
                instructions = "Pelar y cortar manzanas en cubos;Cocinar con agua, canela y limón;Cuando estén tiernas, majar con tenedor;Agregar vainilla al final;Servir caliente o frío",
                preparationTime = 20,
                difficulty = "Fácil",
                servings = 3,
                estimatedCost = 3.00,
                allergens = "",
                rating = 4.4
            )
        )
    }
}