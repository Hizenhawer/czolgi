package com.example.czolg3.ui.home // Or your chosen package

// import androidx.compose.runtime.rememberCoroutineScope // Not strictly needed here if not launching other coroutines from UI events
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.czolg3.ble.BleViewModel

// The TopAppBar from your Scaffold might be better handled by the Scaffold in MainActivity
// if you want a consistent AppBar across all screens.
// If this screen needs a unique TopAppBar, then keeping it here is fine.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bleViewModel: BleViewModel,
    homeViewModel: HomeViewModel, // Accept it
    modifier: Modifier = Modifier
) {
    val message by homeViewModel.screenMessage.collectAsStateWithLifecycle()
    val connectionStatus by bleViewModel.connectionStatus.collectAsStateWithLifecycle()
    val operationLog by bleViewModel.operationLog.collectAsStateWithLifecycle()
    val isLightsLoopActive by bleViewModel.isLightsLoopUiActive.collectAsStateWithLifecycle()
    val receivedData by bleViewModel.receivedData.collectAsStateWithLifecycle()

    val logScrollState = rememberScrollState()
    // val coroutineScope = rememberCoroutineScope() // Only needed if you launch coroutines directly from UI event handlers not tied to LaunchedEffect

    val isScanButtonEnabled = when (connectionStatus) {
        "Connected", "Ready", "Connecting..." -> false
        else -> true
    }
    val isScanButtonChecked = connectionStatus == "Scanning..." // "Scanning..." state from ViewModel
    val isLightsLoopButtonEnabled = when (connectionStatus) {
        "Connected", "Ready" -> true
        else -> false
    }

    LaunchedEffect(operationLog.toString()) { // Trigger when operationLog content changes
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    // Consider if this Scaffold is needed here, or if MainActivity's Scaffold should handle the TopAppBar.
    // If MainActivity provides the Scaffold and TopAppBar, this HomeScreen would just be the Column content.
    Column(
        modifier = Modifier
            .padding(16.dp) // Additional screen-specific padding
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                if (isScanButtonChecked) {
                    bleViewModel.stopBleScan()
                } else {
                    bleViewModel.disconnectDevice()
                    bleViewModel.startBleScan()
                }
            },
            enabled = isScanButtonEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isScanButtonChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(if (isScanButtonChecked) "Stop Scan" else "Start Scan")
        }

        Text(
            text = "Status: $connectionStatus",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = { bleViewModel.toggleLightsLoop() },
            enabled = isLightsLoopButtonEnabled
        ) {
            Text(if (isLightsLoopActive) "Stop Lights Loop" else "Start Lights Loop")
        }

        Text(
            text = "Received: $receivedData",
            style = MaterialTheme.typography.bodyMedium
        )

        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFEFEFEF))
                .padding(8.dp)
        ) {
            Text(
                text = operationLog.toString(),
                modifier = Modifier
                    .verticalScroll(logScrollState)
                    .fillMaxSize(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        // You'd need a way to provide a BleViewModel instance for preview,
        // or create a version of HomeScreen that takes default values.
        // For simplicity, we can just show a static version.
        // Consider creating a fake BleViewModel for previews.
        Column(modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preview - UI Elements would be here")
            Button(onClick = {}) { Text("Start Scan") }
            Text("Status: Disconnected")
            Button(onClick = {}) { Text("Start Lights Loop") }
            Text("Received: ---")
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.LightGray)
                .padding(8.dp)) {
                Text("Logs would appear here...")
            }
        }
    }
}
// Example of a fake ViewModel for previews (place in a test/debug source set or directly in file for simplicity if small)
// class PreviewBleViewModel : BleViewModel(Application()) {
//    init {
//        _connectionStatus.value = "Preview Mode"
//        _operationLog.value = StringBuilder("Log line 1\nLog line 2")
//        _isLightsLoopUiActive.value = false
//        _receivedData.value = "No data"
//    }
// }
//
// @Preview(showBackground = true)
// @Composable
// fun HomeScreenWithPreviewViewModel() {
//    MaterialTheme {
//        HomeScreen(bleViewModel = PreviewBleViewModel())
//    }
// }

