package com.example.drowsiness.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.drowsiness.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val prefs = requireContext().getSharedPreferences("DriverProfile", Context.MODE_PRIVATE)
        binding.etDriverName.setText(prefs.getString("DRIVER_NAME", ""))
        binding.etDriverId.setText(prefs.getString("DRIVER_ID", ""))
        binding.switchAlertAudio.isChecked = prefs.getBoolean("AUDIO_ALERTS", true)

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etDriverName.text.toString()
            val id = binding.etDriverId.text.toString()
            
            prefs.edit()
                .putString("DRIVER_NAME", name)
                .putString("DRIVER_ID", id)
                .putBoolean("AUDIO_ALERTS", binding.switchAlertAudio.isChecked)
                .apply()
                
            Toast.makeText(requireContext(), "Profile Saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
