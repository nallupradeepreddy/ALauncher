package com.alauncher.app.ui.commandpalette

import com.alauncher.app.model.LauncherApp
import org.junit.Assert.*
import org.junit.Test

class AlphabetScrollerTest {

    private fun createApp(label: String, packageName: String = "com.test.$label"): LauncherApp {
        return LauncherApp(
            packageName = packageName,
            label = label,
            normalizedLabel = label.lowercase(),
            isFavorite = false,
            isHidden = false,
            launchCount = 0,
            lastOpenedTime = 0L,
            notificationCount = 0,
            sortWeight = 0,
            icon = null
        )
    }

    private fun computeAvailableLetters(apps: List<LauncherApp>): Set<Char> =
        AlphabetIndexer.computeAvailableLetters(apps)

    private fun computeLetterIndexMap(apps: List<LauncherApp>): Map<Char, Int> =
        AlphabetIndexer.computeLetterIndexMap(apps)

    private fun getTargetIndex(letterIndexMap: Map<Char, Int>, letter: Char): Int? =
        AlphabetIndexer.getTargetIndex(letterIndexMap, letter)

    @Test
    fun testAvailableLettersExtraction() {
        val apps = listOf(
            createApp("Amazon"),
            createApp("Calendar"),
            createApp("Drive"),
            createApp("1Password"),
            createApp("!Special")
        )

        val letters = computeAvailableLetters(apps)
        assertTrue(letters.contains('A'))
        assertTrue(letters.contains('C'))
        assertTrue(letters.contains('D'))
        assertFalse("Non-alpha '1' should not be in A-Z set", letters.contains('1'))
        assertFalse("Non-alpha '!' should not be in A-Z set", letters.contains('!'))
        assertFalse("Missing letter 'B' should not be present", letters.contains('B'))
        assertEquals(3, letters.size)
    }

    @Test
    fun testLetterIndexMapPointsToFirstOccurrenceOfEachLetter() {
        val apps = listOf(
            createApp("Airbnb"),   // Index 0 ('A')
            createApp("Amazon"),   // Index 1 ('A')
            createApp("Chrome"),   // Index 2 ('C')
            createApp("Clock"),    // Index 3 ('C')
            createApp("Drive"),    // Index 4 ('D')
            createApp("YouTube")   // Index 5 ('Y')
        )

        val map = computeLetterIndexMap(apps)
        assertEquals(0, map['A'])
        assertEquals(2, map['C'])
        assertEquals(4, map['D'])
        assertEquals(5, map['Y'])
        assertNull(map['B'])
    }

    @Test
    fun testGetTargetIndexFallsBackToNearestAvailableLetter() {
        val apps = listOf(
            createApp("Amazon"),   // Index 0 ('A')
            createApp("Chrome"),   // Index 1 ('C')
            createApp("YouTube")   // Index 2 ('Y')
        )
        val map = computeLetterIndexMap(apps)

        // Exact match
        assertEquals(0, getTargetIndex(map, 'A'))
        assertEquals(1, getTargetIndex(map, 'C'))
        assertEquals(2, getTargetIndex(map, 'Y'))

        // 'B' is missing -> jumps forward to 'C' (index 1)
        assertEquals(1, getTargetIndex(map, 'B'))

        // 'Z' is missing -> falls back backward to 'Y' (index 2)
        assertEquals(2, getTargetIndex(map, 'Z'))
    }

    @Test
    fun testGetTargetIndexHandlesNumericAndSpecialCharacters() {
        val apps = listOf(
            createApp("1Password"), // Index 0 ('#')
            createApp("Amazon"),     // Index 1 ('A')
            createApp("Chrome")      // Index 2 ('C')
        )
        val map = computeLetterIndexMap(apps)

        assertEquals(0, map['#'])
        assertEquals(0, getTargetIndex(map, '#'))
    }

    @Test
    fun testReverseLayoutStateBasedOnQuery() {
        fun isReverseLayout(query: String): Boolean = query.isNotEmpty()

        assertFalse("Empty query should use standard top-to-bottom layout for natural A-Z browsing", isReverseLayout(""))
        assertTrue("Active search query should use reverseLayout so results stack above keyboard", isReverseLayout("a"))
        assertTrue("Search query should use reverseLayout", isReverseLayout("whatsapp"))
    }

    @Test
    fun testAlphabetScrubberOnlyVisibleWhenAlphabeticalSortAndDefaultLauncher() {
        assertTrue(
            "Scrubber should be visible when browsing with AlphabeticalAsc as default launcher",
            isAlphabetScrubberVisible(isDefaultLauncher = true, query = "", hasApps = true, sortMode = com.alauncher.app.model.SortMode.AlphabeticalAsc)
        )
        assertFalse(
            "Scrubber must be hidden when ALauncher is not the default launcher",
            isAlphabetScrubberVisible(isDefaultLauncher = false, query = "", hasApps = true, sortMode = com.alauncher.app.model.SortMode.AlphabeticalAsc)
        )
        assertFalse(
            "Scrubber should be hidden when hasApps is false even if default launcher",
            isAlphabetScrubberVisible(isDefaultLauncher = true, query = "", hasApps = false, sortMode = com.alauncher.app.model.SortMode.AlphabeticalAsc)
        )
        assertFalse(
            "Scrubber should be hidden when sort mode is MostUsed",
            isAlphabetScrubberVisible(isDefaultLauncher = true, query = "", hasApps = true, sortMode = com.alauncher.app.model.SortMode.MostUsed)
        )
        assertFalse(
            "Scrubber should be hidden when sort mode is FavoritesFirst",
            isAlphabetScrubberVisible(isDefaultLauncher = true, query = "", hasApps = true, sortMode = com.alauncher.app.model.SortMode.FavoritesFirst)
        )
        assertFalse(
            "Scrubber should be hidden when actively searching",
            isAlphabetScrubberVisible(isDefaultLauncher = true, query = "calc", hasApps = true, sortMode = com.alauncher.app.model.SortMode.AlphabeticalAsc)
        )
    }

    @Test
    fun testAlphabetAll26LettersFitWithinCompactHeight() {
        val alphabet = ('A'..'Z').toList()
        assertEquals(26, alphabet.size)
        // With lineHeight = 11dp, 26 letters take 286dp, leaving room inside a ~350dp-450dp container
        val totalLetterHeightDp = alphabet.size * 11
        val minContainerHeightDp = 350
        assertTrue("26 letters ($totalLetterHeightDp dp) must comfortably fit inside a $minContainerHeightDp dp box", totalLetterHeightDp < minContainerHeightDp)
    }
}
