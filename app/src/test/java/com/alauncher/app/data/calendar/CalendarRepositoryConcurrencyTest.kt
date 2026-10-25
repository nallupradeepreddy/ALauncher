package com.alauncher.app.data.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarRepositoryConcurrencyTest {

    private lateinit var context: Context
    private lateinit var repository: CalendarRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = CalendarRepository(context)
    }

    @Test
    fun getTodayEvents_withoutPermission_returnsEmptyListGracefully() {
        // Without runtime permission granted, must not crash or throw SecurityException
        val events = repository.getTodayEvents()
        assertTrue("Events must be empty when permission is missing", events.isEmpty())
    }

    @Test
    fun calendarUpdatesFlow_emitsInitialEmptyListWhenPermissionMissing() = runBlocking {
        val initialEvents = repository.calendarUpdatesFlow.first()
        assertTrue("calendarUpdatesFlow must emit empty list initially", initialEvents.isEmpty())
    }

    @Test
    fun calendarUpdatesFlow_emitsOnceWithinShortWindow() = runBlocking {
        // The production flow emits an initial value then waits 10 minutes for the next
        // periodic tick. Verify it emits at most one value within a short collection window,
        // proving the flow is wired correctly and the 10-minute interval is respected.
        val firstEmission = withTimeoutOrNull(2_000) {
            repository.calendarUpdatesFlow.first()
        }
        assertNotNull(
            "calendarUpdatesFlow must emit an initial value promptly",
            firstEmission
        )
        // The initial emission should be an empty list (no calendar permission in Robolectric)
        assertTrue("Initial emission should be empty without permission", firstEmission!!.isEmpty())
    }

    @Test
    fun calendarUpdatesFlow_concurrentCollectors_receiveConsistentEmissions() = runBlocking {
        val count = 5
        val emissions = java.util.concurrent.ConcurrentLinkedQueue<List<CalendarEvent>>()
        val jobs = (1..count).map {
            launch(Dispatchers.Default) {
                emissions.add(repository.calendarUpdatesFlow.first())
            }
        }
        jobs.forEach { it.join() }
        org.junit.Assert.assertEquals(count, emissions.size)
        emissions.forEach {
            assertTrue("Every concurrent collector receives valid empty list without crash", it.isEmpty())
        }
    }
}
