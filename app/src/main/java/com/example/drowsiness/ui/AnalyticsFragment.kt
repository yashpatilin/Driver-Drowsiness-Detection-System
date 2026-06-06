package com.example.drowsiness.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.drowsiness.databinding.FragmentAnalyticsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private val earGraphEntries = mutableListOf<Entry>()
    private val marGraphEntries = mutableListOf<Entry>()
    private var earTimeIndex = 0f
    private var marTimeIndex = 0f

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGraphs()
        observeViewModel()
    }

    private fun setupGraphs() {
        binding.earChart.description.isEnabled = false
        binding.earChart.setTouchEnabled(false)
        binding.earChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.earChart.axisRight.isEnabled = false
        binding.earChart.axisLeft.axisMinimum = 0f
        binding.earChart.axisLeft.axisMaximum = 0.5f

        binding.marChart.description.isEnabled = false
        binding.marChart.setTouchEnabled(false)
        binding.marChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.marChart.axisRight.isEnabled = false
        binding.marChart.axisLeft.axisMinimum = 0f
        binding.marChart.axisLeft.axisMaximum = 1.0f
    }

    private fun updateEarGraph(ear: Float) {
        earGraphEntries.add(Entry(earTimeIndex++, ear))
        if(earGraphEntries.size > 50) earGraphEntries.removeAt(0)
        
        val dataSet = LineDataSet(earGraphEntries, "EAR Value").apply {
            color = Color.BLUE
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#802196F3")
        }
        
        binding.earChart.data = LineData(dataSet)
        binding.earChart.invalidate()
    }

    private fun updateMarGraph(mar: Float) {
        marGraphEntries.add(Entry(marTimeIndex++, mar))
        if(marGraphEntries.size > 50) marGraphEntries.removeAt(0)
        
        val dataSet = LineDataSet(marGraphEntries, "MAR Value").apply {
            color = Color.MAGENTA
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#80E91E63")
        }
        
        binding.marChart.data = LineData(dataSet)
        binding.marChart.invalidate()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.esp32Ear.collect { ear ->
                        updateEarGraph(ear)
                    }
                }
                launch {
                    viewModel.esp32Mar.collect { mar ->
                        updateMarGraph(mar)
                    }
                }
                launch {
                    viewModel.totalDrowsyTime.collect { duration ->
                        val totalSecs = duration / 1000
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        binding.tvTotalDrowsyTime.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
