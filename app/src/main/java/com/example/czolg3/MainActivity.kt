package com.example.czolg3

// Import other composable screens if you create them (e.g., GalleryScreen, SlideshowScreen)
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.czolg3.ble.BleViewModel
import com.example.czolg3.ui.home.HomeScreen
import com.example.czolg3.ui.home.HomeViewModel
import com.example.czolg3.ui.manualControls.FullScreen
import com.example.czolg3.ui.manualControls.ManualControlsScreen
import com.example.czolg3.ui.manualControls.ManualControlsViewModel
import com.example.czolg3.ui.slideshow.SlideshowScreen
import com.example.czolg3.ui.slideshow.SlideshowViewModel
import kotlinx.coroutines.launch

// Define your navigation routes
object AppDestinations {
    const val MAIN_ACTIVITY_GRAPH_ROUTE = "mainActivityGraph"
    const val HOME_ROUTE = "home"
    const val SLIDESHOW_ROUTE = "slideshow"

    const val MANUAL_CONTROLS_GRAPH_ROUTE = "manualControlsGraph"
    const val MANUAL_CONTROLS_ROUTE = "manualControls"
    const val MANUAL_CONTROLS_FULL_SCREEN_ROUTE = "MCFullScreen"
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() { // Note: ComponentActivity is base for Compose

    private val bleViewModel: BleViewModel by viewModels()
    private val TAG = "MainActivityCompose"
    private var bondStateReceiver: BroadcastReceiver? = null

    // --- Permission and Bluetooth Enable Launchers ---
    private val requestBluetoothEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Bluetooth enabled by user.")
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show()
            }
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allPermissionsGranted = true
            permissions.entries.forEach {
                Log.d(TAG, "Permission: ${it.key}, Granted: ${it.value}")
                if (!it.value) allPermissionsGranted = false
            }
            if (allPermissionsGranted) {
                Toast.makeText(this, "BT Permissions granted", Toast.LENGTH_SHORT).show()
                checkAndRequestBluetoothEnable()
            } else {
                Toast.makeText(this, "BT Permissions needed", Toast.LENGTH_LONG).show()
                showPermissionRationaleOrOpenSettings()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- BLE Related Setup (remains largely the same) ---
        setupBondStateReceiver()
        checkAndRequestBlePermissions() // Start the permission and Bluetooth check flow

        // Observe ViewModel status (optional, could be done in specific Composables)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Now this will work!
                bleViewModel.connectionStatus.collect { status ->
                    Log.d(TAG, "ViewModel Connection Status (from StateFlow): $status")
                    // You can perform actions here
                }
            }
        }

        setContent {
            // Single NavController for the entire application
            val navController = rememberNavController()

            // Top-level NavHost
            NavHost(
                navController = navController,
                // Start with the graph that contains your initial screen (e.g., home screen)
                startDestination = AppDestinations.MAIN_ACTIVITY_GRAPH_ROUTE
            ) {
                // Define the nested graph for scaffolded (normal) screens
                mainGraph(navController, bleViewModel)

                // Define the nested graph for fullscreen screens
                manualControlsGraph()

                // You could also have top-level destinations here if needed,
                // but for your case, graphs are cleaner.
            }
        }
    }


    // Extension function to define the scaffolded graph (keeps MainActivity cleaner)
    private fun NavGraphBuilder.mainGraph(
        navController: NavHostController,
        bleViewModel: BleViewModel // Pass any necessary dependencies
    ) {
        navigation(
            startDestination = AppDestinations.HOME_ROUTE, // Start of this sub-graph
            route = AppDestinations.MAIN_ACTIVITY_GRAPH_ROUTE  // Route to this sub-graph
        ) {
            // Composable for MainAppScreen which provides the Scaffold
            composable(AppDestinations.HOME_ROUTE) { // This route is a bit redundant if MainAppScreen is always the entry
                // A more common pattern is to just define the content directly
                // or make the graph's route the same as its startDestination.
                // For clarity, let's assume MainAppScreen is the container.
                MainAppScreen(
                    mainNavController = navController, // Pass the same top-level controller
                    bleViewModel = bleViewModel
                    // The NavHost inside MainAppScreen will now define destinations WITHIN this scaffolded context
                    // OR, MainAppScreen directly hosts its NavHost for its children.
                    // Let's go with MainAppScreen hosting its own NavHost for its children
                    // to maintain the original structure of MainAppScreen.
                )
            }
            // If MainAppScreen itself hosts the NavHost for its children, then HOME_ROUTE, etc.,
            // are defined *within* MainAppScreen's NavHost.
            // The navigation call to this graph ("scaffolded_graph") will land you in MainAppScreen,
            // which then uses its internal NavHost starting at HOME_ROUTE.
        }
    }


    // Extension function to define the fullscreen graph
    private fun NavGraphBuilder.manualControlsGraph() {
        navigation(
            startDestination = AppDestinations.MANUAL_CONTROLS_FULL_SCREEN_ROUTE, // Default fullscreen screen
            route = AppDestinations.MANUAL_CONTROLS_GRAPH_ROUTE // Route to this sub-graph
        ) {
            composable(AppDestinations.MANUAL_CONTROLS_FULL_SCREEN_ROUTE) {
                FullScreen(bleViewModel = bleViewModel) // Pass the same top-level controller
            }
            // Add other fullscreen destinations here if you have more
            // Example:
            // composable("another_fullscreen_route") { AnotherFullScreen(navController) }
        }
    }

    // --- MainAppScreen Composable (Houses the Scaffold and its content's NavHost) ---
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppScreen(
        mainNavController: NavHostController, // The NavController from MainActivity (for global navigation)
        bleViewModel: BleViewModel
    ) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // For the content *within* the Scaffold, we use a *new, nested* NavController.
        // This simplifies managing the back stack and UI state (like TopAppBar title)
        // for the screens that are part of the scaffolded layout.
        val scaffoldContentNavController = rememberNavController()

        // Observe the current route of the *nested* NavController for UI updates (drawer, title)
        val scaffoldNavBackStackEntry by scaffoldContentNavController.currentBackStackEntryAsState()
        val currentScaffoldRoute = scaffoldNavBackStackEntry?.destination?.route ?: AppDestinations.HOME_ROUTE

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        "CZOLG3 Menu",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    HorizontalDivider()
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentScaffoldRoute == AppDestinations.HOME_ROUTE,
                        onClick = {
                            scaffoldContentNavController.navigate(AppDestinations.HOME_ROUTE) {
                                popUpTo(scaffoldContentNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Games, contentDescription = "Manual Controls") },
                        label = { Text("Manual Controls") }, // This leads to the non-fullscreen version
                        selected = currentScaffoldRoute == AppDestinations.MANUAL_CONTROLS_ROUTE,
                        onClick = {
                            scaffoldContentNavController.navigate(AppDestinations.MANUAL_CONTROLS_ROUTE) {
                                popUpTo(scaffoldContentNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Slideshow, contentDescription = "Slideshow") },
                        label = { Text("Slideshow") },
                        selected = currentScaffoldRoute == AppDestinations.SLIDESHOW_ROUTE,
                        onClick = {
                            scaffoldContentNavController.navigate(AppDestinations.SLIDESHOW_ROUTE) {
                                popUpTo(scaffoldContentNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    // Add other drawer items for scaffolded screens
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(determineTitle(currentScaffoldRoute)) }, // Helper to get title
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open Drawer")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                // This NavHost is for the content *inside* the Scaffold
                ScaffoldContentNavHost(
                    modifier = Modifier.padding(paddingValues),
                    scaffoldNavController = scaffoldContentNavController, // The nested NavController
                    mainNavController = mainNavController,               // The top-level NavController (for navigating to fullscreen)
                    bleViewModel = bleViewModel
                )
            }
        }
    }

    // --- NavHost for the content area of the Scaffold ---
    @Composable
    fun ScaffoldContentNavHost(
        modifier: Modifier = Modifier,
        scaffoldNavController: NavHostController, // For navigation within scaffolded screens
        mainNavController: NavHostController,   // For navigation to other graphs (e.g., fullscreen)
        bleViewModel: BleViewModel
    ) {
        NavHost(
            navController = scaffoldNavController, // Use the NESTED NavController here
            startDestination = AppDestinations.HOME_ROUTE, // Default screen within the scaffold
            modifier = modifier
        ) {
            composable(AppDestinations.HOME_ROUTE) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    bleViewModel = bleViewModel,
                    homeViewModel = homeViewModel
                    // Pass scaffoldNavController if HomeScreen needs to navigate to other scaffolded screens
                )
            }
            composable(AppDestinations.MANUAL_CONTROLS_ROUTE) { // The non-fullscreen one
                val manualControlsViewModel: ManualControlsViewModel = viewModel()
                ManualControlsScreen(
                    manualControlsViewModel = manualControlsViewModel,
                    onNavigateToFullScreen = {
                        // Use the MAIN NavController to switch to the fullscreen GRAPH
                        mainNavController.navigate(AppDestinations.MANUAL_CONTROLS_GRAPH_ROUTE)
                    }
                    // Pass scaffoldNavController if it needs to navigate to other scaffolded screens
                )
            }
            composable(AppDestinations.SLIDESHOW_ROUTE) {
                val slideshowViewModel: SlideshowViewModel = viewModel()
                SlideshowScreen(
                    slideshowViewModel = slideshowViewModel
                    // Pass scaffoldNavController if it needs to navigate to other scaffolded screens
                )
            }
            // Add other destinations that appear within the Scaffold here
        }
    }

    // Helper function to determine TopAppBar title (example)
    @Composable
    private fun determineTitle(route: String?): String {
        return when (route) {
            AppDestinations.HOME_ROUTE -> "Home"
            AppDestinations.MANUAL_CONTROLS_ROUTE -> "Manual Controls"
            AppDestinations.SLIDESHOW_ROUTE -> "Slideshow"
            else -> "Czolg3"
        }
    }


    // --- Permission and BLE logic (mostly unchanged from original MainActivity) ---
    private fun setupBondStateReceiver() {
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        bondStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    val previousBondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.ERROR
                    )
                    val currentBondState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    device?.let {
                        val deviceName = if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            it.name ?: "Unknown Device"
                        } else {
                            "Unknown (Permission Denied)"
                        }
                        Log.d(
                            TAG,
                            "Bond state for '$deviceName' (${it.address}) changed: ${
                                bondStateToString(previousBondState)
                            } -> ${bondStateToString(currentBondState)}"
                        )
                        when (currentBondState) {
                            BluetoothDevice.BOND_BONDED -> Toast.makeText(
                                context,
                                "$deviceName bonded",
                                Toast.LENGTH_SHORT
                            ).show()

                            BluetoothDevice.BOND_NONE -> if (previousBondState == BluetoothDevice.BOND_BONDING) Toast.makeText(
                                context,
                                "Bonding with $deviceName failed",
                                Toast.LENGTH_SHORT
                            ).show()

                            BluetoothDevice.BOND_BONDING -> Toast.makeText(
                                context,
                                "Pairing with $deviceName...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondStateReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bondStateReceiver, intentFilter)
        }
    }

    private fun checkAndRequestBlePermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkAndRequestBluetoothEnable()
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        return bluetoothManager?.adapter?.isEnabled ?: false
    }

    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothEnableLauncher.launch(enableBtIntent)
            } else {
                Toast.makeText(this, "BT Connect permission needed", Toast.LENGTH_LONG).show()
                checkAndRequestBlePermissions()
            }
        } else {
            requestBluetoothEnableLauncher.launch(enableBtIntent)
        }
    }

    private fun checkAndRequestBluetoothEnable() {
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable()
        } else {
            Log.d(TAG, "Bluetooth is already enabled.")
        }
    }

    private fun showPermissionRationaleOrOpenSettings() {
        // Using AlertDialog from androidx.compose.material3 for consistency if showing from Composable
        // However, this is still in Activity scope, so android.app.AlertDialog is also fine.
        // For simplicity, keeping the original AlertDialog logic for now.
        // If you want a Compose AlertDialog, you'd manage its state with `remember { mutableStateOf(false) }`
        // and call it within a Composable.
        val permissionsToExplain = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }
        var showRationale = permissionsToExplain.any {
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                it
            )
        }

        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this) // Original dialog
        if (showRationale) {
            dialogBuilder.setTitle("Permissions Required")
                .setMessage("App needs BT/Location permissions.")
                .setPositiveButton("Grant") { _, _ -> checkAndRequestBlePermissions() }
                .setNegativeButton("Deny") { dialog, _ ->
                    dialog.dismiss(); Toast.makeText(
                    this,
                    "Permissions denied.",
                    Toast.LENGTH_LONG
                ).show()
                }
        } else {
            dialogBuilder.setTitle("Permissions Required")
                .setMessage("Enable permissions in App Settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss(); Toast.makeText(
                    this,
                    "Permissions not granted.",
                    Toast.LENGTH_LONG
                ).show()
                }
        }
        dialogBuilder.show()
    }

    private fun bondStateToString(bondState: Int): String = when (bondState) {
        BluetoothDevice.BOND_NONE -> "NONE"; BluetoothDevice.BOND_BONDING -> "BONDING"; BluetoothDevice.BOND_BONDED -> "BONDED"; else -> "ERROR"
    }

    override fun onDestroy() {
        super.onDestroy()
        bondStateReceiver?.let { unregisterReceiver(it) }
        Log.d(TAG, "MainActivity onDestroy called.")
    }
}
