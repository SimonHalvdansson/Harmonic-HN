package com.simon.harmonichackernews.widget

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A persisted observation, not a claim about current connectivity or the cause of a failure. */
internal fun widgetRefreshDiagnostics(context: Context, attempt: Int, elapsedMillis: Long, outcome: String): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    val power = context.getSystemService(PowerManager::class.java)
    val transport = when {
        network == null -> "none"
        network.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        network.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        else -> "other"
    }
    val backgroundRestricted = if (Build.VERSION.SDK_INT >= 28) {
        context.getSystemService(ActivityManager::class.java).isBackgroundRestricted.toString()
    } else "unknown"
    val dataSaver = when (connectivity.restrictBackgroundStatus) {
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "off"
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "exempt"
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "on"
        else -> "unknown"
    }
    val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return "$time · attempt ${attempt + 1}/${WIDGET_REFRESH_MAX_RETRIES + 1} · ${elapsedMillis / 1000}s · $outcome\n" +
        "Network=$transport · validated=${network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true}\n" +
        "Doze=${power.isDeviceIdleMode} · saver=${power.isPowerSaveMode} · bgRestricted=$backgroundRestricted · dataSaver=$dataSaver"
}
