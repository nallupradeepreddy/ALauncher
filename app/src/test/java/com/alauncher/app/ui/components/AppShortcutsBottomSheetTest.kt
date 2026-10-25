package com.alauncher.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShortcutsBottomSheetTest {

    @Test
    fun testAppInfoIntentCreation() {
        val targetPackage = "com.spotify.music"
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", targetPackage, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:$targetPackage", intent.data.toString())
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun testUninstallAppIntentCreation() {
        val targetPackage = "com.superfluous.game"
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$targetPackage")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:$targetPackage", intent.data.toString())
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }
}
