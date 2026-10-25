package com.alauncher.app.ui.walkthrough

import com.alauncher.app.data.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughStepTest {

    @Test
    fun settingsStateDefaultsToUncompletedWalkthrough() {
        assertEquals(false, SettingsState().hasCompletedWalkthrough)

        val completed = SettingsState().copy(hasCompletedWalkthrough = true)
        assertEquals(true, completed.hasCompletedWalkthrough)
    }

    @Test
    fun walkthroughStepsAreOrderedAndFullyResourced() {
        val steps = WalkthroughStep.entries
        assertEquals("There should be 7 onboarding steps", 7, steps.size)

        steps.forEachIndexed { index, step ->
            assertEquals("Step ordinal must match iteration order", index, step.ordinal)
            assertTrue("badgeRes must be a valid resource id", step.badgeRes != 0)
            assertTrue("titleRes must be a valid resource id", step.titleRes != 0)
            assertTrue("descriptionRes must be a valid resource id", step.descriptionRes != 0)
        }

        assertEquals(WalkthroughStep.WELCOME, steps[0])
        assertEquals(WalkthroughStep.COMMAND_PALETTE, steps[1])
        assertEquals(WalkthroughStep.NOTIFICATION_CENTER, steps[2])
        assertEquals(WalkthroughStep.CONTEXTUAL_ISLAND, steps[3])
        assertEquals(WalkthroughStep.APP_CONTROLS, steps[4])
        assertEquals(WalkthroughStep.THUMB_DOCK_AND_LIMITS, steps[5])
        assertEquals(WalkthroughStep.GET_STARTED, steps[6])
    }

    @Test
    fun getStartedStepIsFinalStep() {
        val lastStep = WalkthroughStep.entries.last()
        assertEquals(WalkthroughStep.GET_STARTED, lastStep)
        assertEquals(6, lastStep.ordinal)
    }

    @Test
    fun walkthroughStepCountIsStrictlyWithinFiveToSevenBounds() {
        val count = WalkthroughStep.entries.size
        assertTrue("Walkthrough must have at least 5 steps", count >= 5)
        assertTrue("Walkthrough must not exceed 7 steps", count <= 7)
    }

    @Test
    fun permissionDisclosureStringsArePresent() {
        assertTrue(com.alauncher.app.R.string.permission_disclosure_title != 0)
        assertTrue(com.alauncher.app.R.string.permission_item_app_library_title != 0)
        assertTrue(com.alauncher.app.R.string.permission_item_app_library_desc != 0)
        assertTrue(com.alauncher.app.R.string.permission_item_notif_filter_title != 0)
        assertTrue(com.alauncher.app.R.string.permission_item_notif_filter_desc != 0)
        assertTrue(com.alauncher.app.R.string.permission_notif_enabled != 0)
        assertTrue(com.alauncher.app.R.string.permission_enable_notif != 0)
        assertTrue(com.alauncher.app.R.string.permission_restricted_settings_hint != 0)
        assertTrue(com.alauncher.app.R.string.permission_restricted_settings_desc != 0)
        assertTrue(com.alauncher.app.R.string.walkthrough_island_enable_permission != 0)
        assertTrue(com.alauncher.app.R.string.walkthrough_island_permission_active != 0)
        assertTrue(com.alauncher.app.R.string.walkthrough_preview_summary_swipe_down != 0)
        assertTrue(com.alauncher.app.R.string.walkthrough_preview_summary_double_tap != 0)
    }
}
