package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Local user content, kept separately from settings, reading history, and disposable caches. */
class HiddenPostsStore(
    private val storage: KeyValueStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private var initialized = false
    private val mutableIds = MutableStateFlow<Set<Int>>(emptySet())
    val ids = mutableIds.asStateFlow()

    suspend fun initialize() = withContext(dispatcher) {
        mutex.withLock { initializeLocked() }
    }

    suspend fun hide(id: Int): Boolean = mutate { if (id > 0) it + id else it }
    suspend fun unhide(id: Int): Boolean = mutate { it - id }
    suspend fun clear(): Boolean = mutate { emptySet() }

    private suspend fun mutate(update: (Set<Int>) -> Set<Int>): Boolean = withContext(dispatcher) {
        mutex.withLock {
            initializeLocked()
            val next = update(mutableIds.value)
            if (next == mutableIds.value) return@withLock false
            storage.putString(STORAGE_KEY, next.joinToString(","))
            mutableIds.value = next
            true
        }
    }

    private fun initializeLocked() {
        if (initialized) return
        mutableIds.value = storage.getString(STORAGE_KEY).orEmpty().split(',')
            .mapNotNull { it.toIntOrNull()?.takeIf { id -> id > 0 } }.toSet()
        initialized = true
    }

    companion object {
        const val STORAGE_KEY = "com.simon.harmonichackernews.HIDDEN_POSTS"
    }
}
