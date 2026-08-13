package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.presentation.AdBlocklistService
import kotlin.math.roundToInt

object HarmonicLog {
    fun debug(value: Any?) {
        Log.d("HARMONIC_TAG", value.toString())
    }
}

object AndroidDisplay {
    fun dpToPx(resources: Resources, dp: Float): Float = dp * resources.displayMetrics.density

    fun dpToPxInt(resources: Resources, dp: Float): Int = dpToPx(resources, dp).roundToInt()

    fun isTablet(resources: Resources): Boolean = resources.getBoolean(R.bool.is_tablet)
}

object AndroidNetworkStatus {
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun isUnmetered(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

/** Loads the Android-generated binary into the application-scoped common blocklist. */
fun loadAndroidAdBlocklist(resources: Resources, target: AdBlocklistService) {
    Thread({
        try {
            val encoded = resources.openRawResource(R.raw.adblockserverlist).use { it.readBytes() }
            target.install(encoded)
        } catch (error: Exception) {
            Log.e("HARMONIC_TAG", "Failed to load ad host blocklist", error)
        }
    }, "Harmonic-adblock-loader").start()
}
