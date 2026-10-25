package com.alauncher.app.domain.app

import com.alauncher.app.model.LauncherApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppTrieSearchIndexTest {

    private lateinit var searchIndex: AppTrieSearchIndex

    private fun createApp(
        packageName: String,
        label: String,
        isFavorite: Boolean = false,
        launchCount: Int = 0
    ): LauncherApp {
        return LauncherApp(
            packageName = packageName,
            label = label,
            normalizedLabel = label.lowercase(),
            isFavorite = isFavorite,
            launchCount = launchCount
        )
    }

    @Before
    fun setUp() {
        searchIndex = AppTrieSearchIndex()
    }

    // --- 1. Accent & Diacritics Normalization Tests ---

    @Test
    fun `test accented app found with unaccented and accented queries`() {
        val apps = listOf(
            createApp("com.android.phone", "Téléphone"),
            createApp("com.spotify.music", "Música"),
            createApp("com.ubereats", "Über Eats"),
            createApp("com.android.settings", "Configuración")
        )
        searchIndex.rebuildIndex(apps)

        // Téléphone
        val resTele = searchIndex.search("tele", apps)
        assertTrue("Expected to find Téléphone with 'tele'", resTele.any { it.packageName == "com.android.phone" })

        val resTeleAccented = searchIndex.search("télé", apps)
        assertTrue("Expected to find Téléphone with 'télé'", resTeleAccented.any { it.packageName == "com.android.phone" })

        val resPhone = searchIndex.search("phone", apps)
        assertTrue("Expected to find Téléphone with 'phone'", resPhone.any { it.packageName == "com.android.phone" })

        // Música
        val resMusica = searchIndex.search("musica", apps)
        assertTrue("Expected to find Música with 'musica'", resMusica.any { it.packageName == "com.spotify.music" })

        val resMusicaAccented = searchIndex.search("música", apps)
        assertTrue("Expected to find Música with 'música'", resMusicaAccented.any { it.packageName == "com.spotify.music" })

        // Über Eats
        val resUber = searchIndex.search("uber", apps)
        assertTrue("Expected to find Über Eats with 'uber'", resUber.any { it.packageName == "com.ubereats" })

        val resUberAccented = searchIndex.search("über", apps)
        assertTrue("Expected to find Über Eats with 'über'", resUberAccented.any { it.packageName == "com.ubereats" })

        // Configuración
        val resConfig = searchIndex.search("configuracion", apps)
        assertTrue("Expected to find Configuración with 'configuracion'", resConfig.any { it.packageName == "com.android.settings" })
    }

    // --- 2. Non-Latin Script Tests (Cyrillic, Greek, Arabic, Chinese, Japanese, Hindi) ---

    @Test
    fun `test Cyrillic apps search and word matching`() {
        val apps = listOf(
            createApp("ru.yandex.maps", "Яндекс Карты"),
            createApp("com.android.settings", "Настройки"),
            createApp("com.vkontakte.android", "ВКонтакте")
        )
        searchIndex.rebuildIndex(apps)

        // Full word prefix
        val resYandex = searchIndex.search("яндекс", apps)
        assertEquals("ru.yandex.maps", resYandex.firstOrNull()?.packageName)

        // Sub-word prefix (Карты / maps)
        val resMaps = searchIndex.search("карты", apps)
        assertTrue("Expected sub-word match for карты", resMaps.any { it.packageName == "ru.yandex.maps" })

        // Partial prefix
        val resSettings = searchIndex.search("настр", apps)
        assertEquals("com.android.settings", resSettings.firstOrNull()?.packageName)
    }

    @Test
    fun `test Greek, Arabic, Chinese, Japanese, and Hindi scripts`() {
        val apps = listOf(
            createApp("com.greek.settings", "Ρυθμίσεις"),
            createApp("com.google.maps.ar", "خرائط جوجل"),
            createApp("com.tencent.mm", "微信"),
            createApp("jp.calendar", "カレンダー"),
            createApp("in.messages", "संदेश")
        )
        searchIndex.rebuildIndex(apps)

        // Greek
        val resGreek = searchIndex.search("ρυθμ", apps)
        assertEquals("com.greek.settings", resGreek.firstOrNull()?.packageName)

        // Arabic
        val resArabic = searchIndex.search("خرائط", apps)
        assertEquals("com.google.maps.ar", resArabic.firstOrNull()?.packageName)

        // Chinese
        val resChinese = searchIndex.search("微", apps)
        assertEquals("com.tencent.mm", resChinese.firstOrNull()?.packageName)

        // Japanese
        val resJapanese = searchIndex.search("カレン", apps)
        assertEquals("jp.calendar", resJapanese.firstOrNull()?.packageName)

        // Hindi
        val resHindi = searchIndex.search("संद", apps)
        assertEquals("in.messages", resHindi.firstOrNull()?.packageName)
    }

    // --- 3. Spaceless and CamelCase Tests ---

    @Test
    fun `test spaceless query matching across English and accented apps`() {
        val apps = listOf(
            createApp("com.android.vending", "Google Play Store"),
            createApp("com.lemon.lvoverseas", "CapCut"),
            createApp("com.accent.phone", "Télé Phone")
        )
        searchIndex.rebuildIndex(apps)

        val resPlayStore = searchIndex.search("playstore", apps)
        assertEquals("com.android.vending", resPlayStore.firstOrNull()?.packageName)

        val resCapCut = searchIndex.search("capcut", apps)
        assertEquals("com.lemon.lvoverseas", resCapCut.firstOrNull()?.packageName)

        val resTelePhone = searchIndex.search("telephone", apps)
        assertEquals("com.accent.phone", resTelePhone.firstOrNull()?.packageName)
    }

    @Test
    fun `test initials matching`() {
        val apps = listOf(
            createApp("com.rockstar.gta", "Grand Theft Auto"),
            createApp("com.google.android.apps.youtube.music", "YouTube Music")
        )
        searchIndex.rebuildIndex(apps)

        val resGta = searchIndex.search("gta", apps)
        assertEquals("com.rockstar.gta", resGta.firstOrNull()?.packageName)

        val resYtm = searchIndex.search("ytm", apps)
        assertEquals("com.google.android.apps.youtube.music", resYtm.firstOrNull()?.packageName)
    }

    // --- 4. Auto-Launch Candidate Resolution Tests ---

    @Test
    fun `test resolveAutoLaunchCandidate with accented and unaccented exact matches`() {
        val musicaApp = createApp("com.spotify.music", "Música")
        val otherApp = createApp("com.other.app", "Music Player")
        val apps = listOf(musicaApp, otherApp)

        // Unaccented exact match "musica" -> Música
        val targetUnaccented = resolveAutoLaunchCandidate(apps, "musica")
        assertNotNull("Expected exact match auto-launch for 'musica'", targetUnaccented)
        assertEquals("com.spotify.music", targetUnaccented?.packageName)

        // Accented exact match "música" -> Música
        val targetAccented = resolveAutoLaunchCandidate(apps, "música")
        assertNotNull("Expected exact match auto-launch for 'música'", targetAccented)
        assertEquals("com.spotify.music", targetAccented?.packageName)
    }

    @Test
    fun `test resolveAutoLaunchCandidate with single remaining candidate`() {
        val youtubeApp = createApp("com.google.android.youtube", "YouTube")
        val apps = listOf(youtubeApp)

        val target = resolveAutoLaunchCandidate(apps, "yo")
        assertNotNull(target)
        assertEquals("com.google.android.youtube", target?.packageName)
    }

    @Test
    fun `test resolveAutoLaunchCandidate does not trigger when multiple candidates remain and no exact match`() {
        val apps = listOf(
            createApp("com.amazon.mShop", "Amazon"),
            createApp("com.amazon.mp3", "Amazon Music")
        )

        val target = resolveAutoLaunchCandidate(apps, "ama")
        assertNull("Should not auto-launch partial match when multiple candidates exist", target)

        val exactTargetAmbiguous = resolveAutoLaunchCandidate(apps, "amazon")
        assertNull("Should NOT auto-launch exact match when prefix conflicts with other candidates", exactTargetAmbiguous)

        val exactTargetSingle = resolveAutoLaunchCandidate(listOf(apps[0]), "amazon")
        assertNotNull("Should auto-launch exact match when unambiguous", exactTargetSingle)
        assertEquals("com.amazon.mShop", exactTargetSingle?.packageName)
    }

    @Test
    fun `test resolveAutoLaunchCandidate guards against queries shorter than 2 characters`() {
        val apps = listOf(createApp("com.google.android.youtube", "YouTube"))

        val target = resolveAutoLaunchCandidate(apps, "y")
        assertNull("Should not auto-launch query with length < 2", target)
    }

    // --- 5. Direct Normalizer & Ranking Precedence Tests ---

    @Test
    fun `test normalizeForSearch in isolation`() {
        assertEquals("", AppTrieSearchIndex.normalizeForSearch(""))
        assertEquals("instagram", AppTrieSearchIndex.normalizeForSearch("Instagram"))
        assertEquals("telephone", AppTrieSearchIndex.normalizeForSearch("Téléphone"))
        assertEquals("uber", AppTrieSearchIndex.normalizeForSearch("Über"))
        assertEquals("naive", AppTrieSearchIndex.normalizeForSearch("naïve"))
        assertEquals("musica", AppTrieSearchIndex.normalizeForSearch("Música"))
        assertEquals("яндекс", AppTrieSearchIndex.normalizeForSearch("Яндекс"))
        assertEquals("微信", AppTrieSearchIndex.normalizeForSearch("微信"))
    }

    @Test
    fun `test search scoring orders prefix match above contains match`() {
        val appPrefix = createApp("com.android.vending", "Play Store")
        val appContains = createApp("com.display.settings", "Display")
        val appSuffix = createApp("com.google.music", "Google Play Music")
        val apps = listOf(appContains, appSuffix, appPrefix)

        searchIndex.rebuildIndex(apps)

        val results = searchIndex.search("play", apps)
        assertEquals("Expected 3 matches for 'play'", 3, results.size)
        // 'Play Store' starts with 'play' -> score 90.0, ranks #1
        assertEquals("com.android.vending", results[0].packageName)
        // 'Google Play Music' contains 'play' as a word suffix -> score 85.0, ranks #2
        assertEquals("com.google.music", results[1].packageName)
        // 'Display' contains 'play' as internal substring -> score 50.0, ranks #3
        assertEquals("com.display.settings", results[2].packageName)
    }

    @Test
    fun `test resolveAutoLaunchCandidate with whitespace or symbols`() {
        val apps = listOf(createApp("com.google.android.youtube", "YouTube"))

        assertNull(resolveAutoLaunchCandidate(apps, "   "))
        assertNull(resolveAutoLaunchCandidate(apps, " "))
    }
}
