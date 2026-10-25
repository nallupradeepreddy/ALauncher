package com.alauncher.app.ui.settings

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that require the Android framework (Intent, Uri parsing).
 * Separated from [FeedbackUtilsTest] so the pure-Kotlin tests
 * skip Robolectric's SDK bootstrap overhead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackUtilsIntentTest {

    @Test
    fun mailtoUriEncodesSubjectAndBody() {
        val subject = "[ALauncher Bug Report] Crash on rotation"
        val body = "Here is the error & details:\nLine 1\nLine 2"
        val uri = FeedbackUtils.buildMailtoUri(subject, body)

        val uriString = uri.toString()
        assertTrue(uriString.startsWith("mailto:pradeep.alauncher@gmail.com?subject="))
        assertTrue(uriString.contains("subject=%5BALauncher%20Bug%20Report%5D%20Crash%20on%20rotation"))
        assertTrue(uriString.contains("body=Here%20is%20the%20error%20%26%20details%3A"))
    }

    @Test
    fun createEmailIntentUsesActionSendTo() {
        val subject = "Test Subject"
        val body = "Test Body"
        val intent = FeedbackUtils.createEmailIntent(subject, body)

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertNotNull(intent.data)
        assertTrue(intent.data.toString().contains("Test%20Subject"))
        assertEquals(subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(body, intent.getStringExtra(Intent.EXTRA_TEXT))
        val emailArray = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
        assertNotNull(emailArray)
        assertEquals("pradeep.alauncher@gmail.com", emailArray!![0])
    }
}
