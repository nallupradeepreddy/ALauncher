package com.alauncher.app.domain.app

import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.SortMode
import com.alauncher.app.ui.commandpalette.CommandPaletteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSortingAndAutoLaunchTest {

    /**
     * Calls the production CommandPaletteViewModel.sortAppsList via reflection.
     * The method is private but is a pure function (reads only its arguments, no field access),
     * so Unsafe.allocateInstance is safe — no constructor side effects needed.
     */
    @Suppress("UNCHECKED_CAST")
    private fun sortAppsViaProduction(list: List<LauncherApp>, mode: SortMode): List<LauncherApp> {
        val vm = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            it.get(null) as sun.misc.Unsafe
        }.allocateInstance(CommandPaletteViewModel::class.java) as CommandPaletteViewModel

        val method = CommandPaletteViewModel::class.java.getDeclaredMethod(
            "sortAppsList", List::class.java, SortMode::class.java
        )
        method.isAccessible = true
        return method.invoke(vm, list, mode) as List<LauncherApp>
    }

    @Test
    fun testAllSortingModes() {
        val appA = LauncherApp("pkg.a", "WhatsApp", "whatsapp", isFavorite = false, launchCount = 10, lastOpenedTime = 1000L)
        val appB = LauncherApp("pkg.b", "Instagram", "instagram", isFavorite = true, launchCount = 5, lastOpenedTime = 3000L)
        val appC = LauncherApp("pkg.c", "Chrome", "chrome", isFavorite = false, launchCount = 20, lastOpenedTime = 2000L)

        val apps = listOf(appA, appB, appC)

        // Alphabetical Asc
        val sortedAsc = sortAppsViaProduction(apps, SortMode.AlphabeticalAsc)
        assertEquals("Chrome", sortedAsc[0].label)
        assertEquals("Instagram", sortedAsc[1].label)
        assertEquals("WhatsApp", sortedAsc[2].label)

        // Alphabetical Desc
        val sortedDesc = sortAppsViaProduction(apps, SortMode.AlphabeticalDesc)
        assertEquals("WhatsApp", sortedDesc[0].label)
        assertEquals("Instagram", sortedDesc[1].label)
        assertEquals("Chrome", sortedDesc[2].label)

        // Favorites First
        val sortedFavFirst = sortAppsViaProduction(apps, SortMode.FavoritesFirst)
        assertTrue("First item must be favorite", sortedFavFirst[0].isFavorite)
        assertEquals("Instagram", sortedFavFirst[0].label)

        // Most Used
        val sortedMostUsed = sortAppsViaProduction(apps, SortMode.MostUsed)
        assertEquals("Chrome", sortedMostUsed[0].label) // 20
        assertEquals("WhatsApp", sortedMostUsed[1].label) // 10
        assertEquals("Instagram", sortedMostUsed[2].label) // 5

        // Recently Opened
        val sortedRecent = sortAppsViaProduction(apps, SortMode.RecentlyOpened)
        assertEquals("Instagram", sortedRecent[0].label) // 3000L
        assertEquals("Chrome", sortedRecent[1].label) // 2000L
        assertEquals("WhatsApp", sortedRecent[2].label) // 1000L
    }

    @Test
    fun testAutoLaunchCandidateResolution() {
        val appAmazon = LauncherApp("in.amazon.mShop.android.shopping", "Amazon", "amazon")
        val appAmazonMusic = LauncherApp("com.amazon.mp3", "Amazon Music", "amazon music")
        val appCapCut = LauncherApp("com.lemon.lvoverseas", "CapCut", "capcut")
        val appA = LauncherApp("com.example.a", "A", "a")

        // 1. Single character query should NEVER auto-launch
        assertNull("Single character query 'a' should not auto-launch", resolveAutoLaunchCandidate(listOf(appA), "a"))

        // 2. Exact match on unique candidate should auto-launch
        assertEquals(appCapCut, resolveAutoLaunchCandidate(listOf(appCapCut), "capcut"))

        // 3. Exact match with prefix ambiguity (e.g. Amazon when Amazon Music exists) should NOT auto-launch
        assertNull("Amazon should not auto-launch when Amazon Music exists", resolveAutoLaunchCandidate(listOf(appAmazon, appAmazonMusic), "amazon"))

        // 3b. Unambiguous exact match when no longer prefix exists should auto-launch
        assertEquals(appAmazon, resolveAutoLaunchCandidate(listOf(appAmazon), "amazon"))

        // 4. Ambiguous partial match with no exact match should not auto-launch
        assertNull(resolveAutoLaunchCandidate(listOf(appAmazon, appAmazonMusic), "amaz"))
    }
}
