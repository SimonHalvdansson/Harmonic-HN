package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AppLaunchStateStore
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.settings.NighttimeScheduleStore

object AndroidAppearanceState {
    fun markWelcomeShown(context: Context) {
        AppLaunchStateStore(AndroidKeyValueStore.global(context)).markWelcomeDialogShown()
    }

    fun saveNighttimeSchedule(
        fromHour: Int,
        fromMinute: Int,
        toHour: Int,
        toMinute: Int,
        context: Context,
    ) {
        NighttimeScheduleStore(AndroidKeyValueStore.global(context)).save(
            NighttimeSchedule(fromHour, fromMinute, toHour, toMinute),
        )
    }

    fun nighttimeSchedule(context: Context): IntArray =
        NighttimeScheduleStore(AndroidKeyValueStore.global(context)).load().toIntArray()
}
