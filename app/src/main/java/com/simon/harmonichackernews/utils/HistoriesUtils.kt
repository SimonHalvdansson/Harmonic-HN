package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.History

object HistoriesUtils {
    const val KEY_SHARED_PREFERENCES_HISTORIES: String = "com.simon.harmonichackernews" +
            ".KEY_SHARED_PREFERENCES_HISTORIES"

    fun init(context: Context) {
        histories.clear()
        histories.addAll(loadHistories(context, true))
        historyIds.clear()
        for (history in histories) {
            historyIds.add(history.id)
        }
        changeVersion++
    }

    private val histories = mutableListOf<History>()
    private val historyIds = mutableSetOf<Int>()
    private var changeVersion = 0L

    fun size() = histories.size

    fun getChangeVersion() = changeVersion

    fun addHistory(context: Context, id: Int) {
        if (historyIds.add(id)) {
            val now = System.currentTimeMillis()
            histories.add(History(id, now))
            addHistoryToStorage(context, id, now)
            changeVersion++
        }
    }

    fun addHistories(context: Context, ids: Collection<Int>) {
        val newIds = ids.filter { isHistoryExist(it).not() }.distinct()
        if (newIds.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        for (id in newIds) {
            histories.add(History(id, now))
        }
        UtilsKt.addHistories(context, newIds)
        changeVersion++
    }

    fun getHistorybyId(id: Int): History? {
        return histories.find { it.id == id }
    }

    fun removeHistoryById(context: Context, id: Int) {
        histories.find { it.id == id }?.let {
            histories.remove(it)
            if (histories.none { history -> history.id == id }) {
                historyIds.remove(id)
            }
            changeVersion++
        }
        removeHistoryFromStorage(context, id)
    }

    fun clearHistories(context: Context) {
        histories.clear()
        historyIds.clear()
        SettingsUtils.saveStringToSharedPreferences(
            context,
            KEY_SHARED_PREFERENCES_HISTORIES,
            ""
        )
        changeVersion++
    }

    fun isHistoryExist(id: Int): Boolean {
        return historyIds.contains(id)
    }

    fun loadHistories(ctx: Context, sorted: Boolean): MutableList<History> {
        return loadHistories(
            sorted,
            SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_HISTORIES)
        )
    }

    private fun loadHistories(sorted: Boolean, historyString: String?): MutableList<History> {
        /* Format is {{ID}}q{{TIME}}-{{ID}}q{{TIME}}... */
        val loadedHistories: MutableList<History> = mutableListOf()
        if (historyString.isNullOrEmpty()) {
            return loadedHistories
        }

        var pairStart = 0
        while (pairStart < historyString.length) {
            val delimiterIndex = historyString.indexOf('-', pairStart)
            val pairEnd = if (delimiterIndex == -1) historyString.length else delimiterIndex
            val valueSeparator = historyString.indexOf('q', pairStart)

            if (valueSeparator in pairStart..<pairEnd && valueSeparator + 1 < pairEnd) {
                val extraSeparator = historyString.indexOf('q', valueSeparator + 1)
                if (extraSeparator == -1 || extraSeparator >= pairEnd) {
                    loadedHistories.add(
                        History(
                            historyString.substring(pairStart, valueSeparator).toInt(),
                            historyString.substring(valueSeparator + 1, pairEnd).toLong()
                        )
                    )
                }
            }

            pairStart = pairEnd + 1
        }

        if (sorted) {
            loadedHistories.sortByDescending { it.created }
        }

        return loadedHistories
    }

    private fun saveHistories(ctx: Context, histories: MutableList<History>) {
        val sb = StringBuilder()
        val size = histories.size

        for (i in 0..<size) {
            val history = histories[i]
            sb.append(history.id)
            sb.append("q")
            sb.append(history.created)
            if (i != size - 1) {
                sb.append("-")
            }
        }

        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_HISTORIES, sb.toString())
    }

    private fun addHistoryToStorage(ctx: Context, id: Int, created: Long) {
        val storedHistories: MutableList<History> = loadHistories(ctx, false)
        storedHistories.add(History(id, created))
        storedHistories.sortByDescending { it.created }
        saveHistories(ctx, storedHistories)
    }

    private fun removeHistoryFromStorage(ctx: Context, id: Int) {
        val storedHistories: MutableList<History> = loadHistories(ctx, false)
        val iterator = storedHistories.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == id) {
                iterator.remove()
                break
            }
        }

        saveHistories(ctx, storedHistories)
    }
}
