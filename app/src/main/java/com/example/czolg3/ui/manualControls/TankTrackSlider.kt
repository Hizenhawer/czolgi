package com.example.czolg3.ui.manualControls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TankTrackSlider(
    currentVisualGear: Float, // Renamed for clarity: this is for the visual position
    onVisualGearChange: (Float) -> Unit, // Callback to update the visual position
    onGearSelected: (Int) -> Unit,    // Callback for when an integer gear is selected
    modifier: Modifier = Modifier,
    minGear: Int,
    maxGear: Int,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    activeTrackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    labelColor: Color = Color.White,
    snapThreshold: Float = 0.4f,
    labelsOnLeft: Boolean = false,
    autoResetToZero: Boolean
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val gearRange = maxGear - minGear

    // Tracks the last *integer* gear that was selected and reported
    var lastReportedGear by remember { mutableIntStateOf(currentVisualGear.roundToInt()) }

    BoxWithConstraints(modifier = modifier) {
        val sliderHeightPx = constraints.maxHeight.toFloat()

        val thumbOffsetPercentage =
            if (gearRange != 0) (currentVisualGear - minGear) / gearRange.toFloat() else 0.5f
        val thumbYPosition = sliderHeightPx * (1 - thumbOffsetPercentage)

        val textPaint = remember(density, labelsOnLeft) {
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = density.run { 14.sp.toPx() }
                color = android.graphics.Color.WHITE
                textAlign =
                    if (labelsOnLeft) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    coroutineScope {
                        awaitEachGesture {
                            val down: PointerInputChange =
                                awaitFirstDown().also { it.consume() } // Consume the down event
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            // Determine the visual gear based on the initial touch down position
                            // This makes the thumb "jump" to where the user first touches, then drag from there.
                            val initialPointerY = down.position.y
                            val gearChangePerPixel =
                                if (sliderHeightPx != 0f) gearRange.toFloat() / sliderHeightPx else 0f

                            // Calculate the gear directly from the initial touch position
                            // Y position 0 is at the top (maxGear), sliderHeightPx is at the bottom (minGear)
                            var currentGestureVisualGear =
                                minGear + ((sliderHeightPx - initialPointerY) * gearChangePerPixel)
                            currentGestureVisualGear = currentGestureVisualGear.coerceIn(
                                minGear.toFloat(),
                                maxGear.toFloat()
                            )

                            onVisualGearChange(currentGestureVisualGear) // Update visual state immediately to the touch point

                            var currentGestureRoundedGear = currentGestureVisualGear.roundToInt()
                            if (currentGestureRoundedGear != lastReportedGear) {
                                onGearSelected(currentGestureRoundedGear)
                                lastReportedGear = currentGestureRoundedGear
                            }

                            verticalDrag(down.id) { change: PointerInputChange ->
                                change.consume()

                                // Calculate the new gear directly from the current pointer's Y position
                                val pointerY = change.position.y
                                var newVisualGear =
                                    minGear + ((sliderHeightPx - pointerY) * gearChangePerPixel)
                                newVisualGear =
                                    newVisualGear.coerceIn(minGear.toFloat(), maxGear.toFloat())

                                onVisualGearChange(newVisualGear) // Update visual state continuously

                                val newRoundedGear = newVisualGear.roundToInt()
                                if (newRoundedGear != currentGestureRoundedGear) { // If integer gear changed during this drag
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (newRoundedGear != lastReportedGear) {
                                        onGearSelected(newRoundedGear)
                                        lastReportedGear = newRoundedGear
                                    }
                                    currentGestureRoundedGear = newRoundedGear
                                }
                            }

                            // Drag has ended
                            // currentVisualGear is already updated via onVisualGearChange from the last drag event
                            val finalVisualGearAfterInteraction = currentVisualGear
                            val finalRoundedGearAfterInteraction =
                                finalVisualGearAfterInteraction.roundToInt()

                            if (autoResetToZero) {
                                val snappedVisualGear = when {
                                    abs(finalVisualGearAfterInteraction) <= snapThreshold -> 0f
                                    abs(finalVisualGearAfterInteraction - maxGear) <= snapThreshold -> maxGear.toFloat()
                                    abs(finalVisualGearAfterInteraction - minGear) <= snapThreshold -> minGear.toFloat()
                                    else -> finalRoundedGearAfterInteraction.toFloat()
                                }
                                val finalVisualGearForReset =
                                    snappedVisualGear.coerceIn(minGear.toFloat(), maxGear.toFloat())

                                onVisualGearChange(finalVisualGearForReset)
                                val finalSelectedGearAfterReset =
                                    finalVisualGearForReset.roundToInt()

                                if (finalSelectedGearAfterReset != currentGestureRoundedGear && finalSelectedGearAfterReset != finalRoundedGearAfterInteraction) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (finalSelectedGearAfterReset != lastReportedGear) {
                                    onGearSelected(finalSelectedGearAfterReset)
                                    lastReportedGear = finalSelectedGearAfterReset
                                }
                            }
                        }
                    }
                }
        ) {
            val trackStrokeWidth = 8.dp.toPx()
            val thumbRadius = 16.dp.toPx()
            val lineCenterY = size.width / 2f

            drawLine(
                color = trackColor,
                start = Offset(lineCenterY, 0f + thumbRadius),
                end = Offset(lineCenterY, size.height - thumbRadius),
                strokeWidth = trackStrokeWidth
            )

            val zeroYPosition = sliderHeightPx * (1 - ((0f - minGear) / gearRange.toFloat()))
            val currentThumbActualY =
                thumbYPosition.coerceIn(thumbRadius, size.height - thumbRadius)
            val activeStartOffset =
                Offset(
                    lineCenterY,
                    if (currentVisualGear >= 0) zeroYPosition else currentThumbActualY
                )
            val activeEndOffset =
                Offset(
                    lineCenterY,
                    if (currentVisualGear >= 0) currentThumbActualY else zeroYPosition
                )

            if (currentVisualGear != 0f) {
                drawLine(
                    color = activeTrackColor,
                    start = activeStartOffset,
                    end = activeEndOffset,
                    strokeWidth = trackStrokeWidth
                )
            }

            val tickMarkLength = 8.dp.toPx()
            val longTickMarkLength = 16.dp.toPx()
            val labelOffsetHorizontal = 8.dp.toPx()

            (minGear..maxGear).forEach { gear ->
                val gearOffsetPercentage =
                    if (gearRange != 0) (gear.toFloat() - minGear) / gearRange.toFloat() else 0.5f
                val yPos = size.height * (1 - gearOffsetPercentage)
                val currentTickLength =
                    if (gear == 0 || gear == minGear || gear == maxGear) longTickMarkLength else tickMarkLength
                val tickStartX =
                    if (labelsOnLeft) lineCenterY + currentTickLength / 2 else lineCenterY - currentTickLength / 2
                val tickEndX =
                    if (labelsOnLeft) lineCenterY - currentTickLength / 2 else lineCenterY + currentTickLength / 2

                drawLine(
                    color = labelColor.copy(alpha = 0.7f),
                    start = Offset(tickStartX, yPos),
                    end = Offset(tickEndX, yPos),
                    strokeWidth = 2.dp.toPx()
                )

                val textXPosition = if (labelsOnLeft) {
                    tickEndX - labelOffsetHorizontal
                } else {
                    tickEndX + labelOffsetHorizontal
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        gear.toString(),
                        textXPosition,
                        yPos + textPaint.textSize / 3f,
                        textPaint
                    )
                }
            }

            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(lineCenterY, currentThumbActualY)
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius / 3,
                center = Offset(lineCenterY, currentThumbActualY)
            )
        }
    }
}