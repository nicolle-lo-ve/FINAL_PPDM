package com.lasalle.mercadosaludable.data.repository

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
 * Actúa como capa de abstracción entre los ViewModels y las fuentes de datos
 * (Room local y Firebase remoto).
 */
class AppRepository(private val database: AppDatabase) {

    // DAOs locales
    private val userDao = database.userDao()
    private val recipeDao = database.recipeDao()
    private val menuPlanDao = database.menuPlanDao()

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()



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

            Result.success(userId)
        } catch (e: Exception) {
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
            firestore.collection("users")
                .document(user.id)
                .set(user)
                .await()
        } catch (e: Exception) {
            // Log error pero no fallar la operación local
            e.printStackTrace()
        }
    }

    /**
     * Descarga el usuario desde Firebase y lo guarda localmente
     */
    private suspend fun syncUserFromFirebase(userId: String) {
        try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            document.toObject(User::class.java)?.let { user ->
                userDao.insertUser(user)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== RECIPE OPERATIONS ====================

    /**
     * Obtiene todas las recetas como LiveData
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
     * Inserta recetas de ejemplo en la base de datos
     */
    suspend fun insertSampleRecipes() {
        val sampleRecipes = getSampleRecipes()
        recipeDao.insertAllRecipes(sampleRecipes)
    }

    // ==================== MENU PLAN OPERATIONS ====================

    /**
     * Crea un nuevo plan de menú
     */
    suspend fun createMenuPlan(menuPlan: MenuPlan): Long {
        // Desactivar planes anteriores
        menuPlanDao.deactivateAllMenuPlans(menuPlan.userId)

        // Insertar nuevo plan
        val menuPlanId = menuPlanDao.insertMenuPlan(menuPlan)

        // Incrementar contador de uso de recetas
        menuPlan.getAllRecipeIds().forEach { recipeId ->
            recipeDao.incrementTimesUsed(recipeId)
        }

        return menuPlanId
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
    }

    /**
     * Elimina un plan de menú
     */
    suspend fun deleteMenuPlan(menuPlan: MenuPlan) {
        menuPlanDao.deleteMenuPlan(menuPlan)
    }

    // ==================== HELPER METHODS ====================

    /**
     * Genera recetas de ejemplo para la base de datos
     */
    private fun getSampleRecipes(): List<Recipe> {
        return listOf(
            Recipe(
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