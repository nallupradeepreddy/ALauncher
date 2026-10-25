package com.alauncher.app.ui.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages the Google Play In-App Review API flow and Play Store listing fallback.
 *
 * Follows Google Play In-App Review best practices:
 * 1. Milestone-driven automatic prompt: Only requests the review flow after the user
 *    has completed onboarding, used the launcher for at least [MIN_INSTALL_AGE_MS] (3 days),
 *    launched at least [MIN_APP_LAUNCH_COUNT] (15) apps, is not in Focus Mode, and has not
 *    been prompted within [REVIEW_COOLDOWN_MS] (60 days).
 * 2. Manual Settings action: Directs explicit user taps on "Rate on Google Play" straight
 *    to the Google Play Store listing so Google's quota never causes a dead button click.
 * 3. 100% Offline & Zero-Network Permission: Communicates with the local Play Store app
 *    (`com.android.vending`) via Android Binder IPC and fails silently if unavailable.
 */
object InAppReviewHelper {

    internal const val PREFS_NAME = "alauncher_in_app_review"
    internal const val KEY_FIRST_SEEN_MS = "first_seen_timestamp_ms"
    internal const val KEY_APP_LAUNCH_COUNT = "app_launch_count"
    internal const val KEY_LAST_REVIEW_REQUEST_MS = "last_review_request_ms"

    /** Minimum time since first install/use before triggering an automatic review prompt (3 days). */
    const val MIN_INSTALL_AGE_MS: Long = 3L * 24 * 60 * 60 * 1000L

    /** Minimum number of apps launched through ALauncher before triggering an automatic review prompt. */
    const val MIN_APP_LAUNCH_COUNT: Int = 15

    /** Cooldown between automatic review flow attempts (60 days). */
    const val REVIEW_COOLDOWN_MS: Long = 60L * 24 * 60 * 60 * 1000L

    /**
     * Records a successful app launch from ALauncher and seeds the first-seen timestamp if needed.
     */
    fun recordAppLaunch(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val firstSeen = getOrSeedFirstSeenMs(context, prefs, nowMs)
        val nextCount = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0) + 1
        prefs.edit()
            .putLong(KEY_FIRST_SEEN_MS, firstSeen)
            .putInt(KEY_APP_LAUNCH_COUNT, nextCount)
            .apply()
    }

    /**
     * Pure eligibility check for whether ALauncher should trigger an automatic In-App Review prompt.
     */
    fun shouldRequestReview(
        hasCompletedWalkthrough: Boolean,
        isFocusModeActive: Boolean,
        firstSeenMs: Long,
        appLaunchCount: Int,
        lastReviewRequestMs: Long,
        nowMs: Long,
        isDefaultLauncher: Boolean = true
    ): Boolean {
        if (!isDefaultLauncher) return false
        if (!hasCompletedWalkthrough) return false
        if (isFocusModeActive) return false
        if (firstSeenMs <= 0L || (nowMs - firstSeenMs) < MIN_INSTALL_AGE_MS) return false
        if (appLaunchCount < MIN_APP_LAUNCH_COUNT) return false
        if (lastReviewRequestMs > 0L && (nowMs - lastReviewRequestMs) < REVIEW_COOLDOWN_MS) return false
        return true
    }

    /**
     * Checks local eligibility state stored in SharedPreferences.
     */
    fun isEligibleForReview(
        context: Context,
        hasCompletedWalkthrough: Boolean,
        isFocusModeActive: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        isDefaultLauncher: Boolean = LauncherUtils.isDefaultLauncher(context)
    ): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val firstSeenMs = getOrSeedFirstSeenMs(context, prefs, nowMs)
        val appLaunchCount = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0)
        val lastReviewRequestMs = prefs.getLong(KEY_LAST_REVIEW_REQUEST_MS, 0L)
        return shouldRequestReview(
            hasCompletedWalkthrough = hasCompletedWalkthrough,
            isFocusModeActive = isFocusModeActive,
            firstSeenMs = firstSeenMs,
            appLaunchCount = appLaunchCount,
            lastReviewRequestMs = lastReviewRequestMs,
            nowMs = nowMs,
            isDefaultLauncher = isDefaultLauncher
        )
    }

    /**
     * Requests and launches the Google Play In-App Review flow if all milestone criteria are met.
     *
     * @return `true` if the review flow was requested and completed without exception, `false` otherwise.
     */
    suspend fun maybeLaunchInAppReview(
        activity: Activity,
        hasCompletedWalkthrough: Boolean,
        isFocusModeActive: Boolean,
        reviewManager: ReviewManager = ReviewManagerFactory.create(activity),
        nowMs: Long = System.currentTimeMillis(),
        isDefaultLauncher: Boolean = LauncherUtils.isDefaultLauncher(activity)
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (!isEligibleForReview(activity, hasCompletedWalkthrough, isFocusModeActive, nowMs, isDefaultLauncher)) {
            return false
        }

        // Record the attempt timestamp immediately so we always honor the 60-day cooldown
        // even if Google Play's internal quota suppresses the dialog UI.
        val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_REVIEW_REQUEST_MS, nowMs).apply()

        return try {
            val reviewInfo = reviewManager.requestReviewFlow().awaitTask()
            if (!activity.isFinishing && !activity.isDestroyed) {
                reviewManager.launchReviewFlow(activity, reviewInfo).awaitTask()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.d(e, "Google Play In-App Review flow unavailable or skipped")
            false
        }
    }

    /**
     * Opens the Google Play Store listing page for ALauncher so the user can leave a rating/review
     * directly without being subject to In-App Review API quota suppression.
     */
    fun openPlayStoreListing(context: Context): Boolean {
        val packageName = context.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            setPackage("com.android.vending")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        }

        return try {
            context.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (e: Exception) {
                Timber.w(e, "Failed to open Play Store listing for %s", packageName)
                false
            }
        }
    }

    private fun getOrSeedFirstSeenMs(
        context: Context,
        prefs: android.content.SharedPreferences,
        nowMs: Long
    ): Long {
        val existing = prefs.getLong(KEY_FIRST_SEEN_MS, 0L)
        if (existing > 0L) return existing

        val installTime = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.firstInstallTime.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } ?: nowMs

        prefs.edit().putLong(KEY_FIRST_SEEN_MS, installTime).apply()
        return installTime
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        // Note: Play Core / GMS Task<T> does not expose an API to unregister an OnCompleteListener
        // on cancellation, so we guard continuation resumption with cont.isActive.
        val completeBlock: (Task<T>) -> Unit = { task: Task<T> ->
            if (cont.isActive) {
                if (task.isSuccessful) {
                    cont.resume(task.result)
                } else {
                    cont.resumeWithException(
                        task.exception ?: IllegalStateException("Play Review Task failed")
                    )
                }
            }
        }
        if (isComplete) {
            completeBlock(this)
        } else {
            addOnCompleteListener({ it.run() }, completeBlock)
        }
    }
}

