package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.AlgoliaStorySummary
import com.simon.harmonichackernews.network.JSONParser
import kotlin.concurrent.Volatile

data class CacheFileInfo(
    val key: String,
    val sizeBytes: Long,
)

/** Platform filesystem port for the legacy-compatible story and article cache layout. */
interface StoryCacheFileStore {
    fun read(namespace: String, key: String): ByteArray?
    fun readText(namespace: String, key: String, charsetName: String = "UTF-8"): String?
    fun write(namespace: String, key: String, value: ByteArray): Boolean
    fun remove(namespace: String, key: String): Boolean
    fun list(namespace: String): List<CacheFileInfo>
    fun clear(namespace: String)
    fun touch(namespace: String, key: String, modifiedAtMillis: Long)
}

/** Metadata port kept separate because existing Android data lives in SharedPreferences. */
interface StoryCacheMetadataStore {
    interface Editor {
        fun putString(key: String, value: String?)
        fun remove(key: String)
        fun putStringSet(key: String, value: Set<String>)
    }

    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
    fun keys(): Set<String>

    fun update(block: Editor.() -> Unit) {
        val target = this
        block(object : Editor {
            override fun putString(key: String, value: String?) = target.putString(key, value)
            override fun remove(key: String) = target.remove(key)
            override fun putStringSet(key: String, value: Set<String>) =
                target.putStringSet(key, value)
        })
    }
}

/** Non-persistent cache storage used by previews and hosts that have not supplied a filesystem. */
class InMemoryStoryCacheFileStore : StoryCacheFileStore {
    private data class Entry(
        val value: ByteArray,
        var modifiedAtMillis: Long = 0L,
    )

    private val entries = mutableMapOf<String, MutableMap<String, Entry>>()

    override fun read(namespace: String, key: String): ByteArray? =
        entries[namespace]?.get(key)?.value?.copyOf()

    override fun readText(namespace: String, key: String, charsetName: String): String? =
        read(namespace, key)?.decodeToString()

    override fun write(namespace: String, key: String, value: ByteArray): Boolean {
        entries.getOrPut(namespace, ::mutableMapOf)[key] = Entry(value.copyOf())
        return true
    }

    override fun remove(namespace: String, key: String): Boolean =
        entries[namespace]?.remove(key) != null

    override fun list(namespace: String): List<CacheFileInfo> = entries[namespace]
        ?.map { (key, entry) -> CacheFileInfo(key, entry.value.size.toLong()) }
        .orEmpty()

    override fun clear(namespace: String) {
        entries.remove(namespace)
    }

    override fun touch(namespace: String, key: String, modifiedAtMillis: Long) {
        entries[namespace]?.get(key)?.modifiedAtMillis = modifiedAtMillis
    }
}

/** Non-persistent metadata companion for [InMemoryStoryCacheFileStore]. */
class InMemoryStoryCacheMetadataStore : StoryCacheMetadataStore {
    private val strings = mutableMapOf<String, String>()
    private val sets = mutableMapOf<String, Set<String>>()

    override fun getString(key: String): String? = strings[key]

    override fun putString(key: String, value: String?) {
        if (value == null) strings.remove(key) else strings[key] = value
    }

    override fun remove(key: String) {
        strings.remove(key)
        sets.remove(key)
    }

    override fun getStringSet(key: String): Set<String> = sets[key].orEmpty()

    override fun putStringSet(key: String, value: Set<String>) {
        sets[key] = value.toSet()
    }

    override fun keys(): Set<String> = strings.keys + sets.keys

    override fun update(block: StoryCacheMetadataStore.Editor.() -> Unit) {
        val stagedStrings = strings.toMutableMap()
        val stagedSets = sets.toMutableMap()
        block(object : StoryCacheMetadataStore.Editor {
            override fun putString(key: String, value: String?) {
                if (value == null) stagedStrings.remove(key) else stagedStrings[key] = value
            }

            override fun remove(key: String) {
                stagedStrings.remove(key)
                stagedSets.remove(key)
            }

            override fun putStringSet(key: String, value: Set<String>) {
                stagedSets[key] = value.toSet()
            }
        })
        strings.clear()
        strings.putAll(stagedStrings)
        sets.clear()
        sets.putAll(stagedSets)
    }
}

object StoryCacheKeys {
    const val INDEX = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS"
    const val ARTICLE_URL = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL"
    const val ARTICLE_CHARSET =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET"

    const val FULL_NAMESPACE = "story_cache/full"
    const val SUMMARY_NAMESPACE = "story_cache/summary"
    const val PREPARED_NAMESPACE = "story_cache/prepared"
    const val ARTICLE_NAMESPACE = "article_cache"

