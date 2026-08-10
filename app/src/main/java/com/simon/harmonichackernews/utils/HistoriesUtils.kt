package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.HistoryLedger

/** Shared history semantics with an Android SharedPreferences persistence adapter. */
object HistoriesUtils {
    const val KEY_SHARED_PREFERENCES_HISTORIES =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_HISTORIES"

    private val ledger = HistoryLedger()
    private var initialized = false

    fun init(context: Context) {
        ledger.initialize(readSerialized(context))
        initialized = true
    }

    fun size(): Int = ledger.size

    fun getChangeVersion(): Long = ledger.changeVersion

    fun addHistory(context: Context, id: Int, createdAtMillis: Long = System.currentTimeMillis()) {
        ensureInitialized(context)
        if (ledger.record(id, createdAtMillis)) persist(context)
    }

    fun getHistoryById(id: Int): History? = ledger.find(id)

    fun removeHistoryById(context: Context, id: Int) {
        ensureInitialized(context)
        if (ledger.remove(id)) persist(context)
    }

    fun clearHistories(context: Context) {
        ledger.clear()
        initialized = true
        persist(context)
    }

    fun isHistoryExist(id: Int): Boolean = ledger.contains(id)

    fun loadHistories(ctx: Context, sorted: Boolean): MutableList<History> =
        HistoryLedger.decodeHistories(readSerialized(ctx), sorted)

    private fun readSerialized(context: Context): String? =
        SettingsUtils.readStringFromSharedPreferences(
            context,
            KEY_SHARED_PREFERENCES_HISTORIES,
        )

    private fun persist(context: Context) {
        SettingsUtils.saveStringToSharedPreferences(
            context,
            KEY_SHARED_PREFERENCES_HISTORIES,
            ledger.serialize(),
        )
    }

    private fun ensureInitialized(context: Context) {
        if (!initialized) init(context)
    }
}
