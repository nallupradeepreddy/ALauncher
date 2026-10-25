package com.alauncher.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.alauncher.app.BuildConfig
import com.alauncher.app.R

enum class FeedbackCategory(val labelRes: Int, val tag: String) {
    FEATURE_REQUEST(R.string.feedback_category_feature, "Feature Request"),
    BUG_REPORT(R.string.feedback_category_bug, "Bug Report"),
    GENERAL_FEEDBACK(R.string.feedback_category_general, "General Feedback")
}

object FeedbackUtils {

    const val DEVELOPER_EMAIL = "pradeep.alauncher@gmail.com"
    const val MIN_MESSAGE_LENGTH = 5
    const val MAX_MESSAGE_LENGTH = 4000

    fun isMessageValid(message: String): Boolean {
        val trimmed = message.trim()
        return trimmed.length in MIN_MESSAGE_LENGTH..MAX_MESSAGE_LENGTH
    }

    fun buildSubject(category: FeedbackCategory, message: String): String {
        val firstLine = message.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: category.tag

        val snippet = if (firstLine.length > 50) {
            firstLine.take(50).trimEnd() + "…"
        } else {
            firstLine
        }

        return "[ALauncher ${category.tag}] $snippet"
    }

    fun buildBody(
        category: FeedbackCategory,
        message: String,
        includeDiagnostics: Boolean,
        appVersion: String = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
        androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
        isDefaultLauncher: Boolean = false
    ): String {
        val builder = StringBuilder()
        builder.appendLine("Category: ${category.tag}")
        builder.appendLine()
        builder.appendLine("--- Message ---")
        builder.appendLine(message.trim())
        builder.appendLine()

        if (includeDiagnostics) {
            builder.appendLine("--- Device Information ---")
            builder.appendLine("App Version: $appVersion")
            builder.appendLine("Android OS: $androidVersion")
            builder.appendLine("Device: $deviceModel")
            builder.appendLine("Default Launcher: ${if (isDefaultLauncher) "Yes" else "No"}")
            builder.appendLine()
        }

        return builder.toString()
    }

    fun buildMailtoUri(subject: String, body: String): Uri {
        val encodedSubject = Uri.encode(subject)
        val encodedBody = Uri.encode(body)
        return Uri.parse("mailto:$DEVELOPER_EMAIL?subject=$encodedSubject&body=$encodedBody")
    }

    fun createEmailIntent(subject: String, body: String): Intent {
        val mailtoUri = buildMailtoUri(subject, body)
        return Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    fun dispatchFeedbackEmail(
        context: Context,
        subject: String,
        body: String
    ): Boolean {
        return try {
            val intent = createEmailIntent(subject, body)
            val chooser = Intent.createChooser(intent, context.getString(R.string.feedback_dialog_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            android.util.Log.e("FeedbackUtils", "Failed to launch email client", e)
            copyToClipboard(context, subject, body)
            false
        }
    }

    private fun copyToClipboard(context: Context, subject: String, body: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val textToCopy = "To: $DEVELOPER_EMAIL\nSubject: $subject\n\n$body"
            clipboard?.setPrimaryClip(ClipData.newPlainText("ALauncher Feedback", textToCopy))
            Toast.makeText(context, R.string.feedback_copied_to_clipboard, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
    }
}
