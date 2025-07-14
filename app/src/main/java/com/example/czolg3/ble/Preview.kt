package com.example.czolg3.ble

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBleViewModel : IBleViewModel {

    // Backing property for connectionStatus
    private val _connectionStatus = MutableStateFlow("Preview: Connected")
    override val connectionStatus: StateFlow<String> get() = _connectionStatus // Expose as StateFlow

    // Backing property for receivedData
    private val _receivedData = MutableStateFlow("Preview: No data")
    override val receivedData: StateFlow<String> get() = _receivedData

    // Backing property for operationLog
    private val _operationLog = MutableStateFlow(StringBuilder("Preview: Log initialized\n"))
    override val operationLog: StateFlow<StringBuilder> get() = _operationLog

    // Backing property for isLightsLoopUiActive
    private val _isLightsLoopUiActive = MutableStateFlow(false)
    override val isLightsLoopUiActive: StateFlow<Boolean> get() = _isLightsLoopUiActive

    override fun startBleScan() {
        Log.d("FakeBleViewModel", "Preview: startBleScan() called")
        _operationLog.value.append("Preview: BLE Scan Started\n")
        // To update the StringBuilder correctly, you might need to do:
        // _operationLog.value = StringBuilder(_operationLog.value).append("Preview: BLE Scan Started\n")
        // Or ensure your UI recomposes when the StringBuilder's content changes,
        // which might not happen automatically just by calling .append().
        // A common pattern is to make the StateFlow hold a new StringBuilder instance on change,
        // or a simple String if the log doesn't need to be a StringBuilder publicly.
    }

    override fun stopBleScan() {
        Log.d("FakeBleViewModel", "Preview: stopBleScan() called")
        _operationLog.value.append("Preview: BLE Scan Stopped\n")
    }

    override fun disconnectDevice() {
        Log.d("FakeBleViewModel", "Preview: disconnectDevice() called")
        _connectionStatus.value = "Preview: Disconnected" // Now update the value of the MutableStateFlow
        _operationLog.value.append("Preview: Device Disconnected\n")
    }

    override fun sendCustomCommand(command: String) {
        Log.d("FakeBleViewModel", "Preview: sendCustomCommand('$command') called")
        _operationLog.value.append("Preview: Sent command '$command'\n")
        _receivedData.value = "Preview: Echo of '$command'"
    }

    override fun toggleLightsLoop() {
        Log.d("FakeBleViewModel", "Preview: toggleLightsLoop() called")
        val current = _isLightsLoopUiActive.value
        _isLightsLoopUiActive.value = !current
        _operationLog.value.append("Preview: Lights loop toggled to ${!current}\n")
    }

    override fun sendGearSelectedCommand(leftGear: Int, rightGear: Int) {
        Log.d("FakeBleViewModel", "Preview: sendGearSelectedCommand(L:$leftGear, R:$rightGear) called")
        _operationLog.value.append("Preview: Sent gears L:$leftGear, R:$rightGear\n")
    }

    override fun sendTurretAngleSelectedCommand(angle: Int) {
        TODO("Not yet implemented")
    }
}
