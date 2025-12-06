package com.lasalle.mercadosaludable.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lasalle.mercadosaludable.data.model.Recipe
import com.lasalle.mercadosaludable.databinding.PageMenuDayBinding

/**
 * Adaptador para el ViewPager2 que muestra cada día de la semana
 */
class MenuWeekAdapter(
    private val recipesPerDay: Map<Int, List<Recipe>>,
    private val onRecipeClick: (Recipe) -> Unit
) : RecyclerView.Adapter<MenuWeekAdapter.DayViewHolder>() {

    inner class DayViewHolder(
        private val binding: PageMenuDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(day: Int, recipes: List<Recipe>) {
            // Configurar RecyclerView para las 3 comidas del día
            binding.rvDayMeals.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = MenuDayAdapter(recipes, onRecipeClick)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = PageMenuDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val recipes = recipesPerDay[position] ?: emptyList()
        holder.bind(position, recipes)
    }

    override fun getItemCount(): Int = 7 // 7 días
}