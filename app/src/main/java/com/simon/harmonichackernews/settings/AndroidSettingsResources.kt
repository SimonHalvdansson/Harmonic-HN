package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.BatteryManager
import com.simon.harmonichackernews.R
import kotlin.math.min

/** Resource and device queries that cannot be implemented in common settings code. */
object AndroidSettingsResources {
    fun fontLabel(context: Context, font: String): String {
        val sanitized = TextPreferences.sanitizeFont(font)
        val entries = context.resources.getStringArray(R.array.font_entries)
        val values = context.resources.getStringArray(R.array.font_values)
        for (index in 0 until min(entries.size, values.size)) {
            if (sanitized == values[index]) return entries[index]
        }
        return entries.firstOrNull() ?: sanitized
    }

    fun faviconProviderIcon(provider: String): Int = when (
        FaviconPreferences.sanitizeProvider(provider)
    ) {
        FaviconPreferences.DUCK_DUCK_GO -> R.drawable.ic_favicon_provider_duckduckgo
        FaviconPreferences.TWENTY -> R.drawable.ic_favicon_provider_twenty
        else -> R.drawable.ic_favicon_provider_google
    }

    fun hasEnoughBatteryForWebViewPreload(context: Context, minimumBattery: Int): Boolean {
        val minimum = WebViewPreferences.clampBatteryPercent(minimumBattery)
        if (minimum <= WebViewPreferences.DEFAULT_MINIMUM_BATTERY) return true
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return true
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return true
        return Math.round(level * 100f / scale) >= minimum
    }

    fun indexOfLabel(resources: Resources, label: String, fallbackFromEnd: Int): Int {
        val options = resources.getStringArray(R.array.sorting_options)
        val match = options.indexOfLast { it == label }
        return match.takeIf { it >= 0 } ?: (options.size - fallbackFromEnd).coerceAtLeast(0)
    }
}
