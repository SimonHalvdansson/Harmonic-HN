package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Copy first and confirm durability before removing only the legacy cache keys. */
suspend fun migrateStoryResourceTints(
    legacy: KeyValueStore,
    target: KeyValueStore,
    awaitPersisted: suspend () -> Unit,
) {
    val marker = "legacy_tints_migrated"
    val source = StoryResourceTintRepository(legacy)
    if (!target.getBoolean(marker, false)) {
        val destination = StoryResourceTintRepository(target)
        destination.replaceEntries(source.entries() + destination.entries())
        target.putBoolean(marker, true)
    }
    awaitPersisted()
    if (legacy.keys().any { it.startsWith("story_resource_tint.") }) source.clear()
}

/** Immediate canonical colors with a single background writer for the disposable palette cache. */
class BufferedStoryResourceTintStore(
    scope: CoroutineScope,
    openStore: suspend () -> KeyValueStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val batchDelayMillis: Long = 100,
) : StoryResourceTintStore {
    private data class Snapshot(
        val revision: Long = 0,
        val entries: PersistentMap<String, StoryResourceTintState> = persistentMapOf(),
        val loaded: Boolean = false,
        val cleared: Boolean = false,
    )
    private val state = MutableStateFlow(Snapshot())
    private val persisted = MutableStateFlow(-1L)

    init {
        scope.launch(dispatcher) {
            // Storage/migration can fail transiently. Keep new colors in memory while retrying.
            val repository = retry { StoryResourceTintRepository(openStore()) }
            val restored = retry { repository.entries().toPersistentMap() }
            state.update { current ->
                current.copy(
                    entries = bounded(if (current.cleared) current.entries else restored.puttingAll(current.entries)),
                    loaded = true,
                )
            }
            persisted.value = 0
            var persistedEntries = restored
            state.collect {
                if (state.value.revision <= persisted.value) return@collect
                delay(batchDelayMillis)
                val batch = state.value
                retry { repository.updateEntries(persistedEntries, batch.entries) }
                persistedEntries = batch.entries
                persisted.value = batch.revision
            }
        }
    }

    override fun read(storyId: Int, kind: StoryResourceTintKind, sourceUrl: String,
        baseColorArgb: Int, paletteConfigKey: String): StoryResourceTintState? =
        state.value.entries[key(storyId, kind)]?.takeIf {
            it.sourceUrl == sourceUrl && it.baseColorArgb == baseColorArgb && it.paletteConfigKey == paletteConfigKey
        }

    override fun write(storyId: Int, kind: StoryResourceTintKind, tint: StoryResourceTintState) {
        val key = key(storyId, kind)
        state.update { current ->
            if (current.entries[key] == tint) current else current.copy(
                entries = bounded(current.entries.putting(key, tint)), revision = current.revision + 1,
            )
        }
    }

    override fun count(): Int = state.value.entries.size
    override fun clear() {
        state.update { it.copy(entries = persistentMapOf(), revision = it.revision + 1, cleared = true) }
    }

    suspend fun awaitLoaded() { state.first { it.loaded } }
    /** Wait for the batch to reach the backing store's in-memory snapshot. */
    suspend fun awaitPersisted() {
        val revision = state.value.revision
        persisted.first { it >= revision }
    }

    private suspend fun <T> retry(block: suspend () -> T): T {
        while (true) {
            try { return block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { delay(1_000) }
        }
    }

    private fun bounded(entries: PersistentMap<String, StoryResourceTintState>): PersistentMap<String, StoryResourceTintState> {
        var result = entries
        while (result.size > 512) result = result.removing(result.keys.first())
        return result
    }

    private fun key(storyId: Int, kind: StoryResourceTintKind) = "${kind.name.lowercase()}.$storyId"
}
