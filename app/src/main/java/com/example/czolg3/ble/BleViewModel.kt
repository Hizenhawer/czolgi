package com.example.czolg3.ble // Adjust package name as needed

// LiveData related imports can be removed if you convert all of them
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs

interface IBleViewModel {
    val connectionStatus: StateFlow<String>
    val receivedData: StateFlow<String>
    val operationLog: StateFlow<StringBuilder>
    val isLightsLoopUiActive: StateFlow<Boolean>
    fun startBleScan()
    fun stopBleScan()
    fun disconnectDevice()
    fun sendCustomCommand(command: String)
    fun toggleLightsLoop()
    fun sendGearSelectedCommand(leftGear: Int, rightGear: Int)
    fun sendTurretAngleSelectedCommand(angle: Int)
}

class BleViewModel(application: Application) : AndroidViewModel(application), IBleViewModel {
    private val TAG = "BleViewModel"

    private val bleManager = BleManager(application)
    private val commandLoopController: CommandLoopController

    private val viewModelUIScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _connectionStatus = MutableStateFlow("Disconnected")
    override val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // Consider converting these to StateFlow as well for consistency
    private val _receivedData = MutableStateFlow("") // Changed from MutableLiveData
    override val receivedData: StateFlow<String> = _receivedData.asStateFlow() // Changed from LiveData

    private val _operationLog = MutableStateFlow(StringBuilder("Logs:\n")) // Changed
    override val operationLog: StateFlow<StringBuilder> = _operationLog.asStateFlow() // Changed

    private val _isLightsLoopUiActive = MutableStateFlow(false) // Changed
    override val isLightsLoopUiActive: StateFlow<Boolean> = _isLightsLoopUiActive.asStateFlow() // Changed


    private var targetDevice: BluetoothDevice? = null
    private var identifiedEchoCharacteristic: BluetoothGattCharacteristic? = null

    init {
        commandLoopController = CommandLoopController(
            scope = viewModelScope,
            commandSender = { command ->
                val success = bleManager.writeCharacteristic(
                    BleConstants.ECHO_SERVICE_UUID,
                    BleConstants.ECHO_CHARACTERISTIC_UUID,
                    command.toByteArray(Charsets.UTF_8)
                )
                success
            },
            logUpdater = { message -> addLog("[LoopCtrl] $message") }
        )
        observeBleEvents()
    }

    private fun addLog(message: String, level: Int = Log.INFO) {
        when (level) {
            Log.DEBUG -> Log.d(TAG, message)
            Log.INFO -> Log.i(TAG, message)
            Log.WARN -> Log.w(TAG, message)
            Log.ERROR -> Log.e(TAG, message)
            else -> Log.v(TAG, message)
        }

        // Update StateFlow for UI display
        // StateFlow updates can be done from any thread, but if UI observes directly, it should be on main.
        // viewModelUIScope.launch ensures this runs on the Main thread.
        viewModelUIScope.launch {
            val currentLogData = _operationLog.value
            if (currentLogData.lines().size > 200) {
                val lines = currentLogData.toString().lines()
                val newLog = StringBuilder("Logs:\n") // Start fresh for clarity
                lines.drop(lines.size - 100).forEach { newLog.append(it).append("\n") }
                _operationLog.value = newLog.append(message).append("\n")
            } else {
                // Create a new StringBuilder instance to ensure StateFlow detects the change
                _operationLog.value = StringBuilder(currentLogData).append(message).append("\n")
            }
        }
    }

