package com.example.drowsiness.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.drowsiness.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import android.animation.ValueAnimator
import com.example.drowsiness.ml.SvmDebugData

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels {
        (requireActivity() as MainActivity).viewModelFactory
    }

    private var currentEsp32ImageWidth: Int = 1
    private var currentEsp32ImageHeight: Int = 1
    
    private var pulseAnimator: ValueAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        val prefs = requireContext().getSharedPreferences("DrowsinessApp", android.content.Context.MODE_PRIVATE)
        val savedIp = prefs.getString("LAST_ESP32_IP", "192.168.4.1")
        binding.etEsp32Ip.setText(savedIp)

        binding.btnConnectEsp32.setOnClickListener {
            if (binding.btnConnectEsp32.text == "Connect" || binding.btnConnectEsp32.text == "Connecting...") {
                var rawIp = binding.etEsp32Ip.text.toString()
                
                // Sanitize input
                rawIp = rawIp.trim()
                    .replace("http://", "", ignoreCase = true)
                    .replace("https://", "", ignoreCase = true)
                    .replace(":81", "")
                    .replace("/stream", "", ignoreCase = true)
                    .trim()
                    
                // Update UI to reflect sanitized input
                binding.etEsp32Ip.setText(rawIp)
                
                // Validate IPv4 format
                val ipRegex = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
                if (!ipRegex.matches(rawIp)) {
                    Toast.makeText(requireContext(), "Please enter a valid ESP32 IP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                viewModel.connectEsp32(rawIp)
            } else {
                viewModel.disconnectEsp32()
            }
        }
        
        binding.btnProvisionEsp32.setOnClickListener {
            val ssid = binding.etProvisionSsid.text.toString()
            val pass = binding.etProvisionPass.text.toString()
            viewModel.provisionWifi(ssid, pass)
        }
        
        binding.btnTestAlert.setOnClickListener {
            viewModel.sendTestAlert()
        }

        val statusText = """
            Model Status:
            ${viewModel.svmModelLoadStatus}

            Exception:
            ${viewModel.svmExceptionClass ?: "N/A"}

            Message:
            ${viewModel.svmExceptionMessage ?: "N/A"}

            ortSession:
            ${if (viewModel.svmSessionNull) "NULL" else "NOT NULL"}
        """.trimIndent()
        binding.tvPlaygroundModelStatus.text = statusText
        
        binding.btnPlaygroundPresetAwake.setOnClickListener {
            binding.etPlaygroundEar.setText("0.30")
            binding.etPlaygroundMar.setText("0.30")
        }

        binding.btnPlaygroundPresetBorderline.setOnClickListener {
            binding.etPlaygroundEar.setText("0.18")
            binding.etPlaygroundMar.setText("0.50")
        }

        binding.btnPlaygroundPresetDrowsy.setOnClickListener {
            binding.etPlaygroundEar.setText("0.10")
            binding.etPlaygroundMar.setText("0.80")
        }

        binding.btnRunPlaygroundPrediction.setOnClickListener {
            val earStr = binding.etPlaygroundEar.text?.toString()?.trim() ?: ""
            val marStr = binding.etPlaygroundMar.text?.toString()?.trim() ?: ""
            
            val ear = earStr.toFloatOrNull()
            val mar = marStr.toFloatOrNull()
            
            if (ear == null || mar == null) {
                Toast.makeText(requireContext(), "Please enter valid numeric values for EAR and MAR", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.runPlaygroundPrediction(ear, mar)
            }
        }
        
        binding.llDiagnosticsHeader.setOnClickListener {
            if (binding.llDiagnosticsContent.visibility == View.VISIBLE) {
                binding.llDiagnosticsContent.visibility = View.GONE
                binding.ivDiagnosticsArrow.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                binding.llDiagnosticsContent.visibility = View.VISIBLE
                binding.ivDiagnosticsArrow.setImageResource(android.R.drawable.arrow_up_float)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.toastMessage.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.status.collect { status ->
                        binding.tvDriverStatus.text = status
                        if (status == "AWAKE") {
                            stopPulseAnimation()
                            binding.tvDriverStatus.setBackgroundColor(Color.parseColor("#4CAF50"))
                            binding.chipAiStatus.text = "AI: Active (Awake)"
                            binding.chipAiStatus.setChipBackgroundColorResource(android.R.color.holo_green_light)
                        } else {
                            startPulseAnimation()
                            binding.chipAiStatus.text = "AI: Triggered (Drowsy)"
                            binding.chipAiStatus.setChipBackgroundColorResource(android.R.color.holo_red_light)
                        }
                    }
                }
                launch {
                    viewModel.esp32ConnectionState.collect { state ->
                        val ip = binding.etEsp32Ip.text.toString()
                        val diagnostics = """
                            IP: $ip
                            Stream: http://$ip:81/stream
                            State: ${state.name}
                        """.trimIndent()
                        binding.tvEsp32Diagnostics.text = diagnostics
                        
                        binding.btnConnectEsp32.text = when(state) {
                            ConnectionState.CONNECTED -> "Disconnect"
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.PROVISIONING -> "Provisioning..."
                            else -> "Connect"
                        }
                        
                        if (state == ConnectionState.FAILED || state == ConnectionState.PROVISIONING) {
                            binding.cardProvisioning.visibility = View.VISIBLE
                        } else {
                            binding.cardProvisioning.visibility = View.GONE
                        }
                        
                        binding.chipEsp32Status.text = "ESP32: ${state.name}"
                        when(state) {
                            ConnectionState.CONNECTED -> {
                                binding.chipEsp32Status.setChipBackgroundColorResource(android.R.color.holo_green_light)
                                binding.chipEsp32Status.setChipIconResource(android.R.drawable.presence_online)
                            }
                            ConnectionState.CONNECTING -> {
                                binding.chipEsp32Status.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                                binding.chipEsp32Status.setChipIconResource(android.R.drawable.presence_away)
                            }
                            else -> {
                                binding.chipEsp32Status.setChipBackgroundColorResource(android.R.color.darker_gray)
                                binding.chipEsp32Status.setChipIconResource(android.R.drawable.presence_offline)
                            }
                        }
                    }
                }
                launch {
                    viewModel.esp32StreamFlow.collect { bitmap ->
                        if (bitmap != null) {
                            binding.llStreamPlaceholder.visibility = View.GONE
                            binding.ivEsp32Stream.setImageBitmap(bitmap)
                            currentEsp32ImageWidth = bitmap.width
                            currentEsp32ImageHeight = bitmap.height
                        } else {
                            binding.llStreamPlaceholder.visibility = View.VISIBLE
                            binding.ivEsp32Stream.setImageDrawable(null)
                            binding.overlayView.clear()
                        }
                    }
                }
                launch {
                    viewModel.esp32Landmarks.collect { result ->
                        if (result != null) {
                            binding.overlayView.setResults(result, currentEsp32ImageWidth, currentEsp32ImageHeight)
                        } else {
                            binding.overlayView.clear()
                        }
                    }
                }
                launch {
                    viewModel.esp32Ear.collect { ear ->
                        binding.tvEsp32EarDebug.text = String.format(java.util.Locale.getDefault(), "EAR: %.3f", ear)
                    }
                }
                launch {
                    viewModel.esp32Mar.collect { mar ->
                        binding.tvEsp32MarDebug.text = String.format(java.util.Locale.getDefault(), "MAR: %.3f", mar)
                    }
                }
                launch {
                    kotlinx.coroutines.flow.combine(
                        viewModel.svmDebugDataFlow,
                        viewModel.esp32DrowsyFramesCountFlow,
                        viewModel.esp32DriverStateFlow
                    ) { debugData, count, state ->
                        if (debugData != null) {
                            val keysStr = debugData.outputKeys.joinToString(", ")
                            val valuesStr = debugData.outputValues.entries.joinToString("\n") { (k, v) -> "  $k: $v" }
                            val predictionLabel = if (debugData.finalPrediction == 1) "1 (DROWSY)" else "0 (AWAKE)"
                            
                            String.format(
                                java.util.Locale.getDefault(),
                                "Raw EAR: %.4f\nRaw MAR: %.4f\nScaled EAR: %.4f\nScaled MAR: %.4f\nONNX Output Keys: [%s]\nRaw ONNX Output Values:\n%s\nFinal Prediction: %s\nDrowsy Frames: %d\nState: %s",
                                debugData.rawEar,
                                debugData.rawMar,
                                debugData.scaledEar,
                                debugData.scaledMar,
                                keysStr,
                                valuesStr,
                                predictionLabel,
                                count,
                                state.name
                            )
                        } else {
                            "EAR: 0.00\nMAR: 0.00\nPrediction: 0\nDrowsy Frames: 0\nState: AWAKE"
                        }
                    }.collect { debugText ->
                        binding.tvSvmDebugInfo.text = debugText
                    }
                }
                launch {
                    viewModel.playgroundResultFlow.collect { debugData ->
                        if (debugData != null) {
                            binding.cardPlaygroundResult.visibility = View.VISIBLE
                            val keysStr = debugData.outputKeys.joinToString(", ")
                            val labelStr = debugData.outputValues["label"] ?: "N/A"
                            val probStr = debugData.outputValues["probabilities"] ?: "N/A"
                            val predictionLabel = if (debugData.finalPrediction == 1) "1 (DROWSY)" else "0 (AWAKE)"
                            
                            val resultsText = String.format(
                                java.util.Locale.US,
                                "Raw EAR: %.2f\nRaw MAR: %.2f\n\nScaled EAR: %.2f\nScaled MAR: %.2f\n\nTensor Sent To ONNX:\n[%.2f, %.2f]\n\nONNX Output Keys:\n[%s]\n\nRaw Label:\n%s\n\nRaw Probabilities:\n%s\n\nFinal Prediction:\n%s",
                                debugData.rawEar,
                                debugData.rawMar,
                                debugData.scaledEar,
                                debugData.scaledMar,
                                debugData.scaledEar,
                                debugData.scaledMar,
                                keysStr,
                                labelStr,
                                probStr,
                                predictionLabel
                            )
                            binding.tvPlaygroundResult.text = resultsText
                        } else {
                            binding.cardPlaygroundResult.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun startPulseAnimation() {
        if (pulseAnimator == null) {
            pulseAnimator = ValueAnimator.ofArgb(Color.parseColor("#F44336"), Color.parseColor("#FFCDD2")).apply {
                duration = 500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { animator ->
                    binding.tvDriverStatus.setBackgroundColor(animator.animatedValue as Int)
                }
                start()
            }
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPulseAnimation()
        _binding = null
    }
}
