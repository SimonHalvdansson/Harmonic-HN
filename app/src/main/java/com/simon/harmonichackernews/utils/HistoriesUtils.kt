package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.History

object HistoriesUtils {

    fun init(context: Context) {
        histories.clear()
        histories.addAll(UtilsKt.loadHistories(context, true))
        changeVersion++
    }

    private val histories = mutableListOf<History>()
    private var changeVersion = 0L

    fun size() = histories.size

    fun getChangeVersion() = changeVersion

    fun addHistory(context: Context, id: Int) {
        if (isHistoryExist(id).not()) {
            val now = System.currentTimeMillis()
            histories.add(History(id, now))
            UtilsKt.addHistory(context, id)
            changeVersion++
        }
    }

    fun getHistorybyId(id: Int): History? {
        return histories.find { it.id == id }
    }

    fun removeHistoryById(context: Context, id: Int) {
        histories.find { it.id == id }?.let {
            histories.remove(it)
            changeVersion++
        }
        UtilsKt.removeHistory(context, id)
    }

    fun clearHistories(context: Context) {
        histories.clear()
        SettingsUtils.saveStringToSharedPreferences(
            context,
            UtilsKt.KEY_SHARED_PREFERENCES_HISTORIES,
            ""
        )
        changeVersion++
    }

    fun isHistoryExist(id: Int): Boolean {
        return histories.any { it.id == id }
    }
}
