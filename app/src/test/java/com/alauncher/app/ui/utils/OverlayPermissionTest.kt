package com.alauncher.app.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayPermissionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test createManageOverlayIntent for specific package targets app overlay settings`() {
        val targetPkg = "com.distracting.overlayapp"
        val intent = createManageOverlayIntent(targetPkg)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals(Uri.parse("package:$targetPkg"), intent.data)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `test createManageOverlayIntent for null package targets global overlay settings`() {
        val intent = createManageOverlayIntent(null)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertNull(intent.data)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `test hasOverlayPermission and getAppsWithOverlayPermission execute safely`() {
        // Robolectric environment default
        val hasPermission = hasOverlayPermission(context, context.packageName)
        // Should execute without throwing any exception
        assertNotNull(hasPermission)

        val overlayApps = getAppsWithOverlayPermission(context)
        assertNotNull(overlayApps)
        assertFalse("ALauncher itself should be excluded from overlay apps list", overlayApps.contains(context.packageName))
    }

    @Test
    fun `test createManageOverlayIntent with malformed package falls back to global settings`() {
        val maliciousPkg = "javascript:alert(1)"
        val intent = createManageOverlayIntent(maliciousPkg)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertNull("Malformed package must not be embedded into package: URI", intent.data)

        val emptyPkgIntent = createManageOverlayIntent("   ")
        assertNull(emptyPkgIntent.data)
    }

    @Test
    fun `test isValidPackageName detects valid and invalid formats`() {
        assertTrue(isValidPackageName("com.example.app"))
        assertTrue(isValidPackageName("org.whatsapp_messenger.sub_app"))
        assertFalse(isValidPackageName("invalid/path"))
        assertFalse(isValidPackageName(".."))
        assertFalse(isValidPackageName(""))
        assertFalse(isValidPackageName(null))
    }

    @Test
    fun `test createManageOverlayIntent with non-installed package and context falls back to global settings`() {
        val fakePkg = "com.nonexistent.fake.app"
        val intent = createManageOverlayIntent(fakePkg, context)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertNull("Non-installed package verified with context must fall back to global settings", intent.data)
    }
}
