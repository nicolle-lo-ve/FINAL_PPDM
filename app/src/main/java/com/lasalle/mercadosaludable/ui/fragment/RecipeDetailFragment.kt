package com.lasalle.mercadosaludable.ui.fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lasalle.mercadosaludable.databinding.FragmentRecipeDetailBinding
import com.lasalle.mercadosaludable.ui.viewmodel.RecipeViewModel
class RecipeDetailFragment : Fragment() {
    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recipeId = arguments?.getLong("recipeId") ?: return
        viewModel.loadRecipe(recipeId)
        viewModel.selectedRecipe.observe(viewLifecycleOwner) { recipe ->
            recipe?.let {
                binding.tvRecipeName.text = it.name
                binding.tvDescription.text = it.description
                binding.tvCalories.text = "${it.calories} kcal"
                binding.tvProtein.text = "${it.protein}g"
                binding.tvCarbs.text = "${it.carbohydrates}g"
                binding.tvFats.text = "${it.fats}g"
                binding.tvFiber.text = "${it.fiber}g"
                binding.tvTime.text = "${it.preparationTime} min"
                binding.tvDifficulty.text = it.difficulty
                binding.tvServings.text = "${it.servings} porciones"
                binding.tvCost.text = "S/ ${String.format("%.2f", it.estimatedCost)}"
                // Mostrar ingredientes
                val ingredients = it.getIngredientsList()
                binding.tvIngredients.text = ingredients.joinToString("\n") { ingredient ->
                    "• $ingredient"
                }
                // Mostrar instrucciones
                val instructions = it.getInstructionsList()
                binding.tvInstructions.text = instructions.mapIndexed { index, instruction ->
                    "${index + 1}. $instruction"
                }.joinToString("\n\n")
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
