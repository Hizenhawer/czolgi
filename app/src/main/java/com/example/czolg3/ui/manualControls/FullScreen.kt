package com.example.czolg3.ui.manualControls

// import androidx.compose.ui.graphics.PaintingStyle // Not used
// import androidx.compose.ui.input.pointer.positionChange // Not strictly needed with current logic
// import kotlinx.coroutines.launch // Not strictly needed with awaitEachGesture
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
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

// Define the range of gears
const val MIN_GEAR = -3
const val MAX_GEAR = 3

const val TAG = "FullScreen"

@Composable
fun FullScreen(bleViewModel: IBleViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    var initialCompositionDone by remember { mutableStateOf(false) }

    var leftTrackVisualGear by remember { mutableFloatStateOf(0f) } // For smooth visual update
    var rightTrackVisualGear by remember { mutableFloatStateOf(0f) } // For smooth visual update
    var leftTrackSelectedGear by remember { mutableIntStateOf(0) }
    var rightTrackSelectedGear by remember { mutableIntStateOf(0) }

    var turretAngleDegrees by remember { mutableFloatStateOf(0f) }

    fun onGearSelected() {
        Log.d(TAG, "Sending gear selected command: $leftTrackSelectedGear, $rightTrackSelectedGear")
        bleViewModel.sendGearSelectedCommand(leftTrackSelectedGear, rightTrackSelectedGear)
    }

    LaunchedEffect(leftTrackSelectedGear, rightTrackSelectedGear) {
        if (initialCompositionDone) {
            onGearSelected()
        } else {
            initialCompositionDone = true
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
            // Top Row: Turret Slider and potentially other controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp), // Added end padding
                verticalAlignment = Alignment.CenterVertically,
                // horizontalArrangement = Arrangement.SpaceBetween // If you add more controls here
            ) {
                TurretControlSlider(
                    modifier = Modifier
                        .width(200.dp),
                    currentAngleDegrees = turretAngleDegrees,
                    onAngleChange = { newAngle ->
                        turretAngleDegrees = newAngle
                    },
                    onAngleSelect = { angle -> bleViewModel.sendTurretAngleSelectedCommand(angle) },
                    minAngleDegrees = -3f,
                    maxAngleDegrees = 3f,
                    indicatorMarkingsColor = Color.White,
                )
                Spacer(modifier = Modifier.weight(1f)) // Pushes buttons to the right or center

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


            Spacer(modifier = Modifier.weight(0.1f)) // Pushes tank tracks and center text down

            Row(
                modifier = Modifier.fillMaxSize(), // This Row takes the rest of the space
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TankTrackSlider(
                    currentVisualGear = leftTrackVisualGear,
                    onVisualGearChange = { newVisualGear ->
                        leftTrackVisualGear = newVisualGear
                    },
                    onGearSelected = { selectedIntGear ->
                        Log.d(TAG, "Left Slider Selected Gear: $selectedIntGear")
                        leftTrackSelectedGear = selectedIntGear
                    },
                    modifier = Modifier
                        .fillMaxHeight(0.8f) // Adjusted height to make space for top row
                        .width(80.dp)
                        .padding(start = 24.dp, end = 16.dp, bottom = 16.dp) // Added bottom padding
                )

                // Center Column: Buttons and Text Display
                Column(
                    modifier = Modifier
                        .weight(1f) // Takes available space between sliders
                        .fillMaxHeight()
                        .padding(bottom = 16.dp), // Added bottom padding
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center // Center the content vertically
                ) {
                    Button(
                        onClick = {
                            Log.d(TAG, "Switch Mode Button Clicked")
                            TODO()
                        },
                        modifier = Modifier.padding(bottom = 24.dp) // Space below this button
                    ) {
                        Text("Switch Mode")
                    }

                    Text(
                        "L: ${leftTrackVisualGear.roundToInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "R: ${rightTrackVisualGear.roundToInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Turret: ${turretAngleDegrees.roundToInt()}°",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                TankTrackSlider(
                    currentVisualGear = rightTrackVisualGear,
                    onVisualGearChange = { newVisualGear ->
                        rightTrackVisualGear = newVisualGear
                    },
                    onGearSelected = { selectedIntGear ->
                        Log.d(TAG, "Right Slider Selected Gear: $selectedIntGear")
                        rightTrackSelectedGear = selectedIntGear
                    },
                    labelsOnLeft = true,
                    modifier = Modifier
                        .fillMaxHeight(0.8f) // Adjusted height
                        .width(80.dp)
                        .padding(start = 16.dp, end = 24.dp, bottom = 16.dp) // Added bottom padding
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=800dp,height=400dp,dpi=240")
@Composable
fun FullscreenWithSlidersPreview() {
    MaterialTheme {
        FullScreen(FakeBleViewModel())
    }
}