    private fun observeBleEvents() {
        bleManager.bleEvents
            .onEach { event ->
                when (event) {
                    is BleEvent.LogMessage -> {
                        addLog("[BleMgr] ${event.message}", event.level)
                    }

                    is BleEvent.ScanResultFound -> {
                        if (targetDevice == null && event.deviceName == BleConstants.ESP32_DEVICE_NAME) {
                            addLog("Target ESP32 found: ${event.deviceName} (${event.device.address}) RSSI: ${event.rssi}")
                            bleManager.stopScan()
                            targetDevice = event.device
                            _connectionStatus.value = "Device Found, Connecting..." // Changed
                            addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)
                            bleManager.connect(event.device)
                        } else if (event.deviceName == BleConstants.ESP32_DEVICE_NAME) {
                            addLog(
                                "Target ESP32 (${event.deviceName}) found again, already have a target or connected.",
                                Log.DEBUG
                            )
                        }
                    }

                    is BleEvent.ScanFailed -> {
                        addLog("Scan Failed: ${event.errorCode}", Log.ERROR)
                        _connectionStatus.value = "Scan Failed (${event.errorCode})" // Changed
                        addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)
                    }

                    is BleEvent.ConnectionStateChanged -> {
                        addLog("Connection Status: ${event.statusMessage} for ${event.deviceAddress} (GATT: ${event.gattStatusCode}, Profile: ${event.bleProfileState})")
                        _connectionStatus.value = event.statusMessage // Changed

                        if (event.statusMessage != "Connected" && event.statusMessage != "Ready") { // Adjusted condition slightly
                            if (_isLightsLoopUiActive.value) { // Check if loop was active
                                commandLoopController.stopLightsCommandLoop()
                                _isLightsLoopUiActive.value = false // Changed
                            }
                            targetDevice = null
                            identifiedEchoCharacteristic = null
                        }
                    }

                    is BleEvent.ServicesDiscovered -> {
                        addLog("Services Discovered. Looking for ECHO service...")

                        if (!_isLightsLoopUiActive.value) {
                            toggleLightsLoop()
                        }
                    }

                    is BleEvent.ServiceDiscoveryFailed -> {
                        addLog(
                            "Service Discovery Failed (GATT Status: ${event.gattStatusCode})",
                            Log.ERROR
                        )
                        _connectionStatus.value = "Service Discovery Failed" // Changed
                        addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)
                        bleManager.disconnect()
                    }

                    is BleEvent.CharacteristicChanged -> {
                        if (event.characteristicUuid == BleConstants.ECHO_CHARACTERISTIC_UUID) {
                            val value = event.value.decodeToString()
                            addLog("Data received from ESP32 (Echo): $value")
                            _receivedData.value = value // Changed
                        }
                    }

                    is BleEvent.CharacteristicWritten -> {
                        if (event.characteristicUuid == BleConstants.ECHO_CHARACTERISTIC_UUID) {
                            if (event.gattStatusCode != BluetoothGatt.GATT_SUCCESS) {
                                addLog(
                                    "Write to ECHO characteristic failed. Status: ${event.gattStatusCode}",
                                    Log.WARN
                                )
                            }
                        }
                    }

