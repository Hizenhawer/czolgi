package com.example.czolg3.ble // Adjust package name as needed

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlin.io.path.name

sealed class BleEvent {
    data class ScanResultFound(val device: BluetoothDevice, val deviceName: String, val rssi: Int) : BleEvent()
    data class ConnectionStateChanged(val deviceAddress: String, val statusMessage: String, val gattStatusCode: Int, val bleProfileState: Int) : BleEvent()
    object ServicesDiscovered : BleEvent() // Can add services list if needed by ViewModel later
    data class ServiceDiscoveryFailed(val gattStatusCode: Int) : BleEvent()
    data class CharacteristicWritten(val characteristicUuid: UUID, val gattStatusCode: Int) : BleEvent()
    data class CharacteristicChanged(val characteristicUuid: UUID, val value: ByteArray) : BleEvent()
    data class CharacteristicRead(val characteristicUuid: UUID, val value: ByteArray, val gattStatusCode: Int) : BleEvent()
    data class DescriptorWritten(val descriptorUuid: UUID, val gattStatusCode: Int) : BleEvent()
    data class LogMessage(val message: String, val level: Int = Log.INFO) : BleEvent()
    data class ScanFailed(val errorCode: Int) : BleEvent()
}

@SuppressLint("MissingPermission") // Permissions checked before use
class BleManager(private val application: Application) {
    private val TAG = "BleManager"

    private val bluetoothManager: BluetoothManager by lazy {
        application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private val bluetoothLeScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    private var bluetoothGatt: BluetoothGatt? = null

    private val _bleEvents = MutableSharedFlow<BleEvent>(replay = 0, extraBufferCapacity = 10) // Allow some buffer
    val bleEvents: SharedFlow<BleEvent> = _bleEvents

    private var isScanning = false
    private val scanTimeoutHandler = Handler(Looper.getMainLooper())
    private val bleOperationScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // For GATT operations

    private fun emitEvent(event: BleEvent) {
        val success = _bleEvents.tryEmit(event)
        if (!success && event is BleEvent.LogMessage) {
            Log.w(TAG, "Failed to emit log event (buffer full?): ${event.message}")
        } else if (!success) {
            Log.w(TAG, "Failed to emit BLE event (buffer full?): $event")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        emitEvent(BleEvent.LogMessage("Connected to $deviceAddress"))
                        emitEvent(BleEvent.ConnectionStateChanged(deviceAddress, "Connected", status, newState))
                        bleOperationScope.launch { // Discover services on a worker thread
                            val discoveryInitiated = gatt.discoverServices()
                            if (!discoveryInitiated) {
                                emitEvent(BleEvent.LogMessage("Failed to initiate service discovery for $deviceAddress", Log.ERROR))
                                // Consider disconnecting or emitting a specific error event
                            } else {
                                emitEvent(BleEvent.LogMessage("Service discovery initiated for $deviceAddress"))
                            }
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        emitEvent(BleEvent.LogMessage("Disconnected from $deviceAddress"))
                        emitEvent(BleEvent.ConnectionStateChanged(deviceAddress, "Disconnected", status, newState))
                        gatt.close()
                        if (bluetoothGatt == gatt) bluetoothGatt = null // Clear only if it's the current GATT
                    }
                }
            } else { // Connection attempt failed or unexpected disconnect
                emitEvent(BleEvent.LogMessage("Connection state error for $deviceAddress. GATT Status: $status, New State: $newState", Log.ERROR))
                emitEvent(BleEvent.ConnectionStateChanged(deviceAddress, "Connection Error", status, newState))
                gatt.close()
                if (bluetoothGatt == gatt) bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                emitEvent(BleEvent.LogMessage("Services discovered for ${gatt.device.address}"))
                // ViewModel will iterate services if needed, or ask BleManager to find specific ones
                emitEvent(BleEvent.ServicesDiscovered)
            } else {
                emitEvent(BleEvent.LogMessage("Service discovery failed for ${gatt.device.address} with status: $status", Log.ERROR))
                emitEvent(BleEvent.ServiceDiscoveryFailed(status))
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            emitEvent(BleEvent.CharacteristicWritten(characteristic.uuid, status))
            val message = if (status == BluetoothGatt.GATT_SUCCESS) "Write successful to ${characteristic.uuid}" else "Write failed to ${characteristic.uuid}, status: $status"
            emitEvent(BleEvent.LogMessage(message, if (status == BluetoothGatt.GATT_SUCCESS) Log.INFO else Log.WARN))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            // This callback is for Android 13 (API 33) and above.
            emitEvent(BleEvent.CharacteristicChanged(characteristic.uuid, value.clone())) // Clone for safety
            emitEvent(BleEvent.LogMessage("Characteristic ${characteristic.uuid} changed: ${value.decodeToString()}"))
        }

        // Deprecated callback for Android 12 (API 32) and below.
        @Deprecated("Used for Android 12 and below")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                emitEvent(BleEvent.CharacteristicChanged(characteristic.uuid, characteristic.value.clone()))
                @Suppress("DEPRECATION")
                emitEvent(BleEvent.LogMessage("Characteristic ${characteristic.uuid} changed (legacy): ${characteristic.value.decodeToString()}"))
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            // This callback is for Android 13 (API 33) and above.
            emitEvent(BleEvent.CharacteristicRead(characteristic.uuid, value.clone(), status))
            val message = if (status == BluetoothGatt.GATT_SUCCESS) "Read successful from ${characteristic.uuid}: ${value.decodeToString()}" else "Read failed from ${characteristic.uuid}, status: $status"
            emitEvent(BleEvent.LogMessage(message, if (status == BluetoothGatt.GATT_SUCCESS) Log.INFO else Log.WARN))
        }

