package com.alauncher.app.domain.app

import com.alauncher.app.model.LauncherApp
import java.util.Locale

class AppTrieSearchIndex {

    companion object {
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val NON_ALPHANUM_REGEX = Regex("""[^\p{L}\p{N}]+""")
        private val DIACRITICS_REGEX = Regex("""\p{M}+""")

        /**
         * Normalizes a string for search by decomposing accented characters into base letters
         * and stripping combining diacritical marks (e.g. "Téléphone" -> "telephone", "Über" -> "uber", "Música" -> "musica").
         * Applies consistent lowercase normalization using Locale.ROOT to avoid locale-dependent mismatches (e.g. Turkish dotless i).
         */
        fun normalizeForSearch(input: String): String {
            if (input.isEmpty()) return ""
            val nfd = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            return DIACRITICS_REGEX.replace(nfd, "").lowercase(Locale.ROOT)
        }
    }

    private class IndexedAppData(
        val app: LauncherApp,
        val rawLabelLower: String,
        val normLabelLower: String,
        val rawLabelSpaceless: String,
        val normLabelSpaceless: String,
        val normWords: List<String>,
        val rawWords: List<String>,
        val normCamelWords: List<String>,
        val rawCamelWords: List<String>,
        val allWords: List<String>
    )

    private class SearchTokens(
        val rawLabelLower: String,
        val normLabelLower: String,
        val rawLabelSpaceless: String,
        val normLabelSpaceless: String,
        val normWords: List<String>,
        val rawWords: List<String>,
        val normCamelWords: List<String>,
        val rawCamelWords: List<String>,
        val allWords: List<String>
    )

    private val tokenCache = java.util.concurrent.ConcurrentHashMap<String, SearchTokens>()

    @Volatile
    private var indexedApps: List<IndexedAppData> = emptyList()

    @Volatile
    private var appMap: Map<String, LauncherApp> = emptyMap()

    private fun getOrCreateTokens(packageName: String, label: String): SearchTokens? {
        val rawLabel = label.lowercase(Locale.ROOT).trim()
        val normLabel = normalizeForSearch(label).trim()
        if (rawLabel.isEmpty() && normLabel.isEmpty()) return null

        val cacheKey = "$packageName:$label"
        return tokenCache.computeIfAbsent(cacheKey) {
            val rawLabelSpaceless = NON_ALPHANUM_REGEX.replace(rawLabel.replace(WHITESPACE_REGEX, ""), "")
            val normLabelSpaceless = NON_ALPHANUM_REGEX.replace(normLabel.replace(WHITESPACE_REGEX, ""), "")

            val normWords = NON_ALPHANUM_REGEX.split(normLabel).filter { it.isNotEmpty() }
            val rawWords = NON_ALPHANUM_REGEX.split(rawLabel).filter { it.isNotEmpty() }
            val normCamelWords = splitCamelCase(label).map { normalizeForSearch(it) }.filter { it.isNotEmpty() }
            val rawCamelWords = splitCamelCase(label).map { it.lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
            val allWords = (normWords + rawWords + normCamelWords + rawCamelWords).distinct()

            SearchTokens(
                rawLabelLower = rawLabel,
                normLabelLower = normLabel,
                rawLabelSpaceless = rawLabelSpaceless,
                normLabelSpaceless = normLabelSpaceless,
                normWords = normWords,
                rawWords = rawWords,
                normCamelWords = normCamelWords,
                rawCamelWords = rawCamelWords,
                allWords = allWords
            )
        }
    }

    private fun buildIndexedData(app: LauncherApp): IndexedAppData? {
        val tokens = getOrCreateTokens(app.packageName, app.label) ?: return null
        return IndexedAppData(
            app = app,
            rawLabelLower = tokens.rawLabelLower,
            normLabelLower = tokens.normLabelLower,
            rawLabelSpaceless = tokens.rawLabelSpaceless,
            normLabelSpaceless = tokens.normLabelSpaceless,
            normWords = tokens.normWords,
            rawWords = tokens.rawWords,
            normCamelWords = tokens.normCamelWords,
            rawCamelWords = tokens.rawCamelWords,
            allWords = tokens.allWords
        )
    }

    /**
     * Rebuilds the search index with the current list of apps.
     * Precomputes normalized labels, spaceless strings, and word tokens on a background thread
     * and swaps references atomically so live queries never read a cleared index or do expensive
     * string decomposition during typing.
     */
    fun rebuildIndex(apps: List<LauncherApp>) {
        val newAppMap = HashMap<String, LauncherApp>(apps.size)
        val newIndexed = ArrayList<IndexedAppData>(apps.size)

        for (app in apps) {
            newAppMap[app.packageName] = app
            val indexed = buildIndexedData(app) ?: continue
            newIndexed.add(indexed)
        }

        // Clean up stale tokens for removed packages
        if (tokenCache.size > apps.size + 20) {
            val activeKeys = apps.map { "${it.packageName}:${it.label}" }.toSet()
            tokenCache.keys.retainAll(activeKeys)
        }

        synchronized(this) {
            indexedApps = newIndexed
            appMap = newAppMap
        }
    }

    /**
     * Searches matching applications with weighted relevance scoring.
     * Orders apps by relevance score descending so best matches are always at index 0.
     * If appMap has not finished building from background thread yet, uses fallbackApps directly.
     */
    fun search(query: String, fallbackApps: List<LauncherApp> = emptyList()): List<LauncherApp> {
        val rawCleanQ = query.lowercase(Locale.ROOT).trim()
        val normCleanQ = normalizeForSearch(query).trim()
        val (currentIndexed, currentMap) = synchronized(this) { indexedApps to appMap }

        if (rawCleanQ.isEmpty() && normCleanQ.isEmpty()) {
            return if (currentMap.isNotEmpty()) currentMap.values.toList() else fallbackApps
        }

        val appsToSearch = if (currentIndexed.isNotEmpty()) {
            currentIndexed
        } else {
            fallbackApps.mapNotNull { buildIndexedData(it) }
        }

        val scoredApps = ArrayList<Pair<LauncherApp, Double>>(appsToSearch.size)
        val rawQSpaceless = NON_ALPHANUM_REGEX.replace(rawCleanQ.replace(WHITESPACE_REGEX, ""), "")
        val normQSpaceless = NON_ALPHANUM_REGEX.replace(normCleanQ.replace(WHITESPACE_REGEX, ""), "")
        val rawQWords = rawCleanQ.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val normQWords = normCleanQ.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val qWords = (rawQWords + normQWords).distinct()

        for (item in appsToSearch) {
            val app = item.app
            val rawLabelLower = item.rawLabelLower
            val normLabelLower = item.normLabelLower
            val rawLabelSpaceless = item.rawLabelSpaceless
            val normLabelSpaceless = item.normLabelSpaceless
            val allWords = item.allWords
            val normWords = item.normWords
            val rawWords = item.rawWords
            val normCamelWords = item.normCamelWords
            val rawCamelWords = item.rawCamelWords

            var score = 0.0

            val isExact = rawLabelLower == rawCleanQ || (normCleanQ.isNotEmpty() && normLabelLower == normCleanQ)
            val isSpacelessExact = (rawQSpaceless.length >= 2 && rawLabelSpaceless == rawQSpaceless) ||
                    (normQSpaceless.length >= 2 && normLabelSpaceless == normQSpaceless)

            if (isExact) {
                score = 100.0
            } else if (isSpacelessExact) {
                score = 98.0
            } else if (rawLabelLower.startsWith(rawCleanQ) || (normCleanQ.isNotEmpty() && normLabelLower.startsWith(normCleanQ))) {
                score = 90.0
            } else if (isWordSuffixPrefixMatch(allWords, rawCleanQ) || (normCleanQ.isNotEmpty() && isWordSuffixPrefixMatch(allWords, normCleanQ))) {
                score = 85.0
            } else if (rawQWords.size > 1 && (isMultiWordPrefixMatch(allWords, rawQWords) ||
                        (normCleanQ.isNotEmpty() && isMultiWordPrefixMatch(allWords, normQWords)))) {
                score = 80.0
            } else if ((rawQSpaceless.length >= 3 && rawLabelSpaceless.contains(rawQSpaceless)) ||
                    (normQSpaceless.length >= 3 && normLabelSpaceless.contains(normQSpaceless))) {
                score = 70.0
            } else if (matchesInitials(normWords, normQSpaceless) ||
                    matchesInitials(rawWords, rawQSpaceless) ||
                    matchesInitials(normCamelWords, normQSpaceless) ||
                    matchesInitials(rawCamelWords, rawQSpaceless)) {
                score = 60.0
            } else if (rawLabelLower.contains(rawCleanQ) || (normCleanQ.isNotEmpty() && normLabelLower.contains(normCleanQ))) {
                score = 50.0
            }

            if (score > 0) {
                score += computeCoverageBonus(allWords, qWords)
                if (app.isFavorite) {
                    score += 2.0
                }
                score += (app.launchCount * 0.1).coerceAtMost(2.0)
                scoredApps.add(app to score)
            }
        }

        return scoredApps.sortedWith(
            compareByDescending<Pair<LauncherApp, Double>> { it.second }
                .thenBy { it.first.normalizedLabel }
        ).map { it.first }
    }

    private fun computeCoverageBonus(words: List<String>, qWords: List<String>): Double {
        var bonus = 0.0
        val usedWordIndices = mutableSetOf<Int>()
        for (qWord in qWords) {
            for (i in words.indices) {
                if (i !in usedWordIndices && words[i].startsWith(qWord)) {
                    usedWordIndices.add(i)
                    bonus += qWord.length.toDouble() / words[i].length.toDouble()
                    break
                }
            }
        }
        return bonus
    }

    private fun isWordSuffixPrefixMatch(words: List<String>, cleanQ: String): Boolean {
        for (i in words.indices) {
            val suffix = words.subList(i, words.size).joinToString(" ")
            if (suffix.startsWith(cleanQ)) return true
        }
        return false
    }

    private fun isMultiWordPrefixMatch(labelWords: List<String>, qWords: List<String>): Boolean {
        var labelIdx = 0
        for (qWord in qWords) {
            var found = false
            while (labelIdx < labelWords.size) {
                val lw = labelWords[labelIdx]
                labelIdx++
                if (lw.startsWith(qWord)) {
                    found = true
                    break
                }
            }
            if (!found) return false
        }
        return true
    }

    private fun matchesInitials(words: List<String>, cleanQSpaceless: String): Boolean {
        if (words.size < 2 || cleanQSpaceless.isEmpty()) return false
        val initials = words.mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
        return initials.startsWith(cleanQSpaceless)
    }

    private fun splitCamelCase(s: String): List<String> {
        val tokens = s.split(NON_ALPHANUM_REGEX).filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        for (token in tokens) {
            val current = StringBuilder()
            for (i in token.indices) {
                val c = token[i]
                if (i > 0 && c.isUpperCase() && ((i + 1 < token.length && token[i + 1].isLowerCase()) || token[i - 1].isLowerCase())) {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                current.append(c)
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }
        }
        return result
    }
}
