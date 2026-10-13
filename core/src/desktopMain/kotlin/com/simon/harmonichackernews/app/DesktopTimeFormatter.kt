package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.platform.LocalCalendarDate
import com.simon.harmonichackernews.platform.PlatformTimeFormatter
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal class DesktopTimeFormatter : PlatformTimeFormatter {
    override fun time(epochMillis: Long): String = nativeTime(epochMillis)?.text
        ?: Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )

    override fun localDate(epochMillis: Long): LocalCalendarDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().let {
            LocalCalendarDate(it.year, it.monthValue, it.dayOfMonth)
        }

    override fun uses24HourClock(): Boolean = nativeTime(0)?.uses24Hour
        ?: ((DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)
            ?.toPattern()?.any { it == 'H' || it == 'k' } ?: true)

    private fun nativeTime(epochMillis: Long): NativeTime? {
        if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return null
        return runCatching { MacTimeFormatting.format(epochMillis) }.getOrNull()
    }
}

internal data class NativeTime(val text: String, val uses24Hour: Boolean)

/** A fresh native locale includes the user's clock override, not just the Java locale. */
internal object MacTimeFormatting {
    private val api by lazy { Native.load("CoreFoundation", DateFormattingApi::class.java) }
    fun format(epochMillis: Long): NativeTime {
        val locale = checkNotNull(api.CFLocaleCopyCurrent())
        try {
            val formatter = checkNotNull(api.CFDateFormatterCreate(null, locale, 0L, 1L))
            try {
                val text = checkNotNull(api.CFDateFormatterCreateStringWithAbsoluteTime(
                    null, formatter, epochMillis / 1_000.0 - 978_307_200.0,
                ))
                try {
                    val pattern = CFStringRef(api.CFDateFormatterGetFormat(formatter)).stringValue()
                    return NativeTime(CFStringRef(text).stringValue(), pattern.any { it == 'H' || it == 'k' })
                } finally { api.CFRelease(text) }
            } finally { api.CFRelease(formatter) }
        } finally { api.CFRelease(locale) }
    }
}

private interface DateFormattingApi : Library {
    fun CFLocaleCopyCurrent(): Pointer?
    fun CFDateFormatterCreate(allocator: Pointer?, locale: Pointer, dateStyle: Long, timeStyle: Long): Pointer?
    fun CFDateFormatterCreateStringWithAbsoluteTime(allocator: Pointer?, formatter: Pointer, time: Double): Pointer?
    fun CFDateFormatterGetFormat(formatter: Pointer): Pointer
    fun CFRelease(value: Pointer)
}
