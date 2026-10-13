package com.simon.harmonichackernews.app

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTimeFormatterTest {
    @Test
    fun macClockUsesNativePreferencesWithoutFallingBackToJava() {
        if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
        val afternoon = LocalDateTime.of(2026, 10, 13, 17, 8)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Call the native bridge directly so linkage errors cannot be hidden by the fallback.
        val native = MacTimeFormatting.format(afternoon)
        val formatter = DesktopTimeFormatter()
        assertTrue(native.text.isNotBlank())
        assertEquals(native.text, formatter.time(afternoon))
        assertEquals(native.uses24Hour, formatter.uses24HourClock())
        if (native.uses24Hour) assertTrue(native.text.contains("17"), native.text)
        println("Native macOS clock: ${native.text}; 24-hour=${native.uses24Hour}")
    }
}