                    is BleEvent.DescriptorWritten -> {
                        if (event.descriptorUuid == BleConstants.CCCD_UUID) {
                            if (event.gattStatusCode == BluetoothGatt.GATT_SUCCESS) {
                                addLog("Notification descriptor for ECHO written successfully.")
                                _connectionStatus.value = "Ready" // Changed
                            } else {
                                addLog(
                                    "Failed to write notification descriptor for ECHO. Status: ${event.gattStatusCode}",
                                    Log.WARN
                                )
                                _connectionStatus.value = "Notification Setup Failed" // Changed
                            }
                            addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)
                        }
                    }

                    else -> {
                        // addLog("Unhandled BleEvent: $event", Log.DEBUG)
                    }
                }
            }
            .catch { e ->
                addLog("Error in BLE event observer: ${e.message}", Log.ERROR)
                _connectionStatus.value = "Error" // Changed
                addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)
                commandLoopController.stopLightsCommandLoop()
                _isLightsLoopUiActive.value = false // Changed
            }
            .launchIn(viewModelScope)
    }

    override fun startBleScan() {
        addLog("Scan requested from UI.")
        _connectionStatus.value = "Scanning..." // Changed
        addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)

        val filters: MutableList<ScanFilter> = ArrayList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleManager.startScan(if (filters.isEmpty()) null else filters, settings)
    }

    override fun stopBleScan() {
        addLog("Stop scan requested from UI.")
        bleManager.stopScan()
        // If not already "Scanning...", don't change status, let ScanFailed handle it if needed
        if (_connectionStatus.value == "Scanning...") {
            _connectionStatus.value =
                "Scan Stopped" // Or revert to "Disconnected" or previous state
            addLog("Connection Status: ${_connectionStatus.value}", Log.DEBUG)
        }
    }

    override fun disconnectDevice() {
        addLog("Disconnect requested from UI.")
        commandLoopController.stopLightsCommandLoop()
        _isLightsLoopUiActive.value = false // Changed
        bleManager.disconnect()
    }

    override fun sendCustomCommand(command: String) {
        if (_connectionStatus.value == "Ready" || _connectionStatus.value == "Connected") {
            addLog("Sending custom command: $command")
            val success = bleManager.writeCharacteristic(
                BleConstants.ECHO_SERVICE_UUID,
                BleConstants.ECHO_CHARACTERISTIC_UUID,
                command.toByteArray(Charsets.UTF_8)
            )
            if (!success) {
                addLog("Failed to initiate custom command send.", Log.WARN)
            }
        } else {
            addLog(
                "Cannot send custom command: Not connected or ready. Status: ${_connectionStatus.value}",
                Log.WARN
            )
        }
    }

    override fun toggleLightsLoop() {
        addLog("Toggle lights loop requested.", Log.INFO)
        if (_isLightsLoopUiActive.value) { // Reading StateFlow value
            commandLoopController.stopLightsCommandLoop()
            _isLightsLoopUiActive.value = false // Setting StateFlow value
        } else {
            if (_connectionStatus.value == "Ready" || _connectionStatus.value == "Connected") {
                commandLoopController.startLightsCommandLoop()
                _isLightsLoopUiActive.value = true // Setting StateFlow value
            } else {
                addLog(
                    "Cannot start lights loop: Device not connected or ready. Status: ${_connectionStatus.value}",
                    Log.WARN
                )
            }
        }
    }

    private val gearToSpeedMap = mapOf(
        0 to 0,   // Stop
        1 to 80,  // Slow Forward
        2 to 125, // Medium Forward
        3 to 255, // Fast Forward
        -1 to -80, // Slow Reverse
        -2 to -125,// Medium Reverse
        -3 to -255 // Fast Reverse
    )

    override fun sendGearSelectedCommand(leftGear: Int, rightGear: Int) {
        val leftTrackSpeedValue = gearToSpeedMap[leftGear] ?: 0 // Default to 0 if gear not in map
        val rightTrackSpeedValue = gearToSpeedMap[rightGear] ?: 0

        addLog(
            "Sending gear selected command. Left Gear: $leftGear -> Speed Value: $leftTrackSpeedValue, Right Gear: $rightGear -> Speed Value: $rightTrackSpeedValue",
            Log.INFO
        )

        val commandString = "TRACKS:$leftTrackSpeedValue:$rightTrackSpeedValue"
        addLog("Command to send: $commandString", Log.DEBUG)

        sendCommand(commandString)
    }

    override fun sendTurretAngleSelectedCommand(angle: Int) {
        val speed = gearToSpeedMap[abs(angle)] ?: 0
        val direction = if (angle > 0) 'e' else 'q'
        val commandString = "TURRET:$direction:$speed"
        addLog("Command to send: $commandString", Log.DEBUG)

        sendCommand(commandString)
    }

    private fun sendCommand(command: String) {
        val success = bleManager.writeCharacteristic(
            BleConstants.ECHO_SERVICE_UUID,
            BleConstants.ECHO_CHARACTERISTIC_UUID,
            command.toByteArray(Charsets.UTF_8)
        )
        if (!success) {
            addLog("Failed to send $command.", Log.WARN)
        } else {
            addLog("$command sent.", Log.INFO)
        }
        TODO("Add queue")
    }

    override fun onCleared() {
        super.onCleared()
        addLog("BleViewModel cleared. Cleaning up resources.", Log.DEBUG)
        commandLoopController.cleanup()
        bleManager.cleanup()
        viewModelScope.coroutineContext.cancelChildren()
        viewModelUIScope.coroutineContext.cancelChildren()
        addLog("BleViewModel onCleared finished.", Log.DEBUG)
    }
}
