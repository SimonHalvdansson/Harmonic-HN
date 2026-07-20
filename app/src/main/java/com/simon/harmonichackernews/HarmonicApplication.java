package com.simon.harmonichackernews;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

/** Application-level configuration for libraries that require process-wide coordination. */
public class HarmonicApplication extends Application implements Configuration.Provider {

    private static final int WORK_MANAGER_JOB_ID_MIN = 10_000;
    private static final int WORK_MANAGER_JOB_ID_MAX = 20_000;

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setJobSchedulerJobIdRange(
                        WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
                .build();
    }
}
