package com.simon.harmonichackernews.summary.local

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context

/** Android 14+ keeps background trim callbacks, but no longer sends foreground low-memory ones. */
@Suppress("DEPRECATION")
internal object LocalAiMemoryPolicy {
    fun background(level: Int): Boolean = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
    fun pressure(level: Int): Boolean = level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW until
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
}

internal fun localAiMemoryAvailable(context: Context): Boolean {
    val manager = context.getSystemService(ActivityManager::class.java) ?: return true
    return !ActivityManager.MemoryInfo().also(manager::getMemoryInfo).lowMemory
}
