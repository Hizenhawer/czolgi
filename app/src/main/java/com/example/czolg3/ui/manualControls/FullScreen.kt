package com.example.czolg3.ui.manualControls

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.czolg3.ble.FakeBleViewModel
import com.example.czolg3.ble.IBleViewModel
import kotlin.math.roundToInt

// Define control modes
enum class ControlMode {
    DRIVE,
    CALIBRATE_DISTANCE
}

// Define the range of gears for DRIVE mode
const val MIN_DRIVE_GEAR = -3
const val MAX_DRIVE_GEAR = 3

// Define the range for CALIBRATE_DISTANCE mode
const val MIN_CALIBRATE_VALUE = -6
const val MAX_CALIBRATE_VALUE = 6

const val TAG = "FullScreen"

@Composable
fun FullScreen(bleViewModel: IBleViewModel, internalControlMode: ControlMode = ControlMode.DRIVE) {
    val context = LocalContext.current
    val activity = context as? Activity

    var currentMode by remember { mutableStateOf(internalControlMode) }
    var initialCompositionDone by remember { mutableStateOf(false) }

    // State for DRIVE mode
    var leftTrackVisualGear by remember { mutableFloatStateOf(0f) }
    var rightTrackVisualGear by remember { mutableFloatStateOf(0f) }
    var leftTrackSelectedGear by remember { mutableIntStateOf(0) }
    var rightTrackSelectedGear by remember { mutableIntStateOf(0) }

    // State for CALIBRATE_DISTANCE mode (uses left slider's states)
    // When switching to CALIBRATE_DISTANCE, leftTrackSelectedGear will hold the calibration value.
    // We can reuse leftTrackVisualGear for its visual representation.
    // var calibrationDistanceSelected by remember { mutableIntStateOf(0) }
    // var calibrationDistanceVisual by remember { mutableFloatStateOf(0f) }
    // For simplicity, let's reuse leftTrackSelectedGear and leftTrackVisualGear and just change their meaning based on mode.

    var turretAngleDegrees by remember { mutableFloatStateOf(0f) }

    fun sendDriveCommand() {
        Log.d(
            TAG,
            "DRIVE Mode: Sending gear selected command: L:$leftTrackSelectedGear, R:$rightTrackSelectedGear"
        )
        bleViewModel.sendGearSelectedCommand(leftTrackSelectedGear, rightTrackSelectedGear)
    }

    // Effect for sending DRIVE commands
    LaunchedEffect(leftTrackSelectedGear, rightTrackSelectedGear, currentMode) {
        if (currentMode == ControlMode.DRIVE) {
            if (initialCompositionDone) {
                sendDriveCommand()
            } else {
                // For DRIVE mode, initialCompositionDone helps prevent sending 0,0 on first load.
                // This might need adjustment if we want selected gear to persist across mode switches.
                initialCompositionDone = true
            }
        } else {
            // In CALIBRATE_DISTANCE mode, we don't send commands automatically on slider change.
            // Reset initialCompositionDone if switching away from DRIVE so it works on next switch to DRIVE
            initialCompositionDone = false
        }
    }


    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Determine slider parameters based on mode for the LEFT slider
    val currentLeftMin =
        if (currentMode == ControlMode.DRIVE) MIN_DRIVE_GEAR else MIN_CALIBRATE_VALUE
    val currentLeftMax =
        if (currentMode == ControlMode.DRIVE) MAX_DRIVE_GEAR else MAX_CALIBRATE_VALUE
    val leftSliderLabel = if (currentMode == ControlMode.DRIVE) "L" else "Inch"


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Row: Turret Slider and Light Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TurretControlSlider(
                    modifier = Modifier.width(200.dp),
                    currentAngleDegrees = turretAngleDegrees,
                    onAngleChange = { newAngle -> turretAngleDegrees = newAngle },
                    onAngleSelect = { angle -> bleViewModel.sendTurretAngleSelectedCommand(angle) },
                    minAngleDegrees = -3f, // Assuming turret slider is independent of these modes
                    maxAngleDegrees = 3f,
                    indicatorMarkingsColor = Color.White,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        Log.d(TAG, "Turret Lights Button Clicked")
                        bleViewModel.sendCommand("LIGHTS:TURRET:TOGGLE")
                    }) {
                        Text("T-Lights")
                    }
                    Button(onClick = {
                        Log.d(TAG, "Hull Lights Button Clicked")
                        bleViewModel.sendCommand("LIGHTS:HULL:TOGGLE")
                    }) {
                        Text("H-Lights")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Main Content Row (Sliders and Center Info)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left TankTrackSlider (behavior changes with mode)
                val isDriveMode = (currentMode == ControlMode.DRIVE)

                key(isDriveMode) { // Keying on the boolean that determines autoResetToZero
                    TankTrackSlider(
                        currentVisualGear = leftTrackVisualGear,
                        onVisualGearChange = { newVisualGear ->
                            leftTrackVisualGear = newVisualGear
                        },
                        onGearSelected = { selectedValue ->
                            // This callback is when the user *releases* the slider or a discrete value is chosen.
                            Log.d(TAG, "Left Slider Selected Value: $selectedValue, Mode: $currentMode")
                            leftTrackSelectedGear = selectedValue // Always update the selected value
                        },
                        minGear = currentLeftMin,
                        maxGear = currentLeftMax,
                        autoResetToZero = isDriveMode, // Use the variable
                        modifier = Modifier
                            .fillMaxHeight(0.8f)
                            .width(80.dp)
                            .padding(start = 24.dp, end = 16.dp, bottom = 16.dp)
                    )
                }

                // Center Column: Mode Switch Button and Text Display
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 16.dp), // Added horizontal padding
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            currentMode = if (currentMode == ControlMode.DRIVE) {
                                // Switching TO Calibrate Mode
                                // Reset right track as it's not used.
                                rightTrackVisualGear = 0f
                                rightTrackSelectedGear = 0
                                // Left slider's value for calibration starts at 0 or persists?
                                // Let's start it at 0 for calibration.
                                leftTrackVisualGear = 0f
                                leftTrackSelectedGear = 0
                                initialCompositionDone = false // Reset for next DRIVE mode entry
                                ControlMode.CALIBRATE_DISTANCE
                            } else {
                                // Switching TO Drive Mode
                                // Reset calibration value (left slider) to 0 for drive.
                                leftTrackVisualGear = 0f
                                leftTrackSelectedGear = 0
                                ControlMode.DRIVE
                            }
                            Log.d(TAG, "Switch Mode Button Clicked. New Mode: $currentMode")
                        },
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Text(if (currentMode == ControlMode.DRIVE) "To Inches Mode" else "To Drive Mode")
                    }

                    // Display selected value from left slider
                    Text(
                        "$leftSliderLabel: ${leftTrackVisualGear.roundToInt()}", // Use visual for live update
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Display for right track (only in DRIVE mode) or other info
                    if (currentMode == ControlMode.DRIVE) {
                        Text(
                            "R: ${rightTrackVisualGear.roundToInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    } else {
                        // Placeholder or other info for CALIBRATE_DISTANCE mode's center text
                        Text(
                            "Adjust Cal Distance",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.LightGray
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Turret: ${turretAngleDegrees.roundToInt()}°",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                // Right Panel: Either TankTrackSlider or Calibration Info
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .width(80.dp)
                        .padding(start = 16.dp, end = 24.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {

                    androidx.compose.animation.AnimatedVisibility(
                        visible = currentMode == ControlMode.DRIVE,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TankTrackSlider(
                            currentVisualGear = rightTrackVisualGear,
                            onVisualGearChange = { newVisualGear ->
                                rightTrackVisualGear = newVisualGear
                            },
                            onGearSelected = { selectedIntGear ->
                                Log.d(TAG, "Right Slider Selected Gear: $selectedIntGear")
                                rightTrackSelectedGear = selectedIntGear
                                // Drive command sent by LaunchedEffect
                            },
                            labelsOnLeft = true,
                            minGear = MIN_DRIVE_GEAR,
                            maxGear = MAX_DRIVE_GEAR,
                            autoResetToZero = true, // Assuming default for drive mode
                            modifier = Modifier.fillMaxSize() // Fill the Box
                        )
                    }


                    androidx.compose.animation.AnimatedVisibility(
                        visible = currentMode == ControlMode.CALIBRATE_DISTANCE,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                "Set Dist:",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                // Use leftTrackSelectedGear for the confirmed value
                                text = "${leftTrackSelectedGear}", // This is the value from the left slider
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Button(onClick = {
                                val calibrationValue = leftTrackSelectedGear
                                Log.d(
                                    TAG,
                                    "Send Calibration Button Clicked. Value: $calibrationValue"
                                )
                                bleViewModel.sendCommand("DIST:$calibrationValue")
                                // Reset left slider to 0 after sending
                                leftTrackVisualGear = 0f
                                leftTrackSelectedGear = 0
                            }) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=800dp,height=400dp,dpi=240")
@Composable
fun FullscreenWithSlidersPreview_DriveMode() {
    MaterialTheme {
        FullScreen(FakeBleViewModel()) // Starts in DRIVE mode
    }
}

@Preview(showBackground = true, device = "spec:width=800dp,height=400dp,dpi=240")
@Composable
fun FullscreenWithSlidersPreview_CalibrateMode() {
    MaterialTheme {
        val fakeVm = FakeBleViewModel()
        // A bit tricky to directly preview the other mode without interaction.
        // You could add a parameter to FullScreen for initial mode for preview purposes.
        // For now, this will show DRIVE mode.
        // To test CALIBRATE_DISTANCE mode in preview, you'd need to simulate the button click or
        // add an initialMode parameter to FullScreen.
        FullScreen(fakeVm, internalControlMode = ControlMode.CALIBRATE_DISTANCE)
    }
}

