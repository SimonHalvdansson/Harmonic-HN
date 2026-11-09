package com.simon.harmonichackernews.network

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.simon.harmonichackernews.awaitHarmonicAppComposition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel as cancelScope
import kotlinx.coroutines.launch

class ReplyNotificationJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            try {
                // A background launch can be the first graph consumer. Do not block the
                // JobService callback on preference migration or platform initialization.
                val app = awaitHarmonicAppComposition()
                val runtime = checkNotNull(app.replyNotifications)
                app.platform.accounts.awaitAccount()
                if (runtime.isEnabled && runtime.accountScoped && runtime.configuredUsername != app.accountActivityUsername) {
                    runtime.disable()
                    jobFinished(params, false)
                    return@launch
                }
                val connectivity = getSystemService(ConnectivityManager::class.java)
                if (runtime.activity.snapshot(runtime.configuredUsername).alerts.wifiOnly &&
                    connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                    jobFinished(params, true)
                    return@launch
                }
                // Subscription/checkpoint mutations retain the same owner as Settings.
                // Scanning, presentation and native publication dispatch their heavy work.
                val result = runtime.checkInBackground()
                val success = result !is ReplyCheckResult.Failed &&
                    result != ReplyCheckResult.UserNotFound
                jobFinished(params, !success)
                if (success) runtime.rescheduleChecks()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReplyNotifications", "Unable to complete reply check", error)
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        checkJob?.cancel()
        checkJob = null
        return true
    }

    override fun onDestroy() {
        serviceScope.cancelScope()
        super.onDestroy()
    }
}
