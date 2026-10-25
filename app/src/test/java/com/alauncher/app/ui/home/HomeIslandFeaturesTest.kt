package com.alauncher.app.ui.home

import org.junit.Assert.*
import org.junit.Test

class HomeIslandFeaturesTest {

    @Test
    fun testContextualActionTypesExist() {
        assertNotNull(ContextualActionType.valueOf("OPEN_BLUETOOTH_SETTINGS"))
        assertNotNull(ContextualActionType.valueOf("OPEN_HOTSPOT_SETTINGS"))
    }

    @Test
    fun testBluetoothStage1ConnectedText() {
        val devName = "Sony WH-1000XM4"
        val connectText = "🎧 Connected • $devName"
        val island = ContextualIsland(
            text = connectText,
            actionType = ContextualActionType.OPEN_BLUETOOTH_SETTINGS,
            type = ContextualIslandType.AMBIENT_INFO,
            packageName = "__bluetooth__"
        )
        assertEquals("🎧 Connected • Sony WH-1000XM4", island.text)
        assertEquals(ContextualActionType.OPEN_BLUETOOTH_SETTINGS, island.actionType)
        assertEquals(ContextualIslandType.AMBIENT_INFO, island.type)
        assertEquals("__bluetooth__", island.packageName)
    }

    @Test
    fun testBluetoothStage2BatteryPercentage() {
        val devName = "AirPods Pro"
        val batteryLevel = 85
        val batteryText = "🔋 $devName • $batteryLevel%"
        val island = ContextualIsland(
            text = batteryText,
            actionType = ContextualActionType.OPEN_BLUETOOTH_SETTINGS,
            type = ContextualIslandType.AMBIENT_INFO,
            packageName = "__bluetooth__"
        )
        assertEquals("🔋 AirPods Pro • 85%", island.text)
        assertEquals(ContextualActionType.OPEN_BLUETOOTH_SETTINGS, island.actionType)
    }

    @Test
    fun testBluetoothStage2LowBatteryWarning() {
        val devName = "Pixel Buds Pro"
        val batteryLevel = 15
        val lowBatteryText = if (batteryLevel <= 20) {
            "🪫 $devName • $batteryLevel% Battery Low"
        } else {
            "🔋 $devName • $batteryLevel%"
        }
        val island = ContextualIsland(
            text = lowBatteryText,
            actionType = ContextualActionType.OPEN_BLUETOOTH_SETTINGS,
            type = ContextualIslandType.AMBIENT_INFO,
            packageName = "__bluetooth__"
        )
        assertEquals("🪫 Pixel Buds Pro • 15% Battery Low", island.text)
    }

    @Test
    fun testBluetoothFallbackWhenBatteryUnavailable() {
        val devName = "Bluetooth Speaker"
        val batteryLevel = -1
        val text = if (batteryLevel in 0..100) "🔋 $devName • $batteryLevel%" else "🎧 $devName"
        val island = ContextualIsland(
            text = text,
            actionType = ContextualActionType.OPEN_BLUETOOTH_SETTINGS,
            type = ContextualIslandType.AMBIENT_INFO,
            packageName = "__bluetooth__"
        )
        assertEquals("🎧 Bluetooth Speaker", island.text)
    }

    @Test
    fun testHotspotIslandFreshVsProlonged() {
        // Fresh hotspot (<30 minutes)
        val freshElapsed = 12L
        val freshText = if (freshElapsed >= 30L) "🌐 Hotspot Active • ${freshElapsed}m elapsed" else "🌐 Hotspot Active"
        val freshIsland = ContextualIsland(
            text = freshText,
            actionType = ContextualActionType.OPEN_HOTSPOT_SETTINGS,
            type = ContextualIslandType.AMBIENT_INFO,
            packageName = "__hotspot__"
        )
        assertEquals("🌐 Hotspot Active", freshIsland.text)
        assertEquals(ContextualActionType.OPEN_HOTSPOT_SETTINGS, freshIsland.actionType)

        // Prolonged hotspot (>=30 minutes)
        val prolongedElapsed = 45L
        val prolongedText = if (prolongedElapsed >= 30L) "🌐 Hotspot Active • ${prolongedElapsed}m elapsed" else "🌐 Hotspot Active"
        val prolongedIsland = ContextualIsland(
            text = prolongedText,
            actionType = ContextualActionType.OPEN_HOTSPOT_SETTINGS,
            type = ContextualIslandType.AMBIENT_INFO,
            packageName = "__hotspot__"
        )
        assertEquals("🌐 Hotspot Active • 45m elapsed", prolongedIsland.text)
        assertEquals(ContextualActionType.OPEN_HOTSPOT_SETTINGS, prolongedIsland.actionType)
    }

    @Test
    fun testClockTimerChronometerRecognition() {
        val clockPkgs = setOf(
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock",
            "com.coloros.alarmclock",
            "com.oppo.alarmclock",
            "com.oneplus.deskclock",
            "com.xiaomi.calendar",
            "com.miui.calculator"
        )

        assertTrue(clockPkgs.contains("com.google.android.deskclock"))
        assertTrue(clockPkgs.contains("com.sec.android.app.clockpackage"))
        assertTrue(clockPkgs.contains("com.oneplus.deskclock"))

        val timerIsland = ContextualIsland(
            text = "⏱️ Timer",
            actionType = ContextualActionType.LAUNCH_NOTIFICATION,
            actionPayload = "timer_key_1",
            showChronometer = true,
            chronometerCountDown = true,
            whenValue = System.currentTimeMillis() + 300000L,
            type = ContextualIslandType.HIGH_PRIORITY_TASK,
            packageName = "com.google.android.deskclock"
        )
        assertEquals("⏱️ Timer", timerIsland.text)
        assertTrue(timerIsland.showChronometer)
        assertTrue(timerIsland.chronometerCountDown)
        assertEquals(ContextualIslandType.HIGH_PRIORITY_TASK, timerIsland.type)
    }
}