    fun storyFile(storyId: Int): String = "$storyId.json"
    fun articleFile(storyId: Int): String = "$storyId.html"
    fun articleUrlKey(storyId: Int): String = ARTICLE_URL + storyId
    fun articleCharsetKey(storyId: Int): String = ARTICLE_CHARSET + storyId
}

object ArticleCacheMetadata {
    private val charsetPattern =
        Regex("charset\\s*=\\s*\\\"?([^;\\\"\\s]+)", RegexOption.IGNORE_CASE)

    fun charsetName(contentType: String?): String = contentType
        ?.let { charsetPattern.find(it)?.groupValues?.getOrNull(1) }
        ?.takeIf(String::isNotBlank)
        ?: "UTF-8"
}

/**
 * Common story/article cache workflow. Platforms provide bytes, charset decoding and metadata I/O;
 * payload compaction, hydration, indexing, eviction and legacy naming remain shared.
 */
class StoryCacheRepository(
    private val files: StoryCacheFileStore,
    private val metadata: StoryCacheMetadataStore,
    private val maximumStories: Int = DEFAULT_MAXIMUM_STORIES,
) {
    @Volatile
    private var recentStoryAvailability: RecentStoryAvailability? = null
    @Volatile
    private var indexedStoryIdsSnapshot: Set<Int>? = null

    fun storeStory(
        storyId: Int,
        payload: String?,
        cachedAtMillis: Long,
        parsedSummary: AlgoliaStorySummary? = null,
    ): Boolean {
        if (storyId <= 0 || payload.isNullOrEmpty() || payload == JSONParser.ALGOLIA_ERROR_STRING) {
            return false
        }
        // Invalidate first: a crash or failed raw write must never pair new JSON with old prepared
        // content. If removal failed and the entry still exists, leave the old pair untouched.
        val preparedKey = "$storyId.bin"
        if (!files.remove(StoryCacheKeys.PREPARED_NAMESPACE, preparedKey) &&
            files.read(StoryCacheKeys.PREPARED_NAMESPACE, preparedKey) != null
        ) return false
        if (!files.write(
                StoryCacheKeys.FULL_NAMESPACE,
                StoryCacheKeys.storyFile(storyId),
                payload.encodeToByteArray(),
            )
        ) {
            return false
        }
        (parsedSummary?.encode(storyId) ?: JSONParser.compactAlgoliaStoryResponse(payload, storyId))?.let { summary ->
            files.write(
                StoryCacheKeys.SUMMARY_NAMESPACE,
                StoryCacheKeys.storyFile(storyId),
                summary.encodeToByteArray(),
            )
        }

        val update = StoryCacheIndex.record(
            metadata.getStringSet(StoryCacheKeys.INDEX),
            storyId,
            cachedAtMillis,
            maximumStories,
        )
        metadata.update {
            putStringSet(StoryCacheKeys.INDEX, update.encodedEntries)
            update.evictedStoryIds.forEach { evictedStoryId ->
                remove(StoryCacheKeys.articleUrlKey(evictedStoryId))
                remove(StoryCacheKeys.articleCharsetKey(evictedStoryId))
            }
        }
        indexedStoryIdsSnapshot = StoryCacheIndex.storyIds(update.encodedEntries)
        update.evictedStoryIds.forEach(::removeFiles)
        if (storyId in indexedStoryIdsSnapshot.orEmpty()) {
            parsedSummary?.preparedThread?.let { prepared ->
                storePreparedThread(storyId, prepared.copy(rankedIds = parsedSummary.topLevelCommentIds.toList()))
            }
        }
        recentStoryAvailability = null
        return true
    }

    fun loadStoryPayload(storyId: Int): String? = storyId.takeIf { it > 0 }?.let {
        files.readText(StoryCacheKeys.FULL_NAMESPACE, StoryCacheKeys.storyFile(it))
    }

    fun hasStoryPayload(storyId: Int): Boolean = storyId > 0 && storyId in indexedStoryIds()

    fun loadPreparedThread(storyId: Int): PreparedCommentThread? {
        if (!hasStoryPayload(storyId)) return null
        val bytes = files.read(StoryCacheKeys.PREPARED_NAMESPACE, "$storyId.bin") ?: return null
        return PreparedCommentCodec.decode(bytes)?.takeIf { it.story.id == storyId }
    }

    /** Called under StoryCacheService's write lock, after the corresponding raw JSON is stored. */
    internal fun storePreparedThread(storyId: Int, thread: PreparedCommentThread): Boolean {
        if (!hasStoryPayload(storyId) || thread.story.id != storyId) return false
        val bytes = runCatching { PreparedCommentCodec.encode(thread) }.getOrNull() ?: return false
        return files.write(StoryCacheKeys.PREPARED_NAMESPACE, "$storyId.bin", bytes)
    }

    fun hydrateStory(story: Story?): Boolean {
        story ?: return false
        if (story.id <= 0) return false
        // The index is authoritative for story payloads. Most feed IDs are not cached, so avoid
        // probing both summary and full-payload files for every miss on the UI thread.
        if (story.id !in indexedStoryIds()) return false
        val summary = loadOrCreateSummary(story.id) ?: return false
        return JSONParser.updateStoryWithCachedStorySummary(story, summary)
    }

    fun savePreviewState(story: Story?): Boolean {
        story ?: return false
        if (story.id <= 0) return false
        val key = StoryCacheKeys.storyFile(story.id)
        val current = loadOrCreateSummary(story.id) ?: return false
        val updated = JSONParser.updateCachedStorySummaryPreviewState(current, story)
            ?.takeUnless { it == current }
            ?: return false
        return files.write(
            StoryCacheKeys.SUMMARY_NAMESPACE,
            key,
            updated.encodeToByteArray(),
        )
    }

    fun recentStories(nowMillis: Long): List<Story> = recentEntries(nowMillis).mapNotNull { entry ->
        Story().apply { id = entry.storyId }.takeIf { hydrateStory(it) && !it.isComment }
    }

    fun hasRecentStories(nowMillis: Long): Boolean {
        recentStoryAvailability?.takeIf { cached ->
            nowMillis >= cached.checkedAtMillis &&
                nowMillis <= cached.validThroughMillis
        }?.let { return it.available }

        val entry = recentEntries(nowMillis).firstOrNull { candidate ->
            Story().apply { id = candidate.storyId }.let { hydrateStory(it) && !it.isComment }
        }
        recentStoryAvailability = RecentStoryAvailability(
            available = entry != null,
            checkedAtMillis = nowMillis,
            // Bound reuse even though mutations invalidate the value. This also bounds staleness
            // if a background cache write races the UI thread's availability read.
            validThroughMillis = minOf(
                entry?.cachedAtMillis?.plus(StoryCacheIndex.DEFAULT_MAX_AGE_MILLIS)
                    ?: Long.MAX_VALUE,
                nowMillis + RECENT_AVAILABILITY_CACHE_MILLIS,
            ),
        )
        return entry != null
    }

    fun cachedItemIds(): Set<Int> = buildSet {
        addAll(StoryCacheIndex.storyIds(metadata.getStringSet(StoryCacheKeys.INDEX)))
        metadata.keys().forEach { key ->
            CacheFileNamePolicy.storyId(key, prefix = StoryCacheKeys.ARTICLE_URL)?.let(::add)
        }
        files.list(StoryCacheKeys.FULL_NAMESPACE).forEach { file ->
            CacheFileNamePolicy.storyId(file.key, suffix = ".json")?.let(::add)
        }
        files.list(StoryCacheKeys.SUMMARY_NAMESPACE).forEach { file ->
            CacheFileNamePolicy.storyId(file.key, suffix = ".json")?.let(::add)
        }
        files.list(StoryCacheKeys.PREPARED_NAMESPACE).forEach { file ->
            CacheFileNamePolicy.storyId(file.key, suffix = ".bin")?.let(::add)
        }
        files.list(StoryCacheKeys.ARTICLE_NAMESPACE).forEach { file ->
            CacheFileNamePolicy.storyId(file.key, suffix = ".html")?.let(::add)
        }
    }

    fun remove(storyId: Int) {
        if (storyId <= 0) return
        val updatedIndex = StoryCacheIndex.remove(metadata.getStringSet(StoryCacheKeys.INDEX), storyId)
        metadata.update {
            putStringSet(StoryCacheKeys.INDEX, updatedIndex)
            remove(StoryCacheKeys.articleUrlKey(storyId))
            remove(StoryCacheKeys.articleCharsetKey(storyId))
        }
        indexedStoryIdsSnapshot = StoryCacheIndex.storyIds(updatedIndex)
        removeFiles(storyId)
        recentStoryAvailability = null
    }

    fun clear(): Int {
        val count = cachedItemIds().size
        files.clear(StoryCacheKeys.FULL_NAMESPACE)
        files.clear(StoryCacheKeys.SUMMARY_NAMESPACE)
        files.clear(StoryCacheKeys.PREPARED_NAMESPACE)
        files.clear(StoryCacheKeys.ARTICLE_NAMESPACE)
        val cacheMetadataKeys = metadata.keys().filter { key ->
            if (key == StoryCacheKeys.INDEX ||
                key.startsWith(StoryCacheKeys.ARTICLE_URL) ||
                key.startsWith(StoryCacheKeys.ARTICLE_CHARSET)
            ) {
                true
            } else {
                false
            }
        }
        metadata.update { cacheMetadataKeys.forEach { key -> remove(key) } }
        indexedStoryIdsSnapshot = emptySet()
        recentStoryAvailability = null
        return count
    }

    fun loadArticle(storyId: Int, nowMillis: Long): String? {
        if (storyId <= 0) return null
        val key = StoryCacheKeys.articleFile(storyId)
        val file = files.list(StoryCacheKeys.ARTICLE_NAMESPACE).firstOrNull { it.key == key }
            ?: return null
        if (!ArticleSnapshotPolicy.isValidSize(file.sizeBytes)) {
            removeArticle(storyId)
            return null
        }
        files.touch(StoryCacheKeys.ARTICLE_NAMESPACE, key, nowMillis)
        val charset = metadata.getString(StoryCacheKeys.articleCharsetKey(storyId))
            ?.takeIf(String::isNotBlank)
            ?: "UTF-8"
        return files.readText(StoryCacheKeys.ARTICLE_NAMESPACE, key, charset)
    }

    fun articleUrl(storyId: Int): String? = storyId.takeIf { it > 0 }?.let {
        metadata.getString(StoryCacheKeys.articleUrlKey(it))
    }

    fun recordArticleMetadata(storyId: Int, sourceUrl: String, contentType: String?) {
        if (storyId <= 0) return
        metadata.update {
            putString(StoryCacheKeys.articleUrlKey(storyId), sourceUrl)
            putString(
                StoryCacheKeys.articleCharsetKey(storyId),
                ArticleCacheMetadata.charsetName(contentType),
            )
        }
    }

    fun removeArticleMetadata(storyId: Int) {
        if (storyId <= 0) return
        metadata.update {
            remove(StoryCacheKeys.articleUrlKey(storyId))
            remove(StoryCacheKeys.articleCharsetKey(storyId))
        }
    }

    fun removeArticle(storyId: Int) {
        if (storyId <= 0) return
        files.remove(StoryCacheKeys.ARTICLE_NAMESPACE, StoryCacheKeys.articleFile(storyId))
        removeArticleMetadata(storyId)
    }

    private fun recentEntries(nowMillis: Long): List<StoryCacheEntry> =
        StoryCacheIndex.recentEntries(metadata.getStringSet(StoryCacheKeys.INDEX), nowMillis)

    private fun indexedStoryIds(): Set<Int> {
        indexedStoryIdsSnapshot?.let { return it }
        // A concurrent first read may calculate the same immutable set twice; that is cheaper than
        // introducing a platform lock into common code and both snapshots are equivalent.
        return StoryCacheIndex.storyIds(metadata.getStringSet(StoryCacheKeys.INDEX)).also {
            indexedStoryIdsSnapshot = it
        }
    }

    private fun loadOrCreateSummary(storyId: Int): String? {
        val key = StoryCacheKeys.storyFile(storyId)
        files.readText(StoryCacheKeys.SUMMARY_NAMESPACE, key)?.takeIf(String::isNotEmpty)?.let {
            return it
        }
        val payload = files.readText(StoryCacheKeys.FULL_NAMESPACE, key)
        val summary = JSONParser.compactAlgoliaStoryResponse(payload, storyId) ?: return null
        files.write(StoryCacheKeys.SUMMARY_NAMESPACE, key, summary.encodeToByteArray())
        return summary
    }

    private fun removeFiles(storyId: Int) {
        val storyKey = StoryCacheKeys.storyFile(storyId)
        files.remove(StoryCacheKeys.FULL_NAMESPACE, storyKey)
        files.remove(StoryCacheKeys.SUMMARY_NAMESPACE, storyKey)
        files.remove(StoryCacheKeys.PREPARED_NAMESPACE, "$storyId.bin")
        files.remove(StoryCacheKeys.ARTICLE_NAMESPACE, StoryCacheKeys.articleFile(storyId))
    }

    private companion object {
        const val DEFAULT_MAXIMUM_STORIES = 200
        const val RECENT_AVAILABILITY_CACHE_MILLIS = 1_000L

        data class RecentStoryAvailability(
            val available: Boolean,
            val checkedAtMillis: Long,
            val validThroughMillis: Long,
        )
    }
}
