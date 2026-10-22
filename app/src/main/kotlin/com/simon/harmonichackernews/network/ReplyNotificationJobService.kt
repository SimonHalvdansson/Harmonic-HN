package com.simon.harmonichackernews.network

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
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
                val runtime = checkNotNull(awaitHarmonicAppComposition().replyNotifications)
                // Subscription/checkpoint mutations retain the same owner as Settings.
                // Scanning, presentation and native publication dispatch their heavy work.
                val result = runtime.checkNow()
                val success = result !is ReplyCheckResult.Failed &&
                    result != ReplyCheckResult.UserNotFound
                jobFinished(params, !success)
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
