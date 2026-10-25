package com.alauncher.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalExceptionHandlerBufferTest {

    @Test
    fun massiveStackTrace_isTruncatedToPreventIpcBufferOverflow() {
        // Generate a stack trace exceeding 25,000 characters
        val massiveStackTrace = "at com.alauncher.app.DeepCallTrace.run(Trace.kt:1)\n".repeat(600)
        assertTrue(
            "Precondition: input stack trace must exceed threshold",
            massiveStackTrace.length > GlobalExceptionHandler.MAX_STACK_TRACE_LENGTH
        )

        val formatted = GlobalExceptionHandler.formatStackTrace(massiveStackTrace)

        val expectedMaxLength = GlobalExceptionHandler.MAX_STACK_TRACE_LENGTH +
                GlobalExceptionHandler.TRUNCATION_DISCLAIMER.length
        assertEquals(expectedMaxLength, formatted.length)
        assertTrue(
            "Formatted trace must end with truncation disclaimer",
            formatted.endsWith(GlobalExceptionHandler.TRUNCATION_DISCLAIMER)
        )
    }

    @Test
    fun smallStackTrace_isNotTruncated() {
        val smallStackTrace = "java.lang.IllegalArgumentException: Test error\n\tat com.alauncher.app.MainActivity.onCreate(MainActivity.kt:10)"
        val formatted = GlobalExceptionHandler.formatStackTrace(smallStackTrace)

        assertEquals("Small stack trace should be completely preserved", smallStackTrace, formatted)
        assertTrue(
            "Small stack trace should not contain truncation disclaimer",
            !formatted.contains(GlobalExceptionHandler.TRUNCATION_DISCLAIMER)
        )
    }

    @Test
    fun exactBoundary_notTruncated() {
        val exactTrace = "x".repeat(GlobalExceptionHandler.MAX_STACK_TRACE_LENGTH)
        val formatted = GlobalExceptionHandler.formatStackTrace(exactTrace)

        assertEquals("Trace at exactly MAX length must not be truncated", exactTrace, formatted)
        assertEquals(GlobalExceptionHandler.MAX_STACK_TRACE_LENGTH, formatted.length)
    }

    @Test
    fun oneBeyondBoundary_isTruncated() {
        val overTrace = "x".repeat(GlobalExceptionHandler.MAX_STACK_TRACE_LENGTH + 1)
        val formatted = GlobalExceptionHandler.formatStackTrace(overTrace)

        val expectedLen = GlobalExceptionHandler.MAX_STACK_TRACE_LENGTH +
                GlobalExceptionHandler.TRUNCATION_DISCLAIMER.length
        assertEquals(expectedLen, formatted.length)
        assertTrue(formatted.endsWith(GlobalExceptionHandler.TRUNCATION_DISCLAIMER))
    }
}
