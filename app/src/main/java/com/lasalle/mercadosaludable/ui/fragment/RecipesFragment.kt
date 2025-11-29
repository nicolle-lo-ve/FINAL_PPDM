package com.lasalle.mercadosaludable.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lasalle.mercadosaludable.R
import com.lasalle.mercadosaludable.databinding.FragmentRecipesBinding
import com.lasalle.mercadosaludable.ui.adapter.RecipeAdapter
import com.lasalle.mercadosaludable.ui.viewmodel.RecipeViewModel
import com.google.android.material.chip.Chip

/**
 * Fragment que muestra el catálogo completo de recetas.
 * Incluye búsqueda y filtrado por categorías y condiciones médicas.
 */
class RecipesFragment : Fragment() {

    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeViewModel by viewModels()
    private lateinit var adapter: RecipeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupFilters()
        setupObservers()
    }

    /**
     * Configura el RecyclerView con su adapter
     */
    private fun setupRecyclerView() {
        adapter = RecipeAdapter { recipe ->
            // Navegar al detalle de la receta
            val bundle = Bundle().apply {
                putLong("recipeId", recipe.id)
            }
            findNavController().navigate(
                R.id.action_recipesFragment_to_recipeDetailFragment,
                bundle
            )
        }

        binding.recyclerViewRecipes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecipesFragment.adapter
        }
    }

    /**
     * Configura el SearchView para búsqueda de recetas
     */
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.searchRecipes(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    if (it.length >= 3 || it.isEmpty()) {
                        viewModel.searchRecipes(it)
                    }
                }
                return true
            }
        })
    }

    /**
     * Configura los chips de filtros
     */
    private fun setupFilters() {
        // Filtros por categoría
        binding.chipDesayuno.setOnClickListener { filterByCategory("Desayuno", it as Chip) }
        binding.chipAlmuerzo.setOnClickListener { filterByCategory("Almuerzo", it as Chip) }
        binding.chipCena.setOnClickListener { filterByCategory("Cena", it as Chip) }

        // Filtros por condición
        binding.chipDiabetes.setOnClickListener { filterByCondition("diabetes", it as Chip) }
        binding.chipHipertension.setOnClickListener { filterByCondition("hipertension", it as Chip) }
        binding.chipObesidad.setOnClickListener { filterByCondition("obesidad", it as Chip) }

        // Botón limpiar filtros
        binding.btnClearFilters.setOnClickListener {
            clearAllFilters()
        }
    }

    /**
     * Filtra recetas por categoría
     */
    private fun filterByCategory(category: String, chip: Chip) {
        if (chip.isChecked) {
            uncheckAllChips(binding.chipGroupCategory, chip)
            viewModel.filterByCategory(category)
        } else {
            viewModel.clearFilters()
        }
    }

    /**
     * Filtra recetas por condición médica
     */
    private fun filterByCondition(condition: String, chip: Chip) {
        if (chip.isChecked) {
            uncheckAllChips(binding.chipGroupCondition, chip)
            viewModel.filterByCondition(condition)
        } else {
            viewModel.clearFilters()
        }
    }

    /**
     * Desmarca todos los chips excepto el seleccionado
     */
    private fun uncheckAllChips(chipGroup: com.google.android.material.chip.ChipGroup, except: Chip) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip
            if (chip != null && chip.id != except.id) {
                chip.isChecked = false
            }
        }
    }

    /**
     * Limpia todos los filtros
     */
    private fun clearAllFilters() {
        binding.chipGroupCategory.clearCheck()
        binding.chipGroupCondition.clearCheck()
        viewModel.clearFilters()
    }

    /**
     * Configura los observers del ViewModel
     */
    private fun setupObservers() {
        // Observer para todas las recetas
        viewModel.allRecipes.observe(viewLifecycleOwner) { recipes ->
            if (recipes.isNotEmpty()) {
                adapter.submitList(recipes)
                showEmptyState(false)
            } else {
                showEmptyState(true)
            }
        }

        // Observer para recetas filtradas
        viewModel.filteredRecipes.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            showEmptyState(recipes.isEmpty())
        }
    }

    /**
     * Muestra u oculta el estado vacío
     */
    private fun showEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerViewRecipes.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerViewRecipes.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}