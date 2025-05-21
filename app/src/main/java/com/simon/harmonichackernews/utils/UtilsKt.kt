package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.History

object UtilsKt {
    const val KEY_SHARED_PREFERENCES_HISTORIES: String = "com.simon.harmonichackernews" +
            ".KEY_SHARED_PREFERENCES_HISTORIES"

    fun loadHistories(ctx: Context, sorted: Boolean): MutableList<History> {
        return loadHistories(
            sorted,
            SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_HISTORIES)
        )
    }

    private fun loadHistories(sorted: Boolean, historyString: String?): MutableList<History> {
        /* Format is {{ID}}q{{TIME}}-{{ID}}q{{TIME}}... */

        val histories: MutableList<History> = mutableListOf()

        if (historyString.isNullOrEmpty()) {
            return histories
        }

        val pairs = historyString.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairs) {
            val info = pair.split("q".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (info.size == 2) {
                val h = History(info[0].toInt(), info[1].toLong())
                histories.add(h)
            }
        }

        if (sorted) {
            histories.sortByDescending { it.created }
        }

        return histories
    }

    private fun saveHistories(ctx: Context, histories: MutableList<History>) {
        val sb = StringBuilder()
        val size = histories.size

        for (i in 0..<size) {
            val b = histories[i]
            sb.append(b.id)
            sb.append("q")
            sb.append(b.created)
            if (i != size - 1) {
                sb.append("-")
            }
        }

        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_HISTORIES, sb.toString())
    }

    fun addHistory(ctx: Context, id: Int) {
        val histories: MutableList<History> = loadHistories(ctx, false)
        val b = History(id, System.currentTimeMillis())
        histories.add(b)
        histories.sortByDescending { it.created }
        saveHistories(ctx, histories)
    }

    fun removeHistory(ctx: Context, id: Int) {
        val bookmarks: MutableList<History> = loadHistories(ctx, false)

        for (bookmark in bookmarks) {
            if (bookmark.id == id) {
                bookmarks.remove(bookmark)
                break
            }
        }

        saveHistories(ctx, bookmarks)
    }
}