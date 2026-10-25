package com.alauncher.app.ui.home

import com.alauncher.app.data.calendar.CalendarEvent
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.domain.notification.NotificationClassifier
import com.alauncher.app.domain.notification.NotificationParser
import com.alauncher.app.model.LauncherNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the production resolveContextualIsland() method via reflection.
 * Uses Unsafe.allocateInstance to construct HomeViewModel without its constructor,
 * then injects the required parser/classifier fields — matching the existing
 * pattern from ExampleUnitTest.kt.
 */
class HomeViewModelContextualIslandResolutionTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var resolveMethod: java.lang.reflect.Method

    @Before
    fun setUp() {
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            it.get(null) as sun.misc.Unsafe
        }
        viewModel = unsafe.allocateInstance(HomeViewModel::class.java) as HomeViewModel

        // Inject the parser field used by parseLiveTracker
        val parserField = HomeViewModel::class.java.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(viewModel, NotificationParser())

        // Inject the lazy _classifier backing field
        val classifierField = HomeViewModel::class.java.getDeclaredField("_classifier")
        classifierField.isAccessible = true
        classifierField.set(viewModel, NotificationClassifier(NotificationParser()))

        // Inject dismissedTransientNotifKeys (skipped by allocateInstance)
        val dtField = HomeViewModel::class.java.getDeclaredField("_dismissedTransientNotifKeys")
        dtField.isAccessible = true
        dtField.set(viewModel, java.util.concurrent.ConcurrentHashMap.newKeySet<String>())

        // Get the production resolveContextualIsland method
        resolveMethod = HomeViewModel::class.java.getDeclaredMethod(
            "resolveContextualIsland",
            HomeViewModel.BatteryInfo::class.java,
            List::class.java,      // activeNotifications
            Boolean::class.java,   // isDnd
            String::class.java,    // storageWarning
            String::class.java,    // bluetoothText
            Boolean::class.java,   // isHotspotActive
            List::class.java,      // calendarEvents
            SettingsState::class.java,
            String::class.java,    // copiedOtpKey
            Map::class.java        // snoozedKeysMap
        )
        resolveMethod.isAccessible = true
    }

    private fun resolve(
        battery: HomeViewModel.BatteryInfo? = null,
        notifications: List<LauncherNotification> = emptyList(),
        isDnd: Boolean = false,
        storageWarning: String? = null,
        bluetoothText: String? = null,
        isHotspotActive: Boolean = false,
        calendarEvents: List<CalendarEvent> = emptyList(),
        settings: SettingsState = SettingsState(),
        copiedOtpKey: String? = null,
        snoozedKeys: Map<String, Long> = emptyMap()
    ): ContextualIsland? {
        return resolveMethod.invoke(
            viewModel, battery, notifications, isDnd, storageWarning,
            bluetoothText, isHotspotActive, calendarEvents, settings,
            copiedOtpKey, snoozedKeys
        ) as ContextualIsland?
    }

    @Test
    fun lowBattery_producesCriticalAlertIsland() {
        val lowBattery = HomeViewModel.BatteryInfo(
            level = 10, isCharging = false, isFastCharging = false,
            chargeTimeRemainingMs = 0, temperatureCelsius = 30f
        )
        val settings = SettingsState() // all island categories enabled by default

        val island = resolve(battery = lowBattery, settings = settings)

        assertNotNull("Low battery (10%) should produce an island", island)
        assertEquals(ContextualIslandType.CRITICAL_ALERT, island!!.type)
        assertTrue("Island text should mention battery level", island.text.contains("10"))
    }

    @Test
    fun otpCopied_winsOverLowBattery_priorityOrder() {
        val lowBattery = HomeViewModel.BatteryInfo(
            level = 10, isCharging = false, isFastCharging = false,
            chargeTimeRemainingMs = 0, temperatureCelsius = 30f
        )
        val settings = SettingsState()

        // OTP Copied is the highest priority category
        val island = resolve(
            battery = lowBattery,
            copiedOtpKey = "some_otp_key",
            settings = settings
        )

        assertNotNull("Should produce an island when both OTP copied and low battery", island)
        assertEquals(
            "OTP Copied must win over Critical Alert (low battery) in priority",
            ContextualIslandType.OTP,
            island!!.type
        )
    }

    @Test
    fun bluetoothConnected_producesAmbientInfoIsland() {
        val settings = SettingsState()

        val island = resolve(bluetoothText = "Sony WH-1000XM4", settings = settings)

        assertNotNull("Bluetooth text should produce an island", island)
        assertEquals(ContextualIslandType.AMBIENT_INFO, island!!.type)
        assertEquals(ContextualActionType.OPEN_BLUETOOTH_SETTINGS, island.actionType)
    }

    @Test
    fun hotspotActive_producesAmbientInfoIsland() {
        val settings = SettingsState()

        val island = resolve(isHotspotActive = true, settings = settings)

        assertNotNull("Active hotspot should produce an island", island)
        assertEquals(ContextualIslandType.AMBIENT_INFO, island!!.type)
        assertEquals(ContextualActionType.OPEN_HOTSPOT_SETTINGS, island.actionType)
    }
}
