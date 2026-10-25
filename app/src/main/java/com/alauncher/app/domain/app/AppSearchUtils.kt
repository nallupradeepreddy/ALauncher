package com.alauncher.app.domain.app

import com.alauncher.app.model.LauncherApp

private val WHITESPACE_REGEX = Regex("""\s+""")

/**
 * Resolves whether the current search query should trigger an automatic app launch.
 * Returns the matching LauncherApp if:
 * 1. An app exactly matches the query (ignoring case & optional internal spaces) with query length >= 2.
 * 2. Exactly one app remains in the filtered results and query length >= 2.
 */
fun resolveAutoLaunchCandidate(apps: List<LauncherApp>, query: String): LauncherApp? {
    val q = query.trim()
    if (q.length < 2 || apps.isEmpty()) return null

    val qNormalized = AppTrieSearchIndex.normalizeForSearch(q)
    val qSpaceless = q.replace(WHITESPACE_REGEX, "")
    val qNormSpaceless = qNormalized.replace(WHITESPACE_REGEX, "")

    // Find exact match candidate (e.g. typing "amazon" when "Amazon" exists,
    // or typing "musica" when "Música" exists). The final auto-launch decision
    // also guards against prefix ambiguity below.
    val exactMatchApp = apps.firstOrNull { app ->
        // Fast-path: standard case-insensitive exact match short-circuits before transformations
        if (q.equals(app.label, ignoreCase = true)) return@firstOrNull true

        val labelNormalized = AppTrieSearchIndex.normalizeForSearch(app.label)
        if (qNormalized.isNotEmpty() && qNormalized.equals(labelNormalized, ignoreCase = true)) return@firstOrNull true

        val labelSpaceless = app.label.replace(WHITESPACE_REGEX, "")
        if (qSpaceless.length >= 2 && labelSpaceless.length >= 2 && qSpaceless.equals(labelSpaceless, ignoreCase = true)) return@firstOrNull true

        val labelNormSpaceless = labelNormalized.replace(WHITESPACE_REGEX, "")
        qNormSpaceless.length >= 2 && labelNormSpaceless.length >= 2 && qNormSpaceless.equals(labelNormSpaceless, ignoreCase = true)
    }

    if (exactMatchApp != null) {
        // Guard against prefix ambiguity:
        // Do NOT auto-launch an exact match if other candidates in the search results
        // start with the query / exact match label (e.g. "Phone" when "Phone Manager" or "PhonePe" exist).
        // This ensures the user can continue typing without being hijacked.
        val hasPrefixAmbiguity = apps.any { other ->
            if (other === exactMatchApp) return@any false

            // Fast path check before performing string transformations
            if (other.label.startsWith(q, ignoreCase = true)) return@any true

            val otherNormalized = AppTrieSearchIndex.normalizeForSearch(other.label)
            if (qNormalized.isNotEmpty() && otherNormalized.startsWith(qNormalized, ignoreCase = true)) return@any true

            val otherSpaceless = other.label.replace(WHITESPACE_REGEX, "")
            if (qSpaceless.length >= 2 && otherSpaceless.startsWith(qSpaceless, ignoreCase = true)) return@any true

            val otherNormSpaceless = otherNormalized.replace(WHITESPACE_REGEX, "")
            qNormSpaceless.length >= 2 && otherNormSpaceless.startsWith(qNormSpaceless, ignoreCase = true)
        }

        if (!hasPrefixAmbiguity) {
            return exactMatchApp
        }
    }

    // Fallback: single remaining candidate in search results
    return if (apps.size == 1) apps.first() else null
}
