
package com.lasalle.mercadosaludable.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.databinding.ItemMenuDayBinding

/**
 * Adapter para mostrar las recetas de un día específico en el menú semanal.
 * Muestra desayuno, almuerzo y cena de forma vertical.
 */
class MenuDayAdapter(
    private val recipes: List<Recipe>,
    private val onRecipeClick: (Recipe) -> Unit
) : RecyclerView.Adapter<MenuDayAdapter.MenuDayViewHolder>() {

    private val mealTypes = listOf("Desayuno", "Almuerzo", "Cena")

    /**
     * ViewHolder para cada comida del día
     */
    inner class MenuDayViewHolder(
        private val binding: ItemMenuDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe, mealType: String) {
            binding.apply {
                tvMealType.text = mealType
                tvRecipeName.text = recipe.name
                tvCalories.text = "${recipe.calories} kcal"
                tvTime.text = "${recipe.preparationTime} min"

                root.setOnClickListener {
                    onRecipeClick(recipe)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuDayViewHolder {
        val binding = ItemMenuDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MenuDayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuDayViewHolder, position: Int) {
        if (position < recipes.size) {
            holder.bind(recipes[position], mealTypes.getOrElse(position) { "Comida" })
        }
    }

    override fun getItemCount(): Int = recipes.size.coerceAtMost(3) // Máximo 3 comidas
}