package com.lasalle.mercadosaludable.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lasalle.mercadosaludable.databinding.FragmentMenuPlanBinding
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.ui.activity.MainActivity
import com.lasalle.mercadosaludable.ui.adapter.MenuWeekAdapter
import com.lasalle.mercadosaludable.ui.viewmodel.MenuPlanViewModel
import com.lasalle.mercadosaludable.ui.viewmodel.ProfileViewModel

class MenuPlanFragment : Fragment() {

    private var _binding: FragmentMenuPlanBinding? = null
    private val binding get() = _binding!!

    private val menuPlanViewModel: MenuPlanViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private val daysOfWeek = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuPlanBinding.inflate(inflater, container, false)
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

    private fun setupObservers(userId: String) {
        // Observer del menú activo
        menuPlanViewModel.activeMenuPlan.observe(viewLifecycleOwner) { menuPlan ->
            if (menuPlan != null) {
                binding.tvMenuName.text = menuPlan.name
                binding.tvTotalCost.text = "S/ ${String.format("%.2f", menuPlan.totalCost)}"
                binding.tvAvgCalories.text = "${menuPlan.averageDailyCalories} kcal/día"

                binding.cardNoMenu.visibility = View.GONE
                binding.cardMenuDetails.visibility = View.VISIBLE
            } else {
                binding.cardNoMenu.visibility = View.VISIBLE
                binding.cardMenuDetails.visibility = View.GONE
            }
        }

        // Observer de las recetas del menú
        menuPlanViewModel.menuRecipes.observe(viewLifecycleOwner) { recipesMap ->
            if (recipesMap.isNotEmpty()) {
                setupViewPager(recipesMap)
            }
        }

        // Observer de estado de generación
        menuPlanViewModel.generationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MenuPlanViewModel.GenerationState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnGenerateMenu.isEnabled = false
                }
                is MenuPlanViewModel.GenerationState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerateMenu.isEnabled = true
                    Toast.makeText(requireContext(), "Menú generado con éxito", Toast.LENGTH_SHORT).show()
                }
                is MenuPlanViewModel.GenerationState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerateMenu.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerateMenu.isEnabled = true
                }
            }
        }

        // Observer del usuario
        profileViewModel.getUserLiveData(userId).observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.btnGenerateMenu.setOnClickListener {
                    menuPlanViewModel.generateWeeklyMenu(user)
                }
            }
        }
    }

    private fun setupViewPager(recipesMap: Map<Int, List<Recipe>>) {
        // Inicializar ViewPager y TabLayout
        viewPager = binding.viewPager
        tabLayout = binding.tabLayout

        // Configurar adaptador
        val adapter = MenuWeekAdapter(recipesMap) { recipe ->
            // TODO: Manejar clic en receta (abrir detalle)
            Toast.makeText(requireContext(), recipe.name, Toast.LENGTH_SHORT).show()
        }

        viewPager.adapter = adapter

        // Conectar TabLayout con ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = daysOfWeek[position]
        }.attach()
    }

    private fun loadData(userId: String) {
        profileViewModel.loadUserProfile(userId)
        menuPlanViewModel.loadActiveMenuPlan(userId)
    }

    private fun setupClickListeners() {
        // Implementar si hay más botones
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}