package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.History

object HistoriesUtils {

    fun init(context: Context) {
        histories.clear()
        histories.addAll(UtilsKt.loadHistories(context, true))
    }

    private val histories = mutableListOf<History>()

    fun size() = histories.size

    fun addHistory(context: Context, id: Int) {
        if (isHistoryExist(id).not()) {
            val now = System.currentTimeMillis()
            histories.add(History(id, now))
            UtilsKt.addHistory(context, id)
        }
    }

    fun getHistorybyId(id: Int): History? {
        return histories.find { it.id == id }
    }

    fun removeHistoryById(context: Context, id: Int) {
        histories.find { it.id == id }?.let { histories.remove(it) }
        UtilsKt.removeHistory(context, id)
    }

    fun isHistoryExist(id: Int): Boolean {
        return histories.any { it.id == id }
    }
}