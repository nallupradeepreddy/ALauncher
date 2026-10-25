package com.alauncher.app.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.calendar.CalendarEvent
import com.alauncher.app.data.calendar.CalendarRepository
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.domain.notification.NotificationClassifier
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.domain.notification.NotificationParser
import com.alauncher.app.model.LauncherNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration test for the 12-flow islandFlow combine in HomeViewModel.
 *
 * Verifies:
 * 1. The combine in HomeViewModel registers the exact 12 flows in the exact order:
 *    [battery, notifications, settings, dnd, storage, bluetooth, hotspot,
 *     calendar, otpKey, refreshTrigger, snoozeVersion, tickerFlow].
 * 2. Production transformIslandFlow() executes all positional casts on the 12-flow array,
 *    failing fast with ClassCastException if the type contract is violated.
 * 3. Priority resolution chooses the right island candidate across conflicting triggers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelIslandFlowIntegrationTest {

    private class FakeAppMetadataDao : AppMetadataDao {
        override fun getAllMetadataFlow(): Flow<List<AppMetadataEntity>> = flowOf(emptyList())
        override suspend fun getAllMetadataDirect(): List<AppMetadataEntity> = emptyList()
        override suspend fun getMetadata(packageName: String): AppMetadataEntity? = null
        override suspend fun insertMetadata(metadata: AppMetadataEntity) {}
        override suspend fun updateFavorite(packageName: String, isFavorite: Boolean) {}
        override suspend fun updateHidden(packageName: String, isHidden: Boolean) {}
        override suspend fun incrementLaunch(packageName: String, time: Long): Int = 1
        override suspend fun resetAllAppStats() {}
        override suspend fun deleteMetadata(packageName: String): Int = 1
    }

    private lateinit var app: Application
    private lateinit var appRepo: AppRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var notifRepo: NotificationRepository
    private lateinit var calendarRepo: CalendarRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        settingsRepo = SettingsRepository(app)
        notifRepo = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = settingsRepo,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        appRepo = AppRepository(
            context = app,
            appMetadataDao = FakeAppMetadataDao(),
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        calendarRepo = CalendarRepository(app)

        viewModel = HomeViewModel(
            application = app,
            appRepository = appRepo,
            settingsRepository = settingsRepo,
            notificationRepository = notifRepo,
            calendarRepository = calendarRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Verifies that the production islandFlow combine pipeline registers exactly 12 flows
     * and that each slot corresponds to the exact expected flow instance.
     * If anyone adds, removes, or swaps flows in the combine, this test fails with an
     * exact mismatch report.
     */
    @Test
    fun combineRegistration_matchesExact12FlowPositions() {
        val islandFlowField = HomeViewModel::class.java.getDeclaredField("islandFlow").apply { isAccessible = true }
        val distinctFlow = islandFlowField.get(viewModel)

        // distinctFlow is DistinctFlowImpl, wrapping the upstream CombineFlow
        val upstreamField = distinctFlow.javaClass.getDeclaredField("upstream").apply { isAccessible = true }
        val combineFlow = upstreamField.get(distinctFlow)

        // combineFlow holds the registered flows array
        val flowsField = combineFlow.javaClass.declaredFields.first { it.type.isArray }.apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val registeredFlows = flowsField.get(combineFlow) as Array<Flow<*>>

        assertEquals("islandFlow combine must register exactly 12 source flows", 12, registeredFlows.size)

        // Slot 0: batteryFlow
        val batteryFlowField = HomeViewModel::class.java.getDeclaredField("batteryFlow").apply { isAccessible = true }
        assertSame("Slot 0 must be batteryFlow", batteryFlowField.get(viewModel), registeredFlows[0])

        // Slot 1: notificationRepository.resolvedNotificationsFlow
        assertSame("Slot 1 must be notificationRepository.resolvedNotificationsFlow", notifRepo.resolvedNotificationsFlow, registeredFlows[1])

        // Slot 2: settingsRepository.settingsFlow
        assertSame("Slot 2 must be settingsRepository.settingsFlow", settingsRepo.settingsFlow, registeredFlows[2])

        // Slot 3: dndFlow
        val dndFlowField = HomeViewModel::class.java.getDeclaredField("dndFlow").apply { isAccessible = true }
        assertSame("Slot 3 must be dndFlow", dndFlowField.get(viewModel), registeredFlows[3])

        // Slot 4: storageFlow
        val storageFlowField = HomeViewModel::class.java.getDeclaredField("storageFlow").apply { isAccessible = true }
        assertSame("Slot 4 must be storageFlow", storageFlowField.get(viewModel), registeredFlows[4])

        // Slot 5: bluetoothFlow
        val bluetoothFlowField = HomeViewModel::class.java.getDeclaredField("bluetoothFlow").apply { isAccessible = true }
        assertSame("Slot 5 must be bluetoothFlow", bluetoothFlowField.get(viewModel), registeredFlows[5])

        // Slot 6: hotspotFlow
        val hotspotFlowField = HomeViewModel::class.java.getDeclaredField("hotspotFlow").apply { isAccessible = true }
        assertSame("Slot 6 must be hotspotFlow", hotspotFlowField.get(viewModel), registeredFlows[6])

        // Slot 7: calendarRepository.calendarUpdatesFlow
        assertSame("Slot 7 must be calendarRepository.calendarUpdatesFlow", calendarRepo.calendarUpdatesFlow, registeredFlows[7])

        // Slot 8: _copiedOtpKey
        val otpKeyField = HomeViewModel::class.java.getDeclaredField("_copiedOtpKey").apply { isAccessible = true }
        assertSame("Slot 8 must be _copiedOtpKey", otpKeyField.get(viewModel), registeredFlows[8])

        // Slot 9: _islandRefreshTrigger
        val triggerField = HomeViewModel::class.java.getDeclaredField("_islandRefreshTrigger").apply { isAccessible = true }
        assertSame("Slot 9 must be _islandRefreshTrigger", triggerField.get(viewModel), registeredFlows[9])

        // Slot 10: _snoozeVersion
        val snoozeVerField = HomeViewModel::class.java.getDeclaredField("_snoozeVersion").apply { isAccessible = true }
        assertSame("Slot 10 must be _snoozeVersion", snoozeVerField.get(viewModel), registeredFlows[10])

        // Slot 11: tickerFlow
        val tickerField = HomeViewModel::class.java.getDeclaredField("tickerFlow").apply { isAccessible = true }
        assertSame("Slot 11 must be tickerFlow", tickerField.get(viewModel), registeredFlows[11])
    }

    /**
     * Executes production transformIslandFlow() with a typed 12-element array.
     * Verifies that the production casting logic successfully processes the contract,
     * and that passing an incompatible type triggers ClassCastException directly
     * from production code.
     */
    @Test
    fun transformIslandFlow_executesProductionCastsAndGuardsTypeContract() {
        val validFlows = arrayOfNulls<Any>(12)
        validFlows[0] = HomeViewModel.BatteryInfo(50, false, false, 0, 30f)
        validFlows[1] = emptyList<LauncherNotification>()
        validFlows[2] = SettingsState()
        validFlows[3] = false
        validFlows[4] = null
        validFlows[5] = null
        validFlows[6] = false
        validFlows[7] = emptyList<CalendarEvent>()
        validFlows[8] = null
        validFlows[9] = 0
        validFlows[10] = 0
        validFlows[11] = System.currentTimeMillis()

        // 1. Call production transformIslandFlow with valid array — must not throw ClassCastException
        val result = viewModel.transformIslandFlow(validFlows)
        // With normal battery and no events, result should be null (no island needed)
        // The critical check is that all casts succeeded in production code.

        // 2. Pass incompatible type at slot 0 (String instead of BatteryInfo)
        val invalidFlows = validFlows.clone()
        invalidFlows[0] = "Incompatible String"
        assertThrows("Production transformIslandFlow must throw ClassCastException on mismatched type", ClassCastException::class.java) {
            viewModel.transformIslandFlow(invalidFlows)
        }

        // 3. Pass incompatible type at slot 2 (String instead of SettingsState)
        val invalidSettingsFlows = validFlows.clone()
        invalidSettingsFlows[2] = "Not a SettingsState"
        assertThrows("Production transformIslandFlow must throw ClassCastException on mismatched SettingsState", ClassCastException::class.java) {
            viewModel.transformIslandFlow(invalidSettingsFlows)
        }
    }

    /**
     * Full pipeline test via production transformIslandFlow:
     * Feeds low battery + bluetooth + hotspot + OTP copied into the production transform
     * and verifies that OTP wins according to priority hierarchy.
     */
    @Test
    fun transformIslandFlow_otpCopied_winsOverAllOtherCandidates() {
        val flows = arrayOfNulls<Any>(12)
        flows[0] = HomeViewModel.BatteryInfo(10, false, false, 0, 30f) // Low battery candidate
        flows[1] = emptyList<LauncherNotification>()
        flows[2] = SettingsState()
        flows[3] = false
        flows[4] = null
        flows[5] = "AirPods Pro"                                       // Bluetooth candidate
        flows[6] = true                                                 // Hotspot candidate
        flows[7] = emptyList<CalendarEvent>()
        flows[8] = "some_otp_key"                                       // OTP candidate
        flows[9] = 0
        flows[10] = 0
        flows[11] = System.currentTimeMillis()

        val island = viewModel.transformIslandFlow(flows)

        assertNotNull("Should produce an island when triggers are active", island)
        assertEquals(
            "OTP Copied must be highest priority, beating low battery + bluetooth + hotspot",
            ContextualIslandType.OTP,
            island!!.type
        )
    }
}
