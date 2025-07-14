package com.example.czolg3.ui.manualControls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

const val TURRET_MIN_ANGLE_DEGREES = -90f // Example: -90 degrees (left)
const val TURRET_MAX_ANGLE_DEGREES = 90f  // Example: +90 degrees (right)
private const val SLIDER_ARC_DEGREES = 100f // How wide the visible arc is (e.g., 120 degrees)
private const val MIDDLE_ANGLE_DEGREES = 0f // Define your snap-to target
private const val DESIRED_ASPECT_RATIO = 2f / 1f

@Composable
fun TurretControlSlider(
    modifier: Modifier = Modifier,
    currentAngleDegrees: Float, // Current angle of the turret in degrees
    onAngleChange: (Float) -> Unit, // Callback when the angle changes
    onAngleSelect: (Int) -> Unit, // Callback when an angle is selected
    minAngleDegrees: Float = TURRET_MIN_ANGLE_DEGREES,
    maxAngleDegrees: Float = TURRET_MAX_ANGLE_DEGREES,
    arcColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    indicatorColor: Color = Color.White,
    arcStrokeWidth: Dp = 8.dp,
    thumbRadius: Dp = 12.dp,
    indicatorMarkingsColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensity.current
    val arcStrokeWidthPx = with(density) { arcStrokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val haptic = LocalHapticFeedback.current
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    var currentSelectedAngle by remember { mutableIntStateOf(currentAngleDegrees.roundToInt()) }

    Box(
        modifier = modifier
            .aspectRatio(
                DESIRED_ASPECT_RATIO,
                matchHeightConstraintsFirst = false
            ) // Then apply aspect ratio
            .onSizeChanged { newSize -> // Get the actual size after aspect ratio is applied
                componentSize = newSize
            }
            .pointerInput(Unit) { // For initial press haptic
                detectTapGestures(
                    onPress = {
                        // tryAwaitRelease() // No need if not handling onTap and want drag to take over
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    // No onTap needed if only drag is primary interaction for angle change
                )
            }
            .pointerInput(Unit) { // For drag gestures
                detectDragGestures(
                    onDragStart = {
                        // Optional: any specific setup for drag start
                        // e.g., if you want a different haptic for drag starting vs initial press
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val centerX = size.width / 2f
                        val pivotY = size.height * 0.8f

                        val dx = change.position.x - centerX
                        val dy = change.position.y - pivotY

                        val angleRad = atan2(dy, dx)
                        var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                        angleDeg += 90f

                        val effectiveAngle = angleDeg.coerceIn(
                            -(SLIDER_ARC_DEGREES / 2f),
                            (SLIDER_ARC_DEGREES / 2f)
                        )

                        val normalizedSliderPos =
                            (effectiveAngle + (SLIDER_ARC_DEGREES / 2f)) / SLIDER_ARC_DEGREES
                        val newTurretAngle =
                            minAngleDegrees + normalizedSliderPos * (maxAngleDegrees - minAngleDegrees)

                        onAngleChange(newTurretAngle.coerceIn(minAngleDegrees, maxAngleDegrees))
                        val selecedAngle = newTurretAngle.roundToInt()
                        if (selecedAngle != currentSelectedAngle) {
                            currentSelectedAngle = selecedAngle
                            onAngleSelect(selecedAngle)
                        }

                    },
                    onDragEnd = {
                        // Snap back to the middle when drag ends
                        onAngleChange(MIDDLE_ANGLE_DEGREES)
                        onAngleSelect(MIDDLE_ANGLE_DEGREES.roundToInt())
                        // Optional: haptic feedback for snap
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Or another type
                    },
                    onDragCancel = {
                        // Also snap back if the drag is cancelled
                        onAngleChange(MIDDLE_ANGLE_DEGREES)
                        onAngleSelect(MIDDLE_ANGLE_DEGREES.roundToInt())
                        // Optional: haptic feedback for snap
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val pivotY = canvasHeight * 1.2f
            // Robust radius calculation
            val safeRadiusHorizontal =
                (canvasWidth / 2f) - thumbRadiusPx - arcStrokeWidthPx - 5.dp.toPx()
            val safeRadiusVertical = pivotY - thumbRadiusPx - arcStrokeWidthPx - 5.dp.toPx()
            val radius = min(
                safeRadiusHorizontal,
                safeRadiusVertical
            ).coerceAtLeast(10.dp.toPx()) // Ensure a minimum radius

            val startAngleCanvas = -90f - (SLIDER_ARC_DEGREES / 2f)
            drawArc(
                color = arcColor,
                startAngle = startAngleCanvas,
                sweepAngle = SLIDER_ARC_DEGREES,
                useCenter = false,
                topLeft = Offset(centerX - radius, pivotY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = arcStrokeWidthPx, cap = StrokeCap.Round)
            )

            val numMarkings = 7
            // val totalAngleRange = maxAngleDegrees - minAngleDegrees // Not used directly in this loop

            for (i in 0 until numMarkings) {
                val proportion = i / (numMarkings - 1).toFloat()
                val angleOffsetDeg = proportion * SLIDER_ARC_DEGREES - (SLIDER_ARC_DEGREES / 2f)
                val angleRad = Math.toRadians(angleOffsetDeg - 90.0).toFloat()

                val tickStartRadius = radius + arcStrokeWidthPx / 2f
                val tickEndRadius =
                    tickStartRadius + if (i == (numMarkings - 1) / 2) 8.dp.toPx() else 4.dp.toPx()


                val tickStartX = centerX + tickStartRadius * cos(angleRad)
                val tickStartY = pivotY + tickStartRadius * sin(angleRad)
                val tickEndX = centerX + tickEndRadius * cos(angleRad)
                val tickEndY = pivotY + tickEndRadius * sin(angleRad)

                drawLine(
                    color = indicatorMarkingsColor,
                    start = Offset(tickStartX, tickStartY),
                    end = Offset(tickEndX, tickEndY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val turretRange = maxAngleDegrees - minAngleDegrees
            val normalizedTurretAngle =
                if (turretRange == 0f) 0.5f else (currentAngleDegrees - minAngleDegrees) / turretRange
            val thumbAngleOffsetDeg =
                normalizedTurretAngle * SLIDER_ARC_DEGREES - (SLIDER_ARC_DEGREES / 2f)
            val thumbAngleRad = Math.toRadians(thumbAngleOffsetDeg - 90.0).toFloat()

            val thumbX = centerX + radius * cos(thumbAngleRad)
            val thumbY = pivotY + radius * sin(thumbAngleRad)

            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = indicatorColor,
                radius = thumbRadiusPx / 2.5f,
                center = Offset(thumbX, thumbY)
            )

            drawLine(
                color = indicatorMarkingsColor.copy(alpha = 0.6f),
                start = Offset(
                    centerX,
                    pivotY - radius - arcStrokeWidthPx / 2 - 10.dp.toPx() // Adjusted start
                ),
                end = Offset(centerX, pivotY - radius * 0.85f), // Adjusted end
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF888888, widthDp = 350, heightDp = 350)
@Composable
fun TurretControlSliderPreview() {
    var angle by remember { mutableFloatStateOf(0f) }
    MaterialTheme {
        Box(
            contentAlignment = Alignment.Center, // Changed for better previewing if height increases
            modifier = Modifier.fillMaxWidth() // Allow box to take full width for alignment
        ) {
            TurretControlSlider(
                modifier = Modifier
                    .height(150.dp), // Increased height for better visibility of pivot adjustments
                currentAngleDegrees = angle,
                onAngleChange = { newAngle ->
                    angle = newAngle
                },
                minAngleDegrees = -45f, // Example range
                maxAngleDegrees = 45f,   // Example range
                onAngleSelect = { selectedAngle ->
                    // Handle angle selection if needed
                }
            )
        }
    }
}
