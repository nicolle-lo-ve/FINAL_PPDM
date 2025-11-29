package com.lasalle.mercadosaludable.data.local

import android.content.Context
import androidx.room.*
import androidx.lifecycle.LiveData
import com.lasalle.mercadosaludable.data.model.User
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.data.model.MenuPlan

/**
 * DAO (Data Access Object) para operaciones de la tabla User
 */
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserByIdLiveData(userId: String): LiveData<User?>

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): User?
}

/**
 * DAO para operaciones de la tabla Recipe
 */
@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRecipes(recipes: List<Recipe>)

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Query("SELECT * FROM recipes")
    fun getAllRecipes(): LiveData<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipeById(recipeId: Long): Recipe?

    @Query("SELECT * FROM recipes WHERE id IN (:recipeIds)")
    suspend fun getRecipesByIds(recipeIds: List<Long>): List<Recipe>

    @Query("SELECT * FROM recipes WHERE category = :category")
    fun getRecipesByCategory(category: String): LiveData<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE suitableFor LIKE '%' || :condition || '%'")
    fun getRecipesByCondition(condition: String): LiveData<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :searchQuery || '%' OR ingredients LIKE '%' || :searchQuery || '%'")
    fun searchRecipes(searchQuery: String): LiveData<List<Recipe>>

    @Query("UPDATE recipes SET timesUsed = timesUsed + 1 WHERE id = :recipeId")
    suspend fun incrementTimesUsed(recipeId: Long)

    @Query("SELECT * FROM recipes ORDER BY rating DESC, timesUsed DESC LIMIT :limit")
    fun getPopularRecipes(limit: Int): LiveData<List<Recipe>>
}

/**
 * DAO para operaciones de la tabla MenuPlan
 */
@Dao
interface MenuPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuPlan(menuPlan: MenuPlan): Long

    @Update
    suspend fun updateMenuPlan(menuPlan: MenuPlan)

    @Delete
    suspend fun deleteMenuPlan(menuPlan: MenuPlan)

    @Query("SELECT * FROM menu_plans WHERE userId = :userId ORDER BY createdAt DESC")
    fun getMenuPlansByUser(userId: String): LiveData<List<MenuPlan>>

    @Query("SELECT * FROM menu_plans WHERE id = :menuPlanId")
    suspend fun getMenuPlanById(menuPlanId: Long): MenuPlan?

    @Query("SELECT * FROM menu_plans WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getActiveMenuPlan(userId: String): MenuPlan?

    @Query("SELECT * FROM menu_plans WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun getActiveMenuPlanLiveData(userId: String): LiveData<MenuPlan?>

    @Query("UPDATE menu_plans SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAllMenuPlans(userId: String)

    @Query("SELECT * FROM menu_plans WHERE userId = :userId AND isFavorite = 1")
    fun getFavoriteMenuPlans(userId: String): LiveData<List<MenuPlan>>
}

/**
 * Base de datos Room principal de la aplicación.
 * Contiene todas las entidades y proporciona acceso a los DAOs.
 */
@Database(
    entities = [User::class, Recipe::class, MenuPlan::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun recipeDao(): RecipeDao
    abstract fun menuPlanDao(): MenuPlanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia única de la base de datos (Singleton pattern)
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mercado_saludable_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}