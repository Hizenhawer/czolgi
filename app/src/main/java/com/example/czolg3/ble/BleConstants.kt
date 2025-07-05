package com.example.czolg3.ble

import java.util.UUID

object BleConstants {
    // *** REPLACE WITH YOUR ESP32's DETAILS ***
    const val ESP32_DEVICE_NAME = "Tank_2.0" // Or whatever name your ESP32 advertises
    val ECHO_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789000") // *** REPLACE THIS ***
    val ECHO_CHARACTERISTIC_UUID: UUID = UUID.fromString("87654321-4321-4321-4321-ba0987654321") // *** REPLACE THIS ***

    // Standard Client Characteristic Configuration Descriptor UUID
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Command strings for the lights loop
    const val LIGHTS_ON_COMMAND = "r"
    const val LIGHTS_OFF_COMMAND = "t"

    // Intervals and periods
    const val COMMAND_INTERVAL_MS = 2000L // Interval for the lights command loop
    const val SCAN_PERIOD_MS: Long = 10000 // Stop scanning after 10 seconds
}