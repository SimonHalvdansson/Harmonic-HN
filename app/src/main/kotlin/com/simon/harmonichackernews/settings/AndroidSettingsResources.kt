package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Device query that cannot be implemented in common settings code. */
object AndroidSettingsResources {
    fun batteryPercent(context: Context): Int? {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return Math.round(level * 100f / scale)
    }
}
