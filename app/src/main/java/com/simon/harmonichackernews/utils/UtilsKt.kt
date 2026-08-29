package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.History

object UtilsKt {
    fun addHistories(ctx: Context, ids: Collection<Int>) {
        if (ids.isEmpty()) {
            return
        }
        val histories = HistoriesUtils.loadHistories(ctx, false)
        val now = System.currentTimeMillis()
        for (id in ids) {
            histories.add(History(id, now))
        }
        histories.sortByDescending { it.created }
        val serializedHistories = histories.joinToString("-") { history ->
            "${history.id}q${history.created}"
        }
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            HistoriesUtils.KEY_SHARED_PREFERENCES_HISTORIES,
            serializedHistories
        )
    }
}
