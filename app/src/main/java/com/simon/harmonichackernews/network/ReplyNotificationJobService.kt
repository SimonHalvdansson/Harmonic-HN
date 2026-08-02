package com.simon.harmonichackernews.network

import android.app.job.JobParameters
import android.app.job.JobService
import com.simon.harmonichackernews.network.RepliesChecker.checkNow

class ReplyNotificationJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        checkNow(
            this,
            RepliesChecker.Callback { success: Boolean -> jobFinished(params, !success) })
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}