        // Deprecated callback for Android 12 (API 32) and below.
        @Deprecated("Used for Android 12 and below")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                emitEvent(BleEvent.CharacteristicRead(characteristic.uuid, characteristic.value.clone(), status))
                @Suppress("DEPRECATION")
                val message = if (status == BluetoothGatt.GATT_SUCCESS) "Read successful from ${characteristic.uuid} (legacy): ${characteristic.value.decodeToString()}" else "Read failed from ${characteristic.uuid} (legacy), status: $status"
                emitEvent(BleEvent.LogMessage(message, if (status == BluetoothGatt.GATT_SUCCESS) Log.INFO else Log.WARN))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            emitEvent(BleEvent.DescriptorWritten(descriptor.uuid, status))
            val message = if (status == BluetoothGatt.GATT_SUCCESS) "Descriptor ${descriptor.uuid} written successfully" else "Failed to write descriptor ${descriptor.uuid}, status: $status"
            emitEvent(BleEvent.LogMessage(message, if (status == BluetoothGatt.GATT_SUCCESS) Log.INFO else Log.WARN))
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: "Unnamed Device"
            Log.v(TAG, "ScanResult: ${result.device.address} - $deviceName, RSSI: ${result.rssi}") // Verbose
            emitEvent(BleEvent.ScanResultFound(result.device, deviceName, result.rssi))
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                val deviceName = result.device.name ?: "Unnamed Device"
                Log.v(TAG, "BatchScanResult: ${result.device.address} - $deviceName, RSSI: ${result.rssi}") // Verbose
                emitEvent(BleEvent.ScanResultFound(result.device, deviceName, result.rssi))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = "Scan failed with error code: $errorCode - ${mapScanErrorCode(errorCode)}"
            emitEvent(BleEvent.LogMessage(errorMsg, Log.ERROR))
            emitEvent(BleEvent.ScanFailed(errorCode))
            isScanning = false // Ensure scanning flag is reset
        }
    }

    fun startScan(filters: List<ScanFilter>?, settings: ScanSettings) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            emitEvent(BleEvent.LogMessage("BLUETOOTH_SCAN permission missing for startScan", Log.ERROR))
            return
        }
        val localBluetoothAdapter = bluetoothAdapter // Read into a local variable
        if (localBluetoothAdapter == null || !localBluetoothAdapter.isEnabled) {
            emitEvent(BleEvent.LogMessage("Bluetooth adapter not available or not enabled.", Log.ERROR))
            return
        }
        if (isScanning) {
            emitEvent(BleEvent.LogMessage("Scan already in progress.", Log.WARN))
            return
        }

        emitEvent(BleEvent.LogMessage("Starting BLE scan..."))
        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        isScanning = true
        scanTimeoutHandler.removeCallbacksAndMessages(null) // Clear previous timeouts
        scanTimeoutHandler.postDelayed({
            if (isScanning) {
                emitEvent(BleEvent.LogMessage("Scan timeout reached after ${BleConstants.SCAN_PERIOD_MS / 1000}s."))
                stopScan() // Stop the scan internally
                // ViewModel can decide if this means "Device not found"
            }
        }, BleConstants.SCAN_PERIOD_MS)
    }

    fun stopScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) && isScanning) {
            // If permission was revoked while scanning
            emitEvent(BleEvent.LogMessage("BLUETOOTH_SCAN permission missing, cannot reliably stop scan. Scan might continue.", Log.WARN))
            // isScanning remains true, but no new results should come.
            // This is an edge case.
            return
        }
        if (isScanning && bluetoothAdapter?.isEnabled == true) {
            bluetoothLeScanner?.stopScan(scanCallback)
            emitEvent(BleEvent.LogMessage("Scan stopped."))
        } else if (!isScanning) {
            // emitEvent(BleEvent.LogMessage("Scan not active, no need to stop.", Log.DEBUG)) // Can be noisy
        }
        isScanning = false
        scanTimeoutHandler.removeCallbacksAndMessages(null) // Always clear timeout handler
    }

    fun connect(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            emitEvent(BleEvent.LogMessage("BLUETOOTH_CONNECT permission missing for connect", Log.ERROR))
            return
        }
        val localBluetoothAdapter = bluetoothAdapter
        if (localBluetoothAdapter == null || !localBluetoothAdapter.isEnabled) {
            emitEvent(BleEvent.LogMessage("Bluetooth adapter not available for connect.", Log.ERROR))
            return
        }
        emitEvent(BleEvent.LogMessage("Attempting to connect to ${device.name ?: device.address} (${device.address})"))
        // Close previous GATT client if it exists and is for a different device or needs reset
        if (bluetoothGatt != null) {
            emitEvent(BleEvent.LogMessage("Closing existing GATT connection before new attempt.", Log.DEBUG))
            bluetoothGatt?.close() // This should be safe as we are creating a new one
            bluetoothGatt = null
        }
        bluetoothGatt = device.connectGatt(application, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (bluetoothGatt == null) {
            emitEvent(BleEvent.LogMessage("connectGatt returned null for ${device.address}", Log.ERROR))
        }
    }

    fun disconnect() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) && bluetoothGatt != null) {
            emitEvent(BleEvent.LogMessage("BLUETOOTH_CONNECT permission missing, cannot reliably disconnect. Connection might persist.", Log.WARN))
            return
        }
        if (bluetoothGatt == null) {
            emitEvent(BleEvent.LogMessage("No active GATT connection to disconnect.", Log.DEBUG))
            return
        }
        emitEvent(BleEvent.LogMessage("Requesting GATT disconnect for ${bluetoothGatt?.device?.address}"))
        bluetoothGatt?.disconnect() // Triggers onConnectionStateChange where close() happens
    }

    fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, data: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT): Boolean {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            emitEvent(BleEvent.LogMessage("BLUETOOTH_CONNECT permission missing for writeCharacteristic", Log.ERROR))
            return false
        }
        val gatt = bluetoothGatt ?: run {
            emitEvent(BleEvent.LogMessage("GATT not initialized for write operation.", Log.ERROR))
            return false
        }
        val service = gatt.getService(serviceUUID) ?: run {
            emitEvent(BleEvent.LogMessage("Service $serviceUUID not found for write.", Log.ERROR))
            return false
        }
        val characteristic = service.getCharacteristic(characteristicUUID) ?: run {
            emitEvent(BleEvent.LogMessage("Characteristic $characteristicUUID not found in service $serviceUUID for write.", Log.ERROR))
            return false
        }

        val properties = characteristic.properties
        val supportsWrite = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0
        val supportsWriteNoResponse = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0

        if (!supportsWrite && !supportsWriteNoResponse) {
            emitEvent(BleEvent.LogMessage("Characteristic ${characteristic.uuid} is not writable.", Log.ERROR))
            return false
        }

        // Determine actual write type based on support and request
        val actualWriteType = when {
            writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT && supportsWrite -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE && supportsWriteNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            supportsWrite -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // Fallback if requested isn't best match
            supportsWriteNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> { // Should not happen given the check above
                emitEvent(BleEvent.LogMessage("Characteristic ${characteristic.uuid} does not support any write type.", Log.ERROR))
                return false
            }
        }
        emitEvent(BleEvent.LogMessage("Writing to ${characteristic.uuid} with type $actualWriteType, data: ${data.decodeToString()}", Log.DEBUG))


        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(characteristic, data, actualWriteType)
            // For writeCharacteristic, status code 0 (GATT_SUCCESS) means the operation was initiated.
            // The actual success/failure comes in onCharacteristicWrite.
            // Here, we check if the call itself failed to be initiated.
            if (status != BluetoothGatt.GATT_SUCCESS) { // This means the call to writeCharacteristic itself failed.
                emitEvent(BleEvent.LogMessage("BluetoothGatt.writeCharacteristic call failed with status: $status", Log.ERROR))
                false
            } else {
                true // Call was initiated. Wait for callback.
            }
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            characteristic.writeType = actualWriteType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic) // Returns boolean indicating if operation was initiated
        }
    }

    fun enableNotifications(serviceUUID: UUID, characteristicUUID: UUID, cccdUUID: UUID = BleConstants.CCCD_UUID): Boolean {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            emitEvent(BleEvent.LogMessage("BLUETOOTH_CONNECT permission missing for enableNotifications", Log.ERROR))
            return false
        }
        val gatt = bluetoothGatt ?: run {
            emitEvent(BleEvent.LogMessage("GATT not initialized for enableNotifications.", Log.ERROR))
            return false
        }
        val service = gatt.getService(serviceUUID) ?: run {
            emitEvent(BleEvent.LogMessage("Service $serviceUUID not found for enableNotifications.", Log.ERROR))
            return false
        }
        val characteristic = service.getCharacteristic(characteristicUUID) ?: run {
            emitEvent(BleEvent.LogMessage("Characteristic $characteristicUUID not found for enableNotifications.", Log.ERROR))
            return false
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            emitEvent(BleEvent.LogMessage("Characteristic ${characteristic.uuid} does not support notifications.", Log.ERROR))
            return false
        }
        val cccd = characteristic.getDescriptor(cccdUUID) ?: run {
            emitEvent(BleEvent.LogMessage("CCCD $cccdUUID not found for characteristic ${characteristic.uuid}.", Log.ERROR))
            return false
        }

        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            emitEvent(BleEvent.LogMessage("Failed to execute setCharacteristicNotification for ${characteristic.uuid}.", Log.ERROR))
            return false
        }

        emitEvent(BleEvent.LogMessage("Writing ENABLE_NOTIFICATION_VALUE to CCCD ${cccd.uuid} for characteristic ${characteristic.uuid}", Log.DEBUG))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitEvent(BleEvent.LogMessage("BluetoothGatt.writeDescriptor call failed with status: $status", Log.ERROR))
                false
            } else {
                true // Call initiated, wait for onDescriptorWrite callback
            }
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd) // Returns boolean indicating if operation was initiated
        }
    }

    fun cleanup() {
        emitEvent(BleEvent.LogMessage("BleManager cleanup called.", Log.DEBUG))
        stopScan() // Stop any active scan
        // Disconnect and close GATT if connected
        if (bluetoothGatt != null) {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bluetoothGatt?.disconnect() // This should trigger close in callback
            } else { // If no permission, try to close directly, though it might not be clean
                bluetoothGatt?.close()
            }
            bluetoothGatt = null
        }
        bleOperationScope.coroutineContext.cancelChildren() // Cancel any pending GATT operations
        scanTimeoutHandler.removeCallbacksAndMessages(null)
        Log.i(TAG, "BleManager resources cleaned up.")
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(application, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun mapScanErrorCode(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
            // For API 29+ (ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES, SCAN_FAILED_SCANNING_TOO_FREQUENTLY)
            // Add these if targeting API 29+ specifically for these codes.
            else -> "UNKNOWN_SCAN_ERROR"
        }
    }
}