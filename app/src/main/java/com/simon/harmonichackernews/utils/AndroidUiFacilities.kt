package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.simon.harmonichackernews.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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

object AndroidAdBlocklist {
    @Volatile
    var hosts: AdHostBlocklist = AdHostBlocklist.empty()
        private set

    private val loading = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    fun load(resources: Resources) {
        if (!hosts.isEmpty || !loading.compareAndSet(false, true)) return
        executor.execute {
            try {
                val decoded = resources.openRawResource(R.raw.adblockserverlist).use { input ->
                    AdHostBlocklist.decode(input.readBytes())
                }
                if (!decoded.isEmpty) hosts = decoded
            } catch (error: Exception) {
                Log.e("HARMONIC_TAG", "Failed to load ad host blocklist", error)
            } finally {
                loading.set(false)
            }
        }
    }
}
