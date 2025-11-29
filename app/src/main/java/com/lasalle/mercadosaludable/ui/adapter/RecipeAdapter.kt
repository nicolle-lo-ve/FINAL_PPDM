package com.lasalle.mercadosaludable.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lasalle.mercadosaludable.R
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.databinding.ItemRecipeBinding

/**
 * Adapter para mostrar la lista de recetas en un RecyclerView.
 * Utiliza DiffUtil para optimizar las actualizaciones de la lista.
 */
class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback()) {

    /**
     * ViewHolder que contiene las vistas de cada item de receta
     */
    inner class RecipeViewHolder(
        private val binding: ItemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Vincula los datos de una receta con las vistas
         */
        fun bind(recipe: Recipe) {
            binding.apply {
                // Nombre y descripción
                tvRecipeName.text = recipe.name
                tvRecipeDescription.text = recipe.description

                // Categoría
                tvCategory.text = recipe.category

                // Información nutricional
                tvCalories.text = "${recipe.calories} kcal"
                tvProtein.text = "${recipe.protein}g proteína"

                // Tiempo de preparación
                tvTime.text = "${recipe.preparationTime} min"

                // Dificultad
                tvDifficulty.text = recipe.difficulty
                tvDifficulty.setBackgroundResource(
                    when (recipe.difficulty) {
                        "Fácil" -> R.drawable.bg_difficulty_easy
                        "Media" -> R.drawable.bg_difficulty_medium
                        "Difícil" -> R.drawable.bg_difficulty_hard
                        else -> R.drawable.bg_difficulty_easy
                    }
                )

                // Costo estimado
                tvCost.text = "S/ ${String.format("%.2f", recipe.estimatedCost)}"

                // Rating
                tvRating.text = String.format("%.1f", recipe.rating)

                // Click listener
                root.setOnClickListener {
                    onRecipeClick(recipe)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * DiffUtil callback para comparar items de recetas
     */
    private class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem == newItem
        }
    }
}

