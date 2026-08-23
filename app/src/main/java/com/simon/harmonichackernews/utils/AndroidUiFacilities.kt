package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.simon.harmonichackernews.R
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

internal data class AndroidConnectivityStatus(
    val online: Boolean,
    val unmetered: Boolean,
) {
    companion object {
        val Offline = AndroidConnectivityStatus(online = false, unmetered = false)
    }
}

internal fun evaluateAndroidConnectivity(
    hasInternetCapability: Boolean,
    hasValidatedCapability: Boolean,
    hasUnmeteredCapability: Boolean,
): AndroidConnectivityStatus {
    val online = hasInternetCapability && hasValidatedCapability
    return AndroidConnectivityStatus(
        online = online,
        unmetered = online && hasUnmeteredCapability,
    )
}

internal object AndroidConnectivityCapabilities {
    fun evaluate(capabilities: NetworkCapabilities?): AndroidConnectivityStatus =
        if (capabilities == null) {
            AndroidConnectivityStatus.Offline
        } else {
            evaluateAndroidConnectivity(
                hasInternetCapability = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                ),
                hasValidatedCapability = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                ),
                hasUnmeteredCapability = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                ),
            )
        }
}

object AndroidNetworkStatus {
    fun isOnline(context: Context): Boolean = status(context).online

    fun isUnmetered(context: Context): Boolean = status(context).unmetered

    private fun status(context: Context): AndroidConnectivityStatus {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return AndroidConnectivityStatus.Offline
        val network = manager.activeNetwork ?: return AndroidConnectivityStatus.Offline
        return AndroidConnectivityCapabilities.evaluate(manager.getNetworkCapabilities(network))
    }
}
