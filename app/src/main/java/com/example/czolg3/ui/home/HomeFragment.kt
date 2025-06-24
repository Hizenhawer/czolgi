package com.example.czolg3.ui.home

// import androidx.lifecycle.ViewModelProvider // Only if you still use HomeViewModel for other purposes
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.czolg3.ble.BleViewModel
import com.example.czolg3.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    // Use activityViewModels for BleViewModel as it's shared
    private val bleViewModel: BleViewModel by activityViewModels()
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // If HomeViewModel is not used by this fragment for other purposes, you can remove it.
        // val homeViewModel =
        // ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Make the log TextView scrollable
        binding.textViewLog.movementMethod = ScrollingMovementMethod() // Assuming you have a textViewLog

        setupScanButton()
        setupLightsLoopButton() // Setup the new button
        observeViewModel()

        return root
    }

    private fun setupScanButton() {
        binding.scanButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Before starting a new scan, ensure any previous connection is properly closed.
                // Disconnecting will also stop any command loops.
                bleViewModel.disconnectDevice() // Disconnect first if connected or attempting
                bleViewModel.startBleScan()
            } else {
                bleViewModel.stopBleScan()
                // If the user unchecks scan, and they were connected, they might expect a disconnect.
                // The BleViewModel's stopScan might lead to "Device not found" if scan times out,
                // but if connected, explicit disconnect is clearer.
                // Consider the desired UX. If already connected, unchecking scan might not disconnect.
                // If you want unchecking to also disconnect:
                // if (bleViewModel.connectionStatus.value == "Connected" || bleViewModel.connectionStatus.value == "Ready") {
                // bleViewModel.disconnectDevice()
                // }
            }
        }
    }

    private fun setupLightsLoopButton() {
        // Assuming you add a button with id 'buttonToggleLightsLoop' in your fragment_home.xml
        binding.buttonToggleLightsLoop.setOnClickListener {
            bleViewModel.toggleLightsLoop()
        }
    }

    private fun observeViewModel() {
        // Observe Connection Status
        bleViewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            binding.textViewStatus.text = "Status: $status" // Assuming you have a textViewStatus
            // Update scan button state based on connection status
            // For example, disable scan button if connected, or change its text
            when (status) {
                "Scanning..." -> {
                    binding.scanButton.isEnabled = true // It's actively scanning
                    binding.scanButton.isChecked = true
                }
                "Connected", "Ready", "Connecting..." -> {
                    binding.scanButton.isEnabled = false // Disable scan if connected or connecting
                    // scanButton.isChecked = false // Or keep it checked to indicate an active session
                }
                "Disconnected", "Scan Failed", "Error", "Device not found", "Service Not Found", "Characteristic Not Found", "Notification Setup Failed" -> {
                    binding.scanButton.isEnabled = true
                    binding.scanButton.isChecked = false
                }
                else -> {
                    binding.scanButton.isEnabled = true
                }
            }

            // Enable/disable lights loop button based on connection status
            binding.buttonToggleLightsLoop.isEnabled = (status == "Ready")
        }

        // Observe Operation Log
        bleViewModel.operationLog.observe(viewLifecycleOwner) { logBuilder ->
            binding.textViewLog.text = logBuilder.toString()
            // Optional: Scroll to the bottom of the log
            // Ensure textViewLog is laid out before trying to scroll
            binding.textViewLog.post {
                val scrollAmount = binding.textViewLog.layout?.getLineTop(binding.textViewLog.lineCount) ?: 0 - binding.textViewLog.height
                if (scrollAmount > 0) {
                    binding.textViewLog.scrollTo(0, scrollAmount)
                } else {
                    binding.textViewLog.scrollTo(0, 0)
                }
            }
        }

        // Observe Lights Loop Active State
        bleViewModel.isLightsLoopUiActive.observe(viewLifecycleOwner) { isActive ->
            binding.buttonToggleLightsLoop.text = if (isActive) "Stop Lights Loop" else "Start Lights Loop"
        }

        // Observe Received Data (if you plan to display it)
        bleViewModel.receivedData.observe(viewLifecycleOwner) { data ->
            // Assuming you have a textViewReceivedData
            binding.textViewReceivedData.text = "Received: $data"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // It's good practice to remove observers if you set them up with `this` (Fragment)
        // as the LifecycleOwner, but with `viewLifecycleOwner`, they are automatically handled.
        // However, explicitly nullifying binding is important.
        _binding = null
    }
}