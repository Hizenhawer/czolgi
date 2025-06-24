package com.example.czolg3.ble // Adjust package name as needed

import kotlinx.coroutines.*

class CommandLoopController(
    private val scope: CoroutineScope, // Scope from the ViewModel or another lifecycle-aware component
    private val commandSender: suspend (command: String) -> Boolean,
    private val logUpdater: (String) -> Unit
) {
    private var commandLoopJob: Job? = null
    private var isLoopActiveInternal: Boolean = false

    val isLoopActive: Boolean
        get() = isLoopActiveInternal

    fun startLightsCommandLoop() {
        if (isLoopActiveInternal) {
            logUpdater("Lights command loop already active.")
            return
        }

        isLoopActiveInternal = true
        logUpdater("Starting lights command loop...")
        commandLoopJob?.cancel() // Cancel any existing loop
        commandLoopJob = scope.launch(Dispatchers.Default) { // Ensure it runs on a background thread
            var lightsOn = true
            try {
                while (isActive && isLoopActiveInternal) { // Loop while coroutine is active and loop is enabled
                    val command = if (lightsOn) BleConstants.LIGHTS_ON_COMMAND else BleConstants.LIGHTS_OFF_COMMAND
                    logUpdater("Loop: Sending command: $command")
                    val success = commandSender(command)
                    if (!success) {
                        logUpdater("Loop: Failed to send command '$command'. Stopping loop.")
                        stopLightsCommandLoopInternal(initiatedByError = true)
                        break // Exit loop on send failure
                    }
                    lightsOn = !lightsOn // Toggle state
                    delay(BleConstants.COMMAND_INTERVAL_MS) // Wait for the interval
                }
            } catch (e: CancellationException) {
                logUpdater("Lights command loop was cancelled.")
                // No need to call stopLightsCommandLoopInternal here as cancellation handles it
            } finally {
                if (isLoopActiveInternal && !isActive) { // If loop was active but coroutine isn't (e.g. scope cancelled)
                    isLoopActiveInternal = false
                }
                logUpdater("Lights command loop processing finished.")
            }
        }
    }

    private fun stopLightsCommandLoopInternal(initiatedByError: Boolean = false) {
        if (isLoopActiveInternal) {
            isLoopActiveInternal = false // Set flag first
            commandLoopJob?.cancel()    // Then cancel the job
            commandLoopJob = null
            if (!initiatedByError) { // Avoid double logging if error already logged
                logUpdater("Lights command loop explicitly stopped.")
            }
        }
    }

    fun stopLightsCommandLoop() {
        if (isLoopActiveInternal) {
            logUpdater("Requesting to stop lights command loop...")
            stopLightsCommandLoopInternal()
        } else {
            logUpdater("Lights command loop is not active.")
        }
    }

    // Call this when the owning scope is being cleared (e.g., ViewModel's onCleared)
    fun cleanup() {
        logUpdater("CommandLoopController cleanup: Stopping loop.")
        stopLightsCommandLoopInternal()
    }
}