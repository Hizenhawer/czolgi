package com.example.czolg3.ble

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
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

// Replace with your ESP32's details
private const val ESP32_DEVICE_NAME = "ESP32_Echo" // Or whatever name your ESP32 advertises
private val ECHO_SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab") // *** REPLACE THIS ***
private val ECHO_CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-ba0987654321") // *** REPLACE THIS ***
private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Configuration Descriptor

@SuppressLint("MissingPermission") // Permissions will be checked before calling these methods
class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "BleViewModel"

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var echoCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionStatus = MutableLiveData<String>("Disconnected")
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _receivedData = MutableLiveData<String>()
    val receivedData: LiveData<String> = _receivedData

    private val _logMessages = MutableLiveData<String>()
    val logMessages: LiveData<String> = _logMessages

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // Stop scanning after 10 seconds

    private fun addLog(message: String) {
        Log.d(TAG, message)
//        _logMessages.postValue("${_logMessages.value ?: ""}\n$message")
    }

    // --- Scanning ---
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.i("BleViewModel_ScanCb", "onScanResult CALLED. Device: ${result?.device?.address}")
            // Temporarily comment out all other logic in here
            // addLog("Scan Result: ${result.device.address}")
            // if (result.device.name == ESP32_DEVICE_NAME) { ... }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.i("BleViewModel_ScanCb", "onBatchScanResults CALLED. Count: ${results?.size}")
            // Temporarily comment out all other logic
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BleViewModel_ScanCb", "onScanFailed CALLED. ErrorCode: $errorCode")
            // Temporarily comment out all other logic
            // addLog("BLE Scan Failed: $errorCode")
            // _connectionStatus.postValue("Scan Failed")
            // isScanning = false
        }
    }

    fun startScan() {
        if (!hasRequiredBluetoothPermissions()) {
            addLog("Bluetooth permissions not granted.")
            _connectionStatus.postValue("Permissions missing")
            return
        }
        if (bluetoothLeScanner == null) {
            addLog("ERROR: bluetoothLeScanner is null. Cannot start scan.")
            _connectionStatus.postValue("BLE Scanner Error")
            return
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            addLog("Bluetooth is not enabled.")
            _connectionStatus.postValue("Bluetooth off")
            // You might want to trigger a request to enable Bluetooth here
            return
        }
        val context = getApplication<Application>().applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "BLUETOOTH_SCAN granted: ${ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "BLUETOOTH_CONNECT granted: ${ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED}")
        } else {
            Log.d(TAG, "ACCESS_FINE_LOCATION granted: ${ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "BLUETOOTH granted: ${ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "BLUETOOTH_ADMIN granted: ${ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED}")
        }

        if (!hasRequiredBluetoothPermissions()) { // Ensure this method accurately reflects the checks above
            addLog("Bluetooth permissions not granted AT THE MOMENT OF SCAN START.")
            _connectionStatus.postValue("Permissions missing")
            return
        }

        if (isScanning) {
            addLog("Already scanning.")
            return
        }

        addLog("Starting BLE scan...")
        _connectionStatus.postValue("Scanning...")
        isScanning = true

        // Stop scanning after a defined period.
        scanHandler.postDelayed({
            if (isScanning) {
                addLog("Scan timeout.")
                stopScan()
                _connectionStatus.postValue("Device not found")
            }
        }, SCAN_PERIOD)

        val filters: MutableList<ScanFilter> = ArrayList()
        // You can filter by name if your device advertises it, or by service UUID
//        val scanFilter = ScanFilter.Builder()
//             .setDeviceName(ESP32_DEVICE_NAME) // More reliable to connect by name/address after discovery
//            .setServiceUuid(ParcelUuid(ECHO_SERVICE_UUID)) // Filter by your service if ESP32 advertises it
//            .build()
//         filters.add(scanFilter) // Uncomment if your ESP32 advertises the service UUID

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // If no filters, scan for all devices and then filter by name in onScanResult
//        bluetoothLeScanner?.startScan(if (filters.isEmpty()) null else filters, settings, scanCallback)
        bluetoothLeScanner.startScan(scanCallback)
        addLog("Scanning with NO filters (temporary for debugging)")
    }

    fun stopScan() {
        if (isScanning && bluetoothAdapter != null && bluetoothAdapter.isEnabled && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            bluetoothLeScanner?.stopScan(scanCallback)
        }
        isScanning = false
        addLog("Scan stopped.")
    }

    // --- Connection ---
    private fun connectToDevice(device: BluetoothDevice) {
        addLog("Connecting to ${device.address}...")
        _connectionStatus.postValue("Connecting...")
        // Ensure GATT is closed if already connected to another device or for retry
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(getApplication(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    addLog("Connected to $deviceAddress")
                    _connectionStatus.postValue("Connected")
                    bluetoothGatt = gatt // Store the GATT instance
                    // Discover services after successful connection
                    uiScope.launch { // Use coroutine for delay
                        delay(600) // Short delay sometimes helps service discovery on some devices
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    addLog("Disconnected from $deviceAddress")
                    _connectionStatus.postValue("Disconnected")
                    gatt.close()
                    bluetoothGatt = null
                    echoCharacteristic = null
                }
            } else {
                addLog("Connection Error for $deviceAddress: $status")
                _connectionStatus.postValue("Connection Error: $status")
                gatt.close()
                bluetoothGatt = null
                echoCharacteristic = null
                // Consider attempting a reconnect here or notifying the user
                // startScan() // Optionally try to rescan
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Services Discovered for ${gatt.device.address}")
                val service = gatt.getService(ECHO_SERVICE_UUID)
                if (service == null) {
                    addLog("Echo Service not found!")
                    _connectionStatus.postValue("Service Not Found")
                    return
                }
                echoCharacteristic = service.getCharacteristic(ECHO_CHARACTERISTIC_UUID)
                if (echoCharacteristic == null) {
                    addLog("Echo Characteristic not found!")
                    _connectionStatus.postValue("Characteristic Not Found")
                    return
                }
                addLog("Echo Characteristic found. Ready to communicate.")
                _connectionStatus.postValue("Ready")
                enableNotifications(gatt, echoCharacteristic!!) // Enable notifications for echo
            } else {
                addLog("Service discovery failed with status: $status")
                _connectionStatus.postValue("Service Discovery Failed")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == ECHO_CHARACTERISTIC_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    addLog("Write successful: ${String(characteristic.value, Charsets.UTF_8)}")
                } else {
                    addLog("Write failed: $status")
                }
            }
        }

        // Called when data is received via notification (echoed back)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == ECHO_CHARACTERISTIC_UUID) {
                val receivedValue = String(value, Charsets.UTF_8)
                addLog("Received from ESP32: $receivedValue")
                _receivedData.postValue(receivedValue) // Update LiveData for UI
            }
        }
        // For Android 12 and below
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == ECHO_CHARACTERISTIC_UUID) {
                    val receivedValue = String(characteristic.value, Charsets.UTF_8)
                    addLog("Received from ESP32 (legacy): $receivedValue")
                    _receivedData.postValue(receivedValue) // Update LiveData for UI
                }
            }
        }


        // For reading characteristics (if your echo service requires explicit read after write)
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int ) {
            if (characteristic.uuid == ECHO_CHARACTERISTIC_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val receivedValue = String(value, Charsets.UTF_8)
                    addLog("Read from ESP32: $receivedValue")
                    _receivedData.postValue(receivedValue)
                } else {
                    addLog("Read failed: $status")
                }
            }
        }
        // For Android 12 and below
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == ECHO_CHARACTERISTIC_UUID) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val receivedValue = String(characteristic.value, Charsets.UTF_8)
                        addLog("Read from ESP32 (legacy): $receivedValue")
                        _receivedData.postValue(receivedValue)
                    } else {
                        addLog("Read failed (legacy): $status")
                    }
                }
            }
        }


        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Notification descriptor written successfully.")
            } else {
                addLog("Failed to write notification descriptor: $status")
            }
        }
    }

    // --- Sending Data ---
    fun sendText(text: String) {
        if (bluetoothGatt == null || echoCharacteristic == null) {
            addLog("Not connected or characteristic not found.")
            _connectionStatus.postValue("Not Ready to Send")
            return
        }

        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            addLog("BLUETOOTH_CONNECT permission not granted for sendText")
            return
        }

        val data = text.toByteArray(Charsets.UTF_8)

        // For Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                echoCharacteristic!!,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // Or WRITE_TYPE_NO_RESPONSE if your ESP32 doesn't send a write ack
            )
        } else {
            // For older versions
            echoCharacteristic!!.value = data
            echoCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            bluetoothGatt?.writeCharacteristic(echoCharacteristic!!)
        }
        addLog("Sent to ESP32: $text")
    }

    // --- Notifications ---
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            addLog("BLUETOOTH_CONNECT permission not granted for enableNotifications")
            return
        }

        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            addLog("CCCD not found for echo characteristic!")
            return
        }

        if (gatt.setCharacteristicNotification(characteristic, true)) {
            // For Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                // For older versions
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }
            addLog("Notifications enabled for echo characteristic.")
        } else {
            addLog("Failed to enable notifications.")
        }
    }


    // --- Permissions ---
    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRequiredBluetoothPermissions(): Boolean {
        val context = getApplication<Application>().applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else { // Android 11 and below
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }


    fun disconnect() {
        addLog("Disconnecting...")
        bluetoothGatt?.disconnect() // This will trigger onConnectionStateChange to STATE_DISCONNECTED where close() is called.
        // bluetoothGatt?.close() // Don't call close() directly here, let the callback handle it.
        bluetoothGatt = null
        echoCharacteristic = null
        _connectionStatus.postValue("Disconnected")
    }

    override fun onCleared() {
        super.onCleared()
        addLog("ViewModel cleared. Disconnecting GATT.")
        disconnect()
        viewModelJob.cancel()
        scanHandler.removeCallbacksAndMessages(null) // Clean up handler
    }
}