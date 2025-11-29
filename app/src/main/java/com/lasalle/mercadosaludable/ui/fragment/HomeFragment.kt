package com.lasalle.mercadosaludable.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.lasalle.mercadosaludable.R
import com.lasalle.mercadosaludable.databinding.FragmentHomeBinding
import com.lasalle.mercadosaludable.ui.activity.MainActivity
import com.lasalle.mercadosaludable.ui.viewmodel.MenuPlanViewModel
import com.lasalle.mercadosaludable.ui.viewmodel.ProfileViewModel
import com.lasalle.mercadosaludable.ui.viewmodel.RecipeViewModel

/**
 * Fragment de inicio que muestra un resumen del perfil del usuario,
 * el menú activo y recetas recomendadas.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by viewModels()
    private val menuPlanViewModel: MenuPlanViewModel by viewModels()
    private val recipeViewModel: RecipeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = MainActivity.getUserId(requireContext())

        if (userId != null) {
            setupObservers(userId)
            loadData(userId)
        }

        setupClickListeners()
    }

    /**
     * Configura los observers de los ViewModels
     */
    private fun setupObservers(userId: String) {
        // Observer del perfil del usuario
        profileViewModel.getUserLiveData(userId).observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.tvWelcome.text = "¡Hola, ${it.name}!"
                binding.tvBmiValue.text = String.format("%.1f", it.bmi)
                binding.tvBmiCategory.text = com.lasalle.mercadosaludable.data.model.User.classifyBMI(it.bmi)

                // Mostrar condiciones médicas
                val conditions = it.getMedicalConditionsList()
                binding.tvConditions.text = if (conditions.isEmpty()) {
                    "Ninguna registrada"
                } else {
                    conditions.joinToString(", ") { condition ->
                        condition.replaceFirstChar { char -> char.uppercase() }
                    }
                }
            }
        }

        // Observer del menú activo
        menuPlanViewModel.activeMenuPlan.observe(viewLifecycleOwner) { menuPlan ->
            if (menuPlan != null) {
                binding.tvMenuActive.text = menuPlan.name
                binding.tvMenuCost.text = "S/ ${String.format("%.2f", menuPlan.totalCost)}"
                binding.tvMenuCalories.text = "${menuPlan.averageDailyCalories} kcal/día"
                binding.cardNoMenu.visibility = View.GONE
                binding.cardMenuActive.visibility = View.VISIBLE
            } else {
                binding.cardNoMenu.visibility = View.VISIBLE
                binding.cardMenuActive.visibility = View.GONE
            }
        }
    }

    /**
     * Carga los datos necesarios
     */
    private fun loadData(userId: String) {
        profileViewModel.loadUserProfile(userId)
        menuPlanViewModel.loadActiveMenuPlan(userId)
    }

    /**
     * Configura los listeners de los botones
     */
    private fun setupClickListeners() {
        // Botón para generar nuevo menú
        binding.btnGenerateMenu.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_menuPlanFragment)
        }

        // Card del menú activo
        binding.cardMenuActive.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_menuPlanFragment)
        }

        // Botón para ver recetas
        binding.btnViewRecipes.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_recipesFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
