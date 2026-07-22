package com.simon.harmonichackernews;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.google.android.play.core.splitcompat.SplitCompat;

/** Application-level configuration for libraries that require process-wide coordination. */
public class HarmonicApplication extends Application implements Configuration.Provider {

    private static final int WORK_MANAGER_JOB_ID_MIN = 10_000;
    private static final int WORK_MANAGER_JOB_ID_MAX = 20_000;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        SplitCompat.install(this);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setJobSchedulerJobIdRange(
                        WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
                .build();
    }
}
