package com.alauncher.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackUtilsTest {

    @Test
    fun developerEmailIsExact() {
        assertEquals("pradeep.alauncher@gmail.com", FeedbackUtils.DEVELOPER_EMAIL)
    }

    @Test
    fun subjectFormattingPerCategory() {
        val featureSub = FeedbackUtils.buildSubject(
            FeedbackCategory.FEATURE_REQUEST,
            "Add animated folder preview icons"
        )
        assertEquals("[ALauncher Feature Request] Add animated folder preview icons", featureSub)

        val bugSub = FeedbackUtils.buildSubject(
            FeedbackCategory.BUG_REPORT,
            "App crashes when rotating on cover screen"
        )
        assertEquals("[ALauncher Bug Report] App crashes when rotating on cover screen", bugSub)

        val generalSub = FeedbackUtils.buildSubject(
            FeedbackCategory.GENERAL_FEEDBACK,
            "Loving the minimalist design!"
        )
        assertEquals("[ALauncher General Feedback] Loving the minimalist design!", generalSub)
    }

    @Test
    fun subjectTruncatesLongFirstLine() {
        val longMessage = "This is a very detailed description that exceeds forty characters by quite a bit"
        val subject = FeedbackUtils.buildSubject(FeedbackCategory.FEATURE_REQUEST, longMessage)
        assertTrue(subject.startsWith("[ALauncher Feature Request] This is a very detailed description"))
        assertTrue(subject.endsWith("…"))
    }

    @Test
    fun subjectFallsBackToCategoryTagWhenMessageBlank() {
        val subject = FeedbackUtils.buildSubject(FeedbackCategory.BUG_REPORT, "   \n  ")
        assertEquals("[ALauncher Bug Report] Bug Report", subject)
    }

    @Test
    fun bodyIncludesDiagnosticsWhenEnabled() {
        val body = FeedbackUtils.buildBody(
            category = FeedbackCategory.FEATURE_REQUEST,
            message = "Please add custom font support",
            includeDiagnostics = true,
            appVersion = "1.1.8 (9)",
            androidVersion = "14 (API 34)",
            deviceModel = "Google Pixel 8",
            isDefaultLauncher = true
        )

        assertTrue(body.contains("Category: Feature Request"))
        assertTrue(body.contains("Please add custom font support"))
        assertTrue(body.contains("--- Device Information ---"))
        assertTrue(body.contains("App Version: 1.1.8 (9)"))
        assertTrue(body.contains("Android OS: 14 (API 34)"))
        assertTrue(body.contains("Device: Google Pixel 8"))
        assertTrue(body.contains("Default Launcher: Yes"))
    }

    @Test
    fun bodyExcludesDiagnosticsWhenDisabled() {
        val body = FeedbackUtils.buildBody(
            category = FeedbackCategory.GENERAL_FEEDBACK,
            message = "Great work!",
            includeDiagnostics = false
        )

        assertTrue(body.contains("Category: General Feedback"))
        assertTrue(body.contains("Great work!"))
        assertFalse(body.contains("--- Device Information ---"))
        assertFalse(body.contains("Android OS:"))
    }

    @Test
    fun messageValidationBounds() {
        assertFalse("Empty string must be invalid", FeedbackUtils.isMessageValid(""))
        assertFalse("Whitespace string must be invalid", FeedbackUtils.isMessageValid("    \n "))
        assertFalse("4 characters must be invalid", FeedbackUtils.isMessageValid("1234"))
        assertTrue("5 characters must be valid", FeedbackUtils.isMessageValid("12345"))
        assertTrue("4000 characters must be valid", FeedbackUtils.isMessageValid("a".repeat(4000)))
        assertFalse("4001 characters must be invalid", FeedbackUtils.isMessageValid("a".repeat(4001)))
    }

    @Test
    fun subjectExactFiftyCharBoundary() {
        val exactly50 = "12345678901234567890123456789012345678901234567890"
        val subject50 = FeedbackUtils.buildSubject(FeedbackCategory.FEATURE_REQUEST, exactly50)
        assertEquals("[ALauncher Feature Request] $exactly50", subject50)

        val fiftyOne = "123456789012345678901234567890123456789012345678901"
        val subject51 = FeedbackUtils.buildSubject(FeedbackCategory.FEATURE_REQUEST, fiftyOne)
        assertEquals("[ALauncher Feature Request] ${fiftyOne.take(50)}…", subject51)
    }

    @Test
    fun subjectMultilineExtractsFirstNonBlankLine() {
        val multiline = "\n   \nFirst actual line of message\nSecond line"
        val subject = FeedbackUtils.buildSubject(FeedbackCategory.BUG_REPORT, multiline)
        assertEquals("[ALauncher Bug Report] First actual line of message", subject)
    }
}
