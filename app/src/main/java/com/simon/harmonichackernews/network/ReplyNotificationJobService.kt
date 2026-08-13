package com.simon.harmonichackernews.network

import android.app.job.JobParameters
import android.app.job.JobService
import com.simon.harmonichackernews.harmonicAppComposition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReplyNotificationJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            try {
                val result = checkNotNull(
                    this@ReplyNotificationJobService.harmonicAppComposition.replyNotifications,
                ).checkNow()
                val success = result !is ReplyCheckResult.Failed &&
                    result != ReplyCheckResult.UserNotFound
                jobFinished(params, !success)
            } catch (error: CancellationException) {
                throw error
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
