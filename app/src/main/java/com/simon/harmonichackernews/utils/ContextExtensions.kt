package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri

fun Context.defaultBrowserPackageName(): String? {
    // This relies on the intent queries defined in the app manifest for Android 11+
    val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
    val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
            browserIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolveInfo
        ?.activityInfo?.packageName
        ?.takeUnless { it in InvalidDefaultBrowsers }
}

/**
 * A list of package names that may be incorrectly resolved as usable browsers by
 * the system.
 *
 * If these are resolved for [android.content.Intent.ACTION_VIEW], it prevents the
 * system from opening a proper browser or any usable app.
 *
 * Some of them may only be present on certain manufacturer's devices.
 */
private val InvalidDefaultBrowsers = listOf(
    "android",
    // Honor
    "com.hihonor.android.internal.app",
    // Huawei
    "com.huawei.android.internal.app",
    // Lenovo
    "com.zui.resolver",
    // Infinix
    "com.transsion.resolver",
    // Xiaomi Redmi
    "com.android.intentresolver",
)