package com.example.czolg3

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.observe
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.czolg3.databinding.ActivityMainBinding // Assuming you have this binding class
import com.example.czolg3.ble.BleViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // Activity-scoped ViewModel. Fragments within this Activity can access this same instance.
    private val bleViewModel: BleViewModel by viewModels() // Ensure BleViewModel is imported

    private var bondStateReceiver: BroadcastReceiver? = null
    private val TAG = "MainActivity"

    private val requestBluetoothEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled by user", Toast.LENGTH_SHORT).show()
                // Now that Bluetooth is on, you might trigger an initial scan if appropriate
                // This logic might be better placed in the Fragment that needs to start scanning
                // For now, we just log it. Fragments will observe ViewModel's connection status.
                Log.d(TAG, "Bluetooth enabled. ViewModel can now proceed if it was waiting.")
                // If HomeFragment or another fragment is responsible for the initial scan:
                // bleViewModel.startScan() // Or a more specific action
            } else {
                Toast.makeText(this, "Bluetooth not enabled. BLE features may not work.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allPermissionsGranted = true
            permissions.entries.forEach {
                Log.d(TAG, "Permission: ${it.key}, Granted: ${it.value}")
                if (!it.value) {
                    allPermissionsGranted = false
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(this, "All required Bluetooth permissions granted.", Toast.LENGTH_SHORT).show()
                checkAndRequestBluetoothEnable() // Permissions granted, now check if Bluetooth is on
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for BLE features.", Toast.LENGTH_LONG).show()
                showPermissionRationaleOrOpenSettings()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: com.google.android.material.navigation.NavigationView = binding.navView // Fully qualify if ambiguous
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // --- BLE Related Setup ---
        setupBondStateReceiver()
        checkAndRequestBlePermissions() // Start the permission and Bluetooth check flow

        bleViewModel.connectionStatus.observe(this) { status ->
            Log.d(TAG, "ViewModel Connection Status: $status")
            // You could update a global status indicator in the activity's toolbar if desired,
            // but individual fragments will likely handle their own UI updates for status.
        }
    }

    private fun setupBondStateReceiver() {
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        bondStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {

                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        // For older versions, the deprecated method is still what's available
                        @Suppress("DEPRECATION") // Suppress for pre-Tiramisu
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    val currentBondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    device?.let {
                        val deviceName = if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            it.name ?: "Unknown Device"
                        } else {
                            "Unknown Device (Permission Denied for name)"
                        }

                        val logMessage = "Bond state for '$deviceName' (${it.address}) changed: ${bondStateToString(previousBondState)} -> ${bondStateToString(currentBondState)}"
                        Log.d(TAG, logMessage)

                        when (currentBondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Toast.makeText(context, "$deviceName bonded successfully!", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Bond with $deviceName succeeded.")
                                // The BleViewModel's gattCallback should handle the continuation of connection/service discovery.
                                // If a connection attempt failed due to lack of bonding, the OS might auto-retry, or your
                                // ViewModel's reconnect logic might kick in if it observes this.
                            }
                            BluetoothDevice.BOND_NONE -> {
                                if (previousBondState == BluetoothDevice.BOND_BONDING) { // Only show "failed" if it was trying to bond
                                    Toast.makeText(context, "Bonding with $deviceName failed.", Toast.LENGTH_SHORT).show()
                                    Log.d(TAG, "Bond with $deviceName failed.")
                                } else {
                                    Log.d(TAG, "Bond with $deviceName removed (BOND_NONE).")
                                }
                                // Handle bonding failure - perhaps update UI or attempt to re-pair if appropriate.
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                Toast.makeText(context, "Pairing with $deviceName...", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Pairing with $deviceName...")
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
        // Register the receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondStateReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bondStateReceiver, intentFilter)
        }
    }

    private fun checkAndRequestBlePermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+ (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
                // ACCESS_FINE_LOCATION is not strictly required for BLE scanning on API 31+
                // unless you derive location from BLE beacons or use companion device pairing.
                // For general BLE, SCAN and CONNECT are primary.
            )
        } else { // Android 11 (API 30) and below
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION // Required for BLE scanning on API 30 and below
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting missing BLE permissions: ${missingPermissions.joinToString()}")
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "All required BLE permissions already granted.")
            checkAndRequestBluetoothEnable() // Permissions are fine, now check if Bluetooth is on
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        return bluetoothManager?.adapter?.isEnabled ?: false
    }

    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        // Check for BLUETOOTH_CONNECT permission before launching the intent on API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                requestBluetoothEnableLauncher.launch(enableBtIntent)
            } else {
                // This case should ideally not be hit if permission flow is correct,
                // as BLUETOOTH_CONNECT should have been requested.
                Log.e(TAG, "BLUETOOTH_CONNECT permission missing to request Bluetooth enable on API 31+.")
                Toast.makeText(this, "Bluetooth connect permission needed to enable Bluetooth.", Toast.LENGTH_LONG).show()
                // Optionally, re-trigger permission request if you detect this state
                checkAndRequestBlePermissions()
            }
        } else {
            // For older versions, existing BLUETOOTH/BLUETOOTH_ADMIN permissions are sufficient
            requestBluetoothEnableLauncher.launch(enableBtIntent)
        }
    }

    private fun checkAndRequestBluetoothEnable() {
        if (isBluetoothEnabled()) {
            Log.d(TAG, "Bluetooth is already enabled.")
            // Bluetooth is on. The app is ready for BLE operations from a system perspective.
            // Fragments can now use the ViewModel to start scans or connect.
            // bleViewModel.onSystemReadyForBle() // You could have a method in ViewModel if needed
        } else {
            Log.d(TAG, "Bluetooth is not enabled. Requesting user to enable it.")
            requestBluetoothEnable()
        }
    }

    private fun showPermissionRationaleOrOpenSettings() {
        val permissionsToExplain = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }

        var showRationale = false
        for (permission in permissionsToExplain) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showRationale = true
                break
            }
        }

        val dialogBuilder = AlertDialog.Builder(this)
        if (showRationale) {
            dialogBuilder
                .setTitle("Permissions Required")
                .setMessage("This app needs Bluetooth (and Location for older Android versions) permissions to find and connect to your ESP32 devices. Please grant these permissions to use BLE features.")
                .setPositiveButton("Grant") { _, _ ->
                    checkAndRequestBlePermissions() // Request again
                }
                .setNegativeButton("Deny") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(this, "Permissions denied. BLE features will not work.", Toast.LENGTH_LONG).show()
                }
        } else { // Permanently denied or first time (where shouldShowRequestPermissionRationale is false)
            dialogBuilder
                .setTitle("Permissions Required")
                .setMessage("Bluetooth permissions are essential for this app. As they were denied, please enable them in App Settings to use BLE features.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(this, "Permissions not granted. BLE features will not work.", Toast.LENGTH_LONG).show()
                }
        }
        dialogBuilder.show()
    }


    private fun bondStateToString(bondState: Int): String {
        return when (bondState) {
            BluetoothDevice.BOND_NONE -> "NONE"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            BluetoothDevice.BOND_BONDED -> "BONDED"
            else -> "ERROR ($bondState)"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        bondStateReceiver?.let {
            unregisterReceiver(it) // Important to unregister
            bondStateReceiver = null
        }
        Log.d(TAG, "MainActivity onDestroy called.")
        // The Activity-scoped BleViewModel will be cleared automatically by the Android framework.
        // Its onCleared() method should handle GATT disconnection and resource cleanup.
    }
}