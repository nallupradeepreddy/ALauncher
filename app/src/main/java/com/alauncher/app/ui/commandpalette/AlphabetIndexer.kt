package com.alauncher.app.ui.commandpalette

import com.alauncher.app.model.LauncherApp

object AlphabetIndexer {
    val ALPHABET: List<Char> = ('A'..'Z').toList()

    fun computeAvailableLetters(apps: List<LauncherApp>): Set<Char> {
        return apps.mapNotNull { app ->
            val first = app.normalizedLabel.firstOrNull()?.uppercaseChar()
            if (first != null && first in 'A'..'Z') first else null
        }.toSet()
    }

    fun computeLetterIndexMap(apps: List<LauncherApp>): Map<Char, Int> {
        val map = mutableMapOf<Char, Int>()
        apps.forEachIndexed { index, app ->
            val firstChar = app.normalizedLabel.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar else '#'
            if (!map.containsKey(key)) {
                map[key] = index
            }
        }
        return map
    }

    fun getTargetIndex(letterIndexMap: Map<Char, Int>, letter: Char): Int? {
        if (letterIndexMap.containsKey(letter)) return letterIndexMap[letter]
        if (letter in 'A'..'Z') {
            for (c in letter..'Z') {
                letterIndexMap[c]?.let { return it }
            }
            for (c in letter downTo 'A') {
                letterIndexMap[c]?.let { return it }
            }
        }
        return letterIndexMap['#']
    }
}
