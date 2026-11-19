package com.simon.harmonichackernews.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Persistent, size-bounded metadata for offline bookmark and history search. */
class SavedStorySearchCache internal constructor(
    private val files: StoryCacheFileStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private var entries: MutableMap<String, Long>? = null
    private var sizeBytes = 0L

    internal suspend fun load(ids: List<Int>): Map<Int, SavedStorySearchMetadata> =
        withContext(dispatcher) {
            mutex.withLock {
                val index = index()
                buildMap {
                    for (id in ids) {
                        ensureActive()
                        val key = StoryCacheKeys.storyFile(id)
                        if (key !in index) continue
                        val text = files.readText(StoryCacheKeys.SAVED_SEARCH_NAMESPACE, key) ?: continue
                        val record = runCatching { Json.decodeFromString<SavedStorySearchMetadata>(text) }.getOrNull()
                        if (record?.story?.id == id) put(id, record)
                    }
                }
            }
        }

    internal suspend fun store(records: List<SavedStorySearchMetadata>) {
        if (records.isEmpty()) return
        withContext(dispatcher) {
            mutex.withLock {
                val index = index()
                for (record in records) {
                    ensureActive()
                    val key = StoryCacheKeys.storyFile(record.story.id)
                    val bytes = Json.encodeToString(record).encodeToByteArray()
                    if (bytes.size > MAX_ENTRY_BYTES) continue
                    val namespace = StoryCacheKeys.SAVED_SEARCH_NAMESPACE
                    if (!files.write(namespace, key, bytes)) continue
                    sizeBytes -= index.remove(key) ?: 0L
                    index[key] = bytes.size.toLong()
                    sizeBytes += bytes.size
                    while (sizeBytes > MAX_BYTES) {
                        val oldest = index.entries.first()
                        if (!files.remove(namespace, oldest.key)) break
                        sizeBytes -= index.remove(oldest.key) ?: 0L
                    }
                }
            }
        }
    }

    internal suspend fun clear() = withContext(dispatcher) {
        mutex.withLock {
            files.clear(StoryCacheKeys.SAVED_SEARCH_NAMESPACE)
            entries = null
            sizeBytes = 0
        }
    }

    private fun index(): MutableMap<String, Long> =
        entries ?: files.list(StoryCacheKeys.SAVED_SEARCH_NAMESPACE)
            .sortedBy { it.key.removeSuffix(".json").toIntOrNull() }
            .associateTo(linkedMapOf()) { it.key to it.sizeBytes }
            .also {
                entries = it
                sizeBytes = it.values.sum()
            }

    private companion object {
        const val MAX_ENTRY_BYTES = 128 * 1024
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}

@Serializable
internal data class SavedStorySearchMetadata(
    val story: StorySnapshot,
    val isLink: Boolean,
    val pdfTitle: String?,
    val videoTitle: String?,
) {
    fun toStory(read: Boolean): Story = Story().apply {
        applySnapshot(story)
        loaded = true
        isRead = read
        this.isLink = this@SavedStorySearchMetadata.isLink
        this.pdfTitle = this@SavedStorySearchMetadata.pdfTitle
        this.videoTitle = this@SavedStorySearchMetadata.videoTitle
    }

    companion object {
        fun from(story: Story) = SavedStorySearchMetadata(
            story.toSnapshot(), story.isLink, story.pdfTitle, story.videoTitle,
        )
    }
}
