package com.alauncher.app.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * Manages store listing intent navigation for user reviews and ratings.
 * 100% Free and Open Source (FOSS) compliant with zero proprietary Google Play libraries.
 */
object InAppReviewHelper {

    /**
     * Opens the app store listing page for ALauncher across any installed app store or browser.
     * Uses standard Android market:// intent with web fallback, compatible with all app stores
     * (Google Play, F-Droid client, Aurora Store) and web browsers.
     */
    fun openStoreListing(context: Context): Boolean {
        val packageName = context.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            )
        }

        return try {
            context.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (e: Exception) {
                Timber.w(e, "Failed to open store listing for %s", packageName)
                false
            }
        }
    }
}
