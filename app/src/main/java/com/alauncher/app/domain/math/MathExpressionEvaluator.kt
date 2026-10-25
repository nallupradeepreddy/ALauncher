package com.alauncher.app.domain.math

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

data class MathResult(
    val expression: String,
    val resultText: String
)

object MathExpressionEvaluator {

    private val UNIT_CONVERSION_REGEX = Regex(
        """^([+-]?[0-9]+(?:\.[0-9]+)?)\s*([a-zA-Z°]+)\s+(?:in|to)\s+([a-zA-Z°]+)$""",
        RegexOption.IGNORE_CASE
    )

    private val PERCENT_OF_REGEX = Regex(
        """^([0-9]+(?:\.[0-9]+)?)\s*%\s+of\s+([0-9]+(?:\.[0-9]+)?)$""",
        RegexOption.IGNORE_CASE
    )

    // Length conversion relative to meter
    private val LENGTH_UNITS = mapOf(
        "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
        "cm" to 0.01, "centimeter" to 0.01, "centimeters" to 0.01,
        "mm" to 0.001, "millimeter" to 0.001, "millimeters" to 0.001,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
        "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144,
        "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
        "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254
    )

    // Weight conversion relative to gram
    private val WEIGHT_UNITS = mapOf(
        "kg" to 1000.0, "kgs" to 1000.0, "kilogram" to 1000.0, "kilograms" to 1000.0,
        "g" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "mg" to 0.001, "milligram" to 0.001, "milligrams" to 0.001,
        "lb" to 453.59237, "lbs" to 453.59237, "pound" to 453.59237, "pounds" to 453.59237,
        "oz" to 28.349523, "ounce" to 28.349523, "ounces" to 28.349523
    )

    // Data conversion relative to Megabyte (MB)
    private val DATA_UNITS = mapOf(
        "tb" to 1024.0 * 1024.0, "terabyte" to 1024.0 * 1024.0, "terabytes" to 1024.0 * 1024.0,
        "gb" to 1024.0, "gigabyte" to 1024.0, "gigabytes" to 1024.0,
        "mb" to 1.0, "megabyte" to 1.0, "megabytes" to 1.0,
        "kb" to 1.0 / 1024.0, "kilobyte" to 1.0 / 1024.0, "kilobytes" to 1.0 / 1024.0,
        "b" to 1.0 / (1024.0 * 1024.0), "byte" to 1.0 / (1024.0 * 1024.0), "bytes" to 1.0 / (1024.0 * 1024.0)
    )

    // Time conversion relative to second
    private val TIME_UNITS = mapOf(
        "day" to 86400.0, "days" to 86400.0, "d" to 86400.0,
        "hr" to 3600.0, "hrs" to 3600.0, "hour" to 3600.0, "hours" to 3600.0, "h" to 3600.0,
        "min" to 60.0, "mins" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "sec" to 1.0, "secs" to 1.0, "second" to 1.0, "seconds" to 1.0, "s" to 1.0
    )

    fun evaluate(rawQuery: String): MathResult? {
        val q = rawQuery.trim()
        if (q.length < 3 || q.length > 80) return null

        // 1. Check unit conversions
        val conversionResult = evaluateUnitConversion(q)
        if (conversionResult != null) return conversionResult

        // 2. Check "X% of Y" pattern
        val percentOfMatch = PERCENT_OF_REGEX.matchEntire(q)
        if (percentOfMatch != null) {
            val p = percentOfMatch.groupValues[1].toDoubleOrNull() ?: return null
            val v = percentOfMatch.groupValues[2].toDoubleOrNull() ?: return null
            val res = (p / 100.0) * v
            return MathResult(q, formatNumber(res))
        }

        // 3. Strict guard for arithmetic:
        // Must contain at least one digit AND at least one operator (+, -, *, /, x, X, ×, ÷, ^, %)
        val hasDigit = q.any { it.isDigit() }
        val hasOperator = q.any { it in "+*xX×/÷^%" } || (q.contains('-') && hasBinaryMinus(q))
        if (!hasDigit || !hasOperator) return null

        // Must not contain general English letters (to prevent matching app queries like "cal 2024" or "app+web")
        // Allow only valid math characters: digits, ., +, -, *, /, x, X, ×, ÷, ^, %, (, ), space
        val isValidMathChars = q.all { it.isDigit() || it in ".+-*xX×/÷^%() " }
        if (!isValidMathChars) return null

        return try {
            val normalized = q
                .replace("×", "*")
                .replace("÷", "/")
                .replace("x", "*")
                .replace("X", "*")
            val res = Parser(normalized).parse()
            if (res.isFinite()) {
                MathResult(q, formatNumber(res))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun hasBinaryMinus(q: String): Boolean {
        for (i in 1 until q.length) {
            if (q[i] == '-') {
                val before = q.substring(0, i).trimEnd()
                if (before.isNotEmpty() && (before.last().isDigit() || before.last() == ')')) {
                    return true
                }
            }
        }
        return false
    }

    private fun evaluateUnitConversion(q: String): MathResult? {
        val match = UNIT_CONVERSION_REGEX.matchEntire(q) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = match.groupValues[2].lowercase(Locale.ROOT)
        val toUnit = match.groupValues[3].lowercase(Locale.ROOT)

        // Temperature
        if (isTemp(fromUnit) && isTemp(toUnit)) {
            val converted = convertTemperature(value, fromUnit, toUnit) ?: return null
            val symbol = if (toUnit in listOf("f", "fahrenheit", "°f")) "°F" else "°C"
            return MathResult(q, "${formatNumber(converted)} $symbol")
        }

        // Length
        if (LENGTH_UNITS.containsKey(fromUnit) && LENGTH_UNITS.containsKey(toUnit)) {
            val inMeters = value * LENGTH_UNITS[fromUnit]!!
            val converted = inMeters / LENGTH_UNITS[toUnit]!!
            return MathResult(q, "${formatNumber(converted)} $toUnit")
        }

        // Weight
        if (WEIGHT_UNITS.containsKey(fromUnit) && WEIGHT_UNITS.containsKey(toUnit)) {
            val inGrams = value * WEIGHT_UNITS[fromUnit]!!
            val converted = inGrams / WEIGHT_UNITS[toUnit]!!
            return MathResult(q, "${formatNumber(converted)} $toUnit")
        }

        // Data Storage
        if (DATA_UNITS.containsKey(fromUnit) && DATA_UNITS.containsKey(toUnit)) {
            val inMb = value * DATA_UNITS[fromUnit]!!
            val converted = inMb / DATA_UNITS[toUnit]!!
            return MathResult(q, "${formatNumber(converted)} ${toUnit.uppercase(Locale.ROOT)}")
        }

        // Time
        if (TIME_UNITS.containsKey(fromUnit) && TIME_UNITS.containsKey(toUnit)) {
            val inSeconds = value * TIME_UNITS[fromUnit]!!
            val converted = inSeconds / TIME_UNITS[toUnit]!!
            return MathResult(q, "${formatNumber(converted)} $toUnit")
        }

        return null
    }

    private fun isTemp(u: String) = u in listOf("c", "celsius", "°c", "f", "fahrenheit", "°f")

    private fun convertTemperature(v: Double, from: String, to: String): Double? {
        val isFromC = from in listOf("c", "celsius", "°c")
        val isToF = to in listOf("f", "fahrenheit", "°f")
        return when {
            isFromC && isToF -> (v * 9.0 / 5.0) + 32.0
            !isFromC && !isToF -> (v - 32.0) * 5.0 / 9.0
            else -> v
        }
    }

    private fun formatNumber(v: Double): String {
        val rounded = (v * 10000.0).roundToLong() / 10000.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.4f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Recursive descent parser with operator precedence:
     * Grammar:
     * expression = term { ("+" | "-") term }
     * term       = factor { ("*" | "/" | "%") factor }
     * factor     = base { "^" factor }
     * base       = [ "+" | "-" ] ( number | "(" expression ")" )
     */
    private class Parser(private val src: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < src.length) src[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            while (ch == ' '.code) nextChar()
            if (pos < src.length) throw IllegalArgumentException("Unexpected: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                        x %= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            val base = parseBase()
            return if (eat('^'.code)) {
                base.pow(parseFactor())
            } else {
                base
            }
        }

        private fun parseBase(): Double {
            if (eat('+'.code)) return +parseBase()
            if (eat('-'.code)) return -parseBase()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = src.substring(startPos, pos).toDouble()
                val savePos = pos
                val saveCh = ch
                if (eat('%'.code)) {
                    var peekPos = pos
                    while (peekPos < src.length && src[peekPos] == ' ') peekPos++
                    val next = if (peekPos < src.length) src[peekPos] else null
                    if (next == null || next in "+-*/) ") {
                        x /= 100.0
                    } else {
                        pos = savePos
                        ch = saveCh
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected: " + ch.toChar())
            }
            return x
        }
    }
}
