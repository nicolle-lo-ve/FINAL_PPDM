package com.lasalle.mercadosaludable.ui.fragment
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.firebase.auth.FirebaseAuth
import com.lasalle.mercadosaludable.databinding.FragmentProfileBinding
import com.lasalle.mercadosaludable.ui.activity.LoginActivity
import com.lasalle.mercadosaludable.ui.activity.MainActivity
import com.lasalle.mercadosaludable.ui.viewmodel.ProfileViewModel
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
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
        viewModel.getUserLiveData(userId).observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.tvName.text = it.name
                binding.tvEmail.text = it.email
                binding.tvAge.text = "${it.age} años"
                binding.tvWeight.text = "${it.weight} kg"
                binding.tvHeight.text = "${it.height} cm"
                binding.tvBmi.text = String.format("%.1f", it.bmi)
                binding.tvBmiCategory.text = com.lasalle.mercadosaludable.data.model.User.classifyBMI(it.bmi)
                binding.tvBudget.text = "S/ ${String.format("%.2f", it.monthlyBudget)}"
                val conditions = it.getMedicalConditionsList()
                binding.tvConditions.text = if (conditions.isEmpty()) {
                    "Ninguna"
                } else {
                    conditions.joinToString(", ")
                }
                val allergies = it.getAllergiesList()
                binding.tvAllergies.text = if (allergies.isEmpty()) {
                    "Ninguna"
                } else {
                    allergies.joinToString(", ")
                }
            }
        }
    }
    private fun loadData(userId: String) {
        viewModel.loadUserProfile(userId)
    }
    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            // Cerrar sesión
            auth.signOut()
            MainActivity.clearSession(requireContext())
            // Ir a login
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}