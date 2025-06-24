package com.example.czolg3.ble // Adjust package name as needed

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "BleViewModel"

    private val bleManager = BleManager(application)
    private val commandLoopController: CommandLoopController

    // ViewModel's own scope for UI related coroutines or tasks that need ViewModel's lifecycle
    private val viewModelUIScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _connectionStatus = MutableLiveData<String>("Disconnected")
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _receivedData = MutableLiveData<String>() // For data from ESP32 notifications/reads
    val receivedData: LiveData<String> = _receivedData

    private val _operationLog = MutableLiveData<StringBuilder>(StringBuilder("Logs:\n"))
    val operationLog: LiveData<StringBuilder> = _operationLog

    // LiveData to expose loop active state to UI
    private val _isLightsLoopUiActive = MutableLiveData<Boolean>(false)
    val isLightsLoopUiActive: LiveData<Boolean> = _isLightsLoopUiActive

    private var targetDevice: BluetoothDevice? = null
    // Storing the characteristic if specific properties need to be checked often,
    // otherwise, BleManager handles it.
    private var identifiedEchoCharacteristic: BluetoothGattCharacteristic? = null

    init {
        commandLoopController = CommandLoopController(
            scope = viewModelScope, // Use viewModelScope for the command loop's lifecycle
            commandSender = { command ->
                // This suspend lambda will be called by CommandLoopController
                // It's a suspending function if BleManager.writeCharacteristic is suspend,
                // or returns a boolean indicating if the write was successfully initiated.
                // For simplicity, assuming writeCharacteristic is effectively synchronous or handles its own async nature.
                val success = bleManager.writeCharacteristic(
                    BleConstants.ECHO_SERVICE_UUID,
                    BleConstants.ECHO_CHARACTERISTIC_UUID,
                    command.toByteArray(Charsets.UTF_8)
                    // Consider write type if needed, e.g., BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
                // Return true if the write was initiated, false otherwise.
                // The CommandLoopController uses this to decide if it should stop.
                success
            },
            logUpdater = { message -> addLog("[LoopCtrl] $message") }
        )
        observeBleEvents()
    }

    private fun addLog(message: String, level: Int = Log.INFO) {
        // Log to Android's Logcat
        when(level) {
            Log.DEBUG -> Log.d(TAG, message)
            Log.INFO -> Log.i(TAG, message)
            Log.WARN -> Log.w(TAG, message)
            Log.ERROR -> Log.e(TAG, message)
            else -> Log.v(TAG, message) // Default to verbose
        }

        // Update LiveData for UI display, ensuring it's on the main thread
        viewModelUIScope.launch {
            val currentLog = _operationLog.value ?: StringBuilder("Logs:\n")
            // Simple log length limiting for the UI
            if (currentLog.lines().size > 200) {
                val lines = currentLog.toString().lines()
                currentLog.clear()
                // Keep the last 100 lines
                lines.drop(lines.size - 100).forEach { currentLog.append(it).append("\n") }
            }
            currentLog.append(message).append("\n")
            _operationLog.setValue(currentLog) // Use setValue as we are on the Main thread (viewModelUIScope)
        }
    }

    private fun observeBleEvents() {
        bleManager.bleEvents
            .onEach { event ->
                // Process events, ensuring UI updates are on the main thread
                // viewModelUIScope.launch { // Already launched in this scope
                when (event) {
                    is BleEvent.LogMessage -> {
                        addLog("[BleMgr] ${event.message}", event.level)
                    }
                    is BleEvent.ScanResultFound -> {
                        // Example: Only connect to the first found target device matching the name
                        if (targetDevice == null && event.deviceName == BleConstants.ESP32_DEVICE_NAME) {
                            addLog("Target ESP32 found: ${event.deviceName} (${event.device.address}) RSSI: ${event.rssi}")
                            bleManager.stopScan() // Stop scan once target is identified
                            targetDevice = event.device // Store the device
                            _connectionStatus.postValue("Device Found, Connecting...")
                            bleManager.connect(event.device)
                        } else if (event.deviceName == BleConstants.ESP32_DEVICE_NAME) {
                            // Log if target found again but already processing one or connected
                            addLog("Target ESP32 (${event.deviceName}) found again, already have a target or connected.", Log.DEBUG)
                        }
                    }
                    is BleEvent.ScanFailed -> {
                        addLog("Scan Failed: ${event.errorCode}", Log.ERROR)
                        _connectionStatus.postValue("Scan Failed (${event.errorCode})")
                    }
                    is BleEvent.ConnectionStateChanged -> {
                        addLog("Connection Status: ${event.statusMessage} for ${event.deviceAddress} (GATT: ${event.gattStatusCode}, Profile: ${event.bleProfileState})")
                        _connectionStatus.postValue(event.statusMessage) // e.g., "Connected", "Disconnected", "Connection Error"

                        if (event.statusMessage != "Connected") {
                            // If disconnected or connection error, stop any active loops and clear device info
                            if (_isLightsLoopUiActive.value == true) { // Check if loop was active
                                commandLoopController.stopLightsCommandLoop()
                                _isLightsLoopUiActive.postValue(false) // Update UI state
                            }
                            targetDevice = null
                            identifiedEchoCharacteristic = null
                        }
                    }
                    is BleEvent.ServicesDiscovered -> {
                        addLog("Services Discovered. Looking for ECHO service...")
                        // After services are discovered, you need to find your specific service and characteristic.
                        // This logic could also be in BleManager which then emits a more specific event
                        // if it had the UUIDs. For now, ViewModel does it.

                        // Attempt to get the GATT instance to find service/characteristic
                        // This part is tricky as BleManager encapsulates gatt.
                        // A better approach: BleManager emits the characteristic if found, or an event if not.
                        // For this example, we assume ViewModel would need to ask BleManager to find it.
                        // OR, if BleManager emits a generic ServicesDiscovered, ViewModel iterates.
                        // Let's assume for now, we'd need a way to get the characteristic.
                        // For simplicity, we'll proceed as if we can get it or BleManager confirmed it.

                        // Hypothetically, if BleManager confirmed characteristic readiness:
                        // identifiedEchoCharacteristic = theCharacteristicFromBleManagerEvent;

                        // For now, let's proceed to enable notifications and start loop as if char is ready.
                        // In a real scenario, BleManager would emit an event like "EchoCharacteristicReady"
                        _connectionStatus.postValue("Services Ready, Initializing...")

                        // Enable notifications for the ECHO characteristic
                        val successNotifications = bleManager.enableNotifications(
                            BleConstants.ECHO_SERVICE_UUID,
                            BleConstants.ECHO_CHARACTERISTIC_UUID
                        )
                        if (successNotifications) {
                            addLog("Notifications enabling initiated for ECHO characteristic.")
                        } else {
                            addLog("Failed to initiate notifications for ECHO characteristic.", Log.WARN)
                            // Consider disconnecting or other error handling
                        }

                        // Start the command loop if not already active
                        if (_isLightsLoopUiActive.value == false) {
                            toggleLightsLoop() // This will check connection status again
                        }
                    }
                    is BleEvent.ServiceDiscoveryFailed -> {
                        addLog("Service Discovery Failed (GATT Status: ${event.gattStatusCode})", Log.ERROR)
                        _connectionStatus.postValue("Service Discovery Failed")
                        bleManager.disconnect() // Disconnect if services can't be resolved
                    }
                    is BleEvent.CharacteristicChanged -> {
                        if (event.characteristicUuid == BleConstants.ECHO_CHARACTERISTIC_UUID) {
                            val value = event.value.decodeToString()
                            addLog("Data received from ESP32 (Echo): $value")
                            _receivedData.postValue(value)
                        }
                    }
                    is BleEvent.CharacteristicWritten -> {
                        if (event.characteristicUuid == BleConstants.ECHO_CHARACTERISTIC_UUID) {
                            if (event.gattStatusCode == BluetoothGatt.GATT_SUCCESS) {
                                // addLog("Write to ECHO characteristic successful.", Log.DEBUG)
                            } else {
                                addLog("Write to ECHO characteristic failed. Status: ${event.gattStatusCode}", Log.WARN)
                                // If a write fails within the loop, the loop controller should handle stopping.
                            }
                        }
                    }
                    is BleEvent.DescriptorWritten -> {
                        if (event.descriptorUuid == BleConstants.CCCD_UUID) {
                            if (event.gattStatusCode == BluetoothGatt.GATT_SUCCESS) {
                                addLog("Notification descriptor for ECHO written successfully.")
                                _connectionStatus.postValue("Ready") // Device is fully ready now
                            } else {
                                addLog("Failed to write notification descriptor for ECHO. Status: ${event.gattStatusCode}", Log.WARN)
                                _connectionStatus.postValue("Notification Setup Failed")
                            }
                        }
                    }
                    // Handle other specific events like CharacteristicRead if used
                    else -> {
                        // addLog("Unhandled BleEvent: $event", Log.DEBUG)
                    }
                }
                // }
            }
            .catch { e ->
                // Handle any exceptions from the flow collection itself
                addLog("Error in BLE event observer: ${e.message}", Log.ERROR)
                _connectionStatus.postValue("Error")
                commandLoopController.stopLightsCommandLoop()
                _isLightsLoopUiActive.postValue(false)

            }
            .launchIn(viewModelScope) // Use viewModelScope for automatic cancellation
    }

    fun startBleScan() {
        addLog("Scan requested from UI.")
        // Optionally, check for Bluetooth adapter status or permissions here again,
        // though BleManager also checks.
        _connectionStatus.postValue("Scanning...")

        val filters: MutableList<ScanFilter> = ArrayList()
        // Example: Filter by device name. ESP32 must advertise this name.
        // val scanFilter = ScanFilter.Builder()
        // .setDeviceName(BleConstants.ESP32_DEVICE_NAME)
        // .build()
        // filters.add(scanFilter)
        // If no filters are added, BleManager will scan for all devices.
        // The ViewModel then filters by name in ScanResultFound.

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use for active scanning
            // .setReportDelay(0) // Report results immediately (default)
            .build()

        bleManager.startScan(if (filters.isEmpty()) null else filters, settings)
    }

    fun stopBleScan() {
        addLog("Stop scan requested from UI.")
        bleManager.stopScan()
        // Connection status will be updated by ScanFailed or timeout event from BleManager if no device was found
    }

    fun disconnectDevice() {
        addLog("Disconnect requested from UI.")
        commandLoopController.stopLightsCommandLoop() // Stop loop before disconnecting
        _isLightsLoopUiActive.postValue(false)
        bleManager.disconnect()
        // Connection status will be updated via BleEvent.ConnectionStateChanged
    }

    // Example function to send a one-off command (not part of the loop)
    fun sendCustomCommand(command: String) {
        if (_connectionStatus.value == "Ready" || _connectionStatus.value == "Connected") { // Or just "Ready" if notifications are essential
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
            addLog("Cannot send custom command: Not connected or ready. Status: ${_connectionStatus.value}", Log.WARN)
        }
    }

    fun toggleLightsLoop() {
        if (_isLightsLoopUiActive.value == true) {
            commandLoopController.stopLightsCommandLoop()
            _isLightsLoopUiActive.postValue(false)
        } else {
            // Only start if connected and services are presumably ready
            if (_connectionStatus.value == "Ready" || _connectionStatus.value == "Connected") { // Or just "Ready"
                commandLoopController.startLightsCommandLoop()
                _isLightsLoopUiActive.postValue(true)
            } else {
                addLog("Cannot start lights loop: Device not connected or ready. Status: ${_connectionStatus.value}", Log.WARN)
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        addLog("BleViewModel cleared. Cleaning up resources.", Log.DEBUG)
        commandLoopController.cleanup() // Stop any active loops
        bleManager.cleanup()         // Disconnect GATT, stop scan, release resources
        viewModelScope.coroutineContext.cancelChildren() // Cancel any coroutines tied to viewModelScope
        viewModelUIScope.coroutineContext.cancelChildren()
        Log.i(TAG, "BleViewModel onCleared finished.")
    }
}