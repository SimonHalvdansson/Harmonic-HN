package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.JSONParser

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
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
    fun keys(): Set<String>
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
}

object StoryCacheKeys {
    const val INDEX = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS"
    const val ARTICLE_URL = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL"
    const val ARTICLE_CHARSET =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET"

    const val FULL_NAMESPACE = "story_cache/full"
    const val SUMMARY_NAMESPACE = "story_cache/summary"
    const val ARTICLE_NAMESPACE = "article_cache"

    fun storyFile(storyId: Int): String = "$storyId.json"
    fun articleFile(storyId: Int): String = "$storyId.html"
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
    fun storeStory(storyId: Int, payload: String?, cachedAtMillis: Long): Boolean {
        if (storyId <= 0 || payload.isNullOrEmpty() || payload == JSONParser.ALGOLIA_ERROR_STRING) {
            return false
        }
        if (!files.write(
                StoryCacheKeys.FULL_NAMESPACE,
                StoryCacheKeys.storyFile(storyId),
                payload.encodeToByteArray(),
            )
        ) {
            return false
        }
        JSONParser.compactAlgoliaStoryResponse(payload, storyId)?.let { summary ->
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
        metadata.putStringSet(StoryCacheKeys.INDEX, update.encodedEntries)
        update.evictedStoryIds.forEach(::removeFilesAndArticleMetadata)
        return true
    }

    fun loadStoryPayload(storyId: Int): String? = storyId.takeIf { it > 0 }?.let {
        files.readText(StoryCacheKeys.FULL_NAMESPACE, StoryCacheKeys.storyFile(it))
    }

    fun hydrateStory(story: Story?): Boolean {
        story ?: return false
        if (story.id <= 0) return false
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

    fun hasRecentStories(nowMillis: Long): Boolean = recentEntries(nowMillis).any { entry ->
        Story().apply { id = entry.storyId }.let { hydrateStory(it) && !it.isComment }
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
        files.list(StoryCacheKeys.ARTICLE_NAMESPACE).forEach { file ->
            CacheFileNamePolicy.storyId(file.key, suffix = ".html")?.let(::add)
        }
    }

    fun remove(storyId: Int) {
        if (storyId <= 0) return
        metadata.putStringSet(
            StoryCacheKeys.INDEX,
            StoryCacheIndex.remove(metadata.getStringSet(StoryCacheKeys.INDEX), storyId),
        )
        removeFilesAndArticleMetadata(storyId)
    }

    fun clear(): Int {
        val count = cachedItemIds().size
        files.clear(StoryCacheKeys.FULL_NAMESPACE)
        files.clear(StoryCacheKeys.SUMMARY_NAMESPACE)
        files.clear(StoryCacheKeys.ARTICLE_NAMESPACE)
        metadata.keys().forEach { key ->
            if (key == StoryCacheKeys.INDEX ||
                key.startsWith(StoryCacheKeys.ARTICLE_URL) ||
                key.startsWith(StoryCacheKeys.ARTICLE_CHARSET)
            ) {
                metadata.remove(key)
            }
        }
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
        val charset = metadata.getString(StoryCacheKeys.ARTICLE_CHARSET + storyId)
            ?.takeIf(String::isNotBlank)
            ?: "UTF-8"
        return files.readText(StoryCacheKeys.ARTICLE_NAMESPACE, key, charset)
    }

    fun articleUrl(storyId: Int): String? = storyId.takeIf { it > 0 }?.let {
        metadata.getString(StoryCacheKeys.ARTICLE_URL + it)
    }

    fun recordArticleMetadata(storyId: Int, sourceUrl: String, contentType: String?) {
        if (storyId <= 0) return
        metadata.putString(StoryCacheKeys.ARTICLE_URL + storyId, sourceUrl)
        metadata.putString(
            StoryCacheKeys.ARTICLE_CHARSET + storyId,
            ArticleCacheMetadata.charsetName(contentType),
        )
    }

    fun removeArticleMetadata(storyId: Int) {
        if (storyId <= 0) return
        metadata.remove(StoryCacheKeys.ARTICLE_URL + storyId)
        metadata.remove(StoryCacheKeys.ARTICLE_CHARSET + storyId)
    }

    fun removeArticle(storyId: Int) {
        if (storyId <= 0) return
        files.remove(StoryCacheKeys.ARTICLE_NAMESPACE, StoryCacheKeys.articleFile(storyId))
        removeArticleMetadata(storyId)
    }

    private fun recentEntries(nowMillis: Long): List<StoryCacheEntry> =
        StoryCacheIndex.recentEntries(metadata.getStringSet(StoryCacheKeys.INDEX), nowMillis)

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

    private fun removeFilesAndArticleMetadata(storyId: Int) {
        val storyKey = StoryCacheKeys.storyFile(storyId)
        files.remove(StoryCacheKeys.FULL_NAMESPACE, storyKey)
        files.remove(StoryCacheKeys.SUMMARY_NAMESPACE, storyKey)
        removeArticle(storyId)
    }

    private companion object {
        const val DEFAULT_MAXIMUM_STORIES = 200
    }
}
