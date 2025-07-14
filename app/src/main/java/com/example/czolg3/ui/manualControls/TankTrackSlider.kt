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
    minGear: Int = MIN_GEAR,
    maxGear: Int = MAX_GEAR,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    activeTrackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    labelColor: Color = Color.White,
    snapThreshold: Float = 0.4f,
    labelsOnLeft: Boolean = false
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
                            val down: PointerInputChange = awaitFirstDown()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            val dragStartY = down.position.y
                            val visualGearAtDragStart = currentVisualGear
                            var currentDragRoundedGear =
                                currentVisualGear.roundToInt() // Track rounded gear for this drag

                            // Initialize lastReportedGear based on the state when the drag starts,
                            // if it hasn't been set or to ensure it's current.
                            lastReportedGear = currentVisualGear.roundToInt()


                            verticalDrag(down.id) { change: PointerInputChange ->
                                change.consume()
                                val dragDeltaYFromStart = change.position.y - dragStartY
                                val gearChangePerPixel =
                                    if (sliderHeightPx != 0f) gearRange.toFloat() / sliderHeightPx else 0f
                                val newVisualGearRaw =
                                    visualGearAtDragStart - (dragDeltaYFromStart * gearChangePerPixel)
                                val newVisualGearCoerced =
                                    newVisualGearRaw.coerceIn(minGear.toFloat(), maxGear.toFloat())

                                onVisualGearChange(newVisualGearCoerced) // Update visual state continuously

                                val newRoundedGear = newVisualGearCoerced.roundToInt()
                                if (newRoundedGear != currentDragRoundedGear) { // Changed integer gear during this drag
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Call onGearSelected only if it's a new integer gear compared to the last reported one
                                    if (newRoundedGear != lastReportedGear) {
                                        onGearSelected(newRoundedGear)
                                        lastReportedGear = newRoundedGear
                                    }
                                    currentDragRoundedGear =
                                        newRoundedGear // Update for current drag
                                }
                            }

                            // Drag has ended, now apply snapping
                            val finalVisualGearAfterDrag =
                                currentVisualGear // Get the latest visual gear
                            val roundedFinalVisualGear = finalVisualGearAfterDrag.roundToInt()
                            val snappedVisualGear = when {
                                abs(finalVisualGearAfterDrag) <= snapThreshold -> 0f
                                abs(finalVisualGearAfterDrag - maxGear) <= snapThreshold -> maxGear.toFloat()
                                abs(finalVisualGearAfterDrag - minGear) <= snapThreshold -> minGear.toFloat()
                                else -> roundedFinalVisualGear.toFloat()
                            }
                            val finalVisualGear =
                                snappedVisualGear.coerceIn(minGear.toFloat(), maxGear.toFloat())

                            onVisualGearChange(finalVisualGear) // Update visual to the snapped position

                            val finalSelectedGear = finalVisualGear.roundToInt()
                            if (finalSelectedGear != currentDragRoundedGear) { // If snapping changed the integer gear
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            // Always report the final selected integer gear after snapping if it's different from last reported
                            if (finalSelectedGear != lastReportedGear) {
                                onGearSelected(finalSelectedGear)
                                lastReportedGear = finalSelectedGear
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