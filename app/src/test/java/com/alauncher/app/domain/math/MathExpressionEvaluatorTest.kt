package com.alauncher.app.domain.math

import org.junit.Assert.*
import org.junit.Test

class MathExpressionEvaluatorTest {

    @Test
    fun testBasicArithmetic() {
        assertEquals("540", MathExpressionEvaluator.evaluate("45 * 12")?.resultText)
        assertEquals("175", MathExpressionEvaluator.evaluate("150 + 25")?.resultText)
        assertEquals("125", MathExpressionEvaluator.evaluate("150 - 25")?.resultText)
        assertEquals("150", MathExpressionEvaluator.evaluate("1800 / 12")?.resultText)
        assertEquals("1024", MathExpressionEvaluator.evaluate("2 ^ 10")?.resultText)
        assertEquals("1", MathExpressionEvaluator.evaluate("10 % 3")?.resultText)
        assertEquals("2", MathExpressionEvaluator.evaluate("14 % 4")?.resultText)
    }

    @Test
    fun testPrecedenceAndDecimals() {
        assertEquals("182.9", MathExpressionEvaluator.evaluate("(120 + 35) * 1.18")?.resultText)
        assertEquals("14", MathExpressionEvaluator.evaluate("2 + 3 * 4")?.resultText)
        assertEquals("20", MathExpressionEvaluator.evaluate("(2 + 3) * 4")?.resultText)
        assertEquals("6", MathExpressionEvaluator.evaluate("((10 + 5) * 2) / (3 + 2)")?.resultText)
        assertEquals("13", MathExpressionEvaluator.evaluate("2 + 3 * 4 - 5 / 5")?.resultText)
    }

    @Test
    fun testNegativeNumbersAndSigns() {
        assertEquals("5", MathExpressionEvaluator.evaluate("-5 + 10")?.resultText)
        assertEquals("-20", MathExpressionEvaluator.evaluate("10 * -2")?.resultText)
        assertEquals("15", MathExpressionEvaluator.evaluate("10 - -5")?.resultText)
        assertEquals("-8", MathExpressionEvaluator.evaluate("-5 - 3")?.resultText)
        assertEquals("-30", MathExpressionEvaluator.evaluate("-10 - 20")?.resultText)
        assertEquals("-6.5", MathExpressionEvaluator.evaluate("-2.5 - 4")?.resultText)
    }

    @Test
    fun testDivisionAndModuloByZeroSafe() {
        // Division and modulo by zero must return null without crashing
        assertNull(MathExpressionEvaluator.evaluate("10 / 0"))
        assertNull(MathExpressionEvaluator.evaluate("0 / 0"))
        assertNull(MathExpressionEvaluator.evaluate("15 / (5 - 5)"))
        assertNull(MathExpressionEvaluator.evaluate("10 % 0"))
    }

    @Test
    fun testPercentage() {
        assertEquals("127.5", MathExpressionEvaluator.evaluate("15% of 850")?.resultText)
        assertEquals("127.5", MathExpressionEvaluator.evaluate("850 * 15%")?.resultText)
        assertEquals("10", MathExpressionEvaluator.evaluate("20% of 50")?.resultText)
        assertEquals("100.2", MathExpressionEvaluator.evaluate("100 + 20%")?.resultText)
    }

    @Test
    fun testLengthConversions() {
        val kmToMiles = MathExpressionEvaluator.evaluate("10 km in miles")
        assertNotNull(kmToMiles)
        assertEquals("6.2137 miles", kmToMiles?.resultText)

        val milesToKm = MathExpressionEvaluator.evaluate("5 miles in km")
        assertNotNull(milesToKm)
        assertEquals("8.0467 km", milesToKm?.resultText)

        val mToFt = MathExpressionEvaluator.evaluate("1.8 m in ft")
        assertNotNull(mToFt)
        assertEquals("5.9055 ft", mToFt?.resultText)

        val ftToM = MathExpressionEvaluator.evaluate("6 ft in m")
        assertNotNull(ftToM)
        assertEquals("1.8288 m", ftToM?.resultText)

        val cmToInches = MathExpressionEvaluator.evaluate("15 cm in inches")
        assertNotNull(cmToInches)
        assertEquals("5.9055 inches", cmToInches?.resultText)

        val inchesToCm = MathExpressionEvaluator.evaluate("10 in in cm")
        assertNotNull(inchesToCm)
        assertEquals("25.4 cm", inchesToCm?.resultText)
    }

    @Test
    fun testWeightConversions() {
        val kgToLbs = MathExpressionEvaluator.evaluate("70 kg in lbs")
        assertNotNull(kgToLbs)
        assertEquals("154.3236 lbs", kgToLbs?.resultText)

        val lbsToKg = MathExpressionEvaluator.evaluate("154 lbs in kg")
        assertNotNull(lbsToKg)
        assertEquals("69.8532 kg", lbsToKg?.resultText)

        val gToOz = MathExpressionEvaluator.evaluate("250 g in oz")
        assertNotNull(gToOz)
        assertEquals("8.8185 oz", gToOz?.resultText)
    }

    @Test
    fun testTemperatureConversions() {
        val cToF = MathExpressionEvaluator.evaluate("37 c in f")
        assertNotNull(cToF)
        assertEquals("98.6 °F", cToF?.resultText)

        val fToC = MathExpressionEvaluator.evaluate("98.6 f in c")
        assertNotNull(fToC)
        assertEquals("37 °C", fToC?.resultText)

        val freezingC = MathExpressionEvaluator.evaluate("0 c in f")
        assertNotNull(freezingC)
        assertEquals("32 °F", freezingC?.resultText)

        val boilingC = MathExpressionEvaluator.evaluate("100 c in f")
        assertNotNull(boilingC)
        assertEquals("212 °F", boilingC?.resultText)

        val equalTemp = MathExpressionEvaluator.evaluate("-40 c in f")
        assertNotNull(equalTemp)
        assertEquals("-40 °F", equalTemp?.resultText)
    }

    @Test
    fun testDataStorageConversions() {
        val gbToMb = MathExpressionEvaluator.evaluate("16 gb in mb")
        assertNotNull(gbToMb)
        assertEquals("16384 MB", gbToMb?.resultText)

        val tbToGb = MathExpressionEvaluator.evaluate("2 tb in gb")
        assertNotNull(tbToGb)
        assertEquals("2048 GB", tbToGb?.resultText)

        val mbToGb = MathExpressionEvaluator.evaluate("2048 mb in gb")
        assertNotNull(mbToGb)
        assertEquals("2 GB", mbToGb?.resultText)
    }

    @Test
    fun testTimeConversions() {
        val daysToHours = MathExpressionEvaluator.evaluate("2 days in hours")
        assertNotNull(daysToHours)
        assertEquals("48 hours", daysToHours?.resultText)

        val hoursToMins = MathExpressionEvaluator.evaluate("1.5 hours in mins")
        assertNotNull(hoursToMins)
        assertEquals("90 mins", hoursToMins?.resultText)
    }

    @Test
    fun testAppQueriesStrictlyRejected() {
        // App searches and general queries must NEVER return math results
        assertNull(MathExpressionEvaluator.evaluate("whatsapp"))
        assertNull(MathExpressionEvaluator.evaluate("camera"))
        assertNull(MathExpressionEvaluator.evaluate("calculator"))
        assertNull(MathExpressionEvaluator.evaluate("1password"))
        assertNull(MathExpressionEvaluator.evaluate("cal 2024"))
        assertNull(MathExpressionEvaluator.evaluate("maps"))
        assertNull(MathExpressionEvaluator.evaluate("chrome"))
        assertNull(MathExpressionEvaluator.evaluate("fifa 23"))
        assertNull(MathExpressionEvaluator.evaluate("top 10 apps"))
        assertNull(MathExpressionEvaluator.evaluate("iphone 15 pro"))
        assertNull(MathExpressionEvaluator.evaluate("abc + def"))
        assertNull(MathExpressionEvaluator.evaluate("+++"))
        assertNull(MathExpressionEvaluator.evaluate(""))
        assertNull(MathExpressionEvaluator.evaluate("12"))
    }
}
