package com.simon.harmonichackernews

import android.app.Application
import android.content.Context
import androidx.annotation.NonNull
import androidx.work.Configuration

/** Application-level configuration for libraries that require process-wide coordination.  */
class HarmonicApplication : Application(), Configuration.Provider {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        LOCAL_AI_SUPPORT.install(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setJobSchedulerJobIdRange(
                WORK_MANAGER_JOB_ID_MIN,
                WORK_MANAGER_JOB_ID_MAX
            )
            .build()

    companion object {
        private const val WORK_MANAGER_JOB_ID_MIN = 10000
        private const val WORK_MANAGER_JOB_ID_MAX = 20000
        private val LOCAL_AI_SUPPORT: LocalAiApplicationSupport = LocalAiApplicationSupportImpl()
    }
}
