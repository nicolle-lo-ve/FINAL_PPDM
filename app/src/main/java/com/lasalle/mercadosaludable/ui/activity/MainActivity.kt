package com.lasalle.mercadosaludable.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.lasalle.mercadosaludable.R
import com.lasalle.mercadosaludable.databinding.ActivityMainBinding

/**
 * Activity principal que contiene la navegación de la aplicación.
 * Utiliza Navigation Component con Bottom Navigation para navegar entre fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        const val PREFS_NAME = "MercadoSaludablePrefs"
        const val KEY_USER_ID = "userId"

        /**
         * Guarda el ID del usuario en SharedPreferences
         */
        fun saveUserId(context: Context, userId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }

        /**
         * Obtiene el ID del usuario desde SharedPreferences
         */
        fun getUserId(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_USER_ID, null)
        }

        /**
         * Limpia los datos de sesión
         */
        fun clearSession(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Verificar si hay sesión activa
        if (auth.currentUser == null || getUserId(this) == null) {
            // Redirigir a login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Inflar layout con ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar navegación
        setupNavigation()
    }

    /**
     * Configura el Navigation Component con Bottom Navigation
     */
    private fun setupNavigation() {
        // Obtener NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Configurar BottomNavigationView con NavController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Configurar título del toolbar según el fragmento
        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = when (destination.id) {
                R.id.homeFragment -> "Inicio"
                R.id.recipesFragment -> "Recetas"
                R.id.menuPlanFragment -> "Mi Menú"
                R.id.profileFragment -> "Perfil"
                else -> "Mercado Saludable"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}