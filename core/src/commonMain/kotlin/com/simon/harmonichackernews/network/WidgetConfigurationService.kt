package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.settings.KeyValueStore

data class WidgetConfiguration(
    val storyType: StoryType = StoryType.TOP_STORIES,
    val feedName: String? = null,
    val visibleStoryCount: Int = DEFAULT_STORY_COUNT,
) {
    val feedUrl: String get() = checkNotNull(storyType.hackerNewsUrl)
    val fetchStoryCount: Int get() = when (visibleStoryCount) {
        STORY_COUNT_SMALL -> 10
        STORY_COUNT_LARGE -> 28
        else -> 20
    }

    companion object {
        const val STORY_COUNT_SMALL = 8
        const val STORY_COUNT_MEDIUM = 16
        const val STORY_COUNT_LARGE = 24
        const val DEFAULT_STORY_COUNT = STORY_COUNT_MEDIUM
        val allowedStoryCounts = setOf(STORY_COUNT_SMALL, STORY_COUNT_MEDIUM, STORY_COUNT_LARGE)
    }
}

data class WidgetRuntimeState(
    val skipFetch: Boolean,
    val refreshing: Boolean,
    val lastUpdatedMillis: Long,
)

/**
 * Portable per-widget configuration, refresh bookkeeping, migration and feed loading. Native
 * widget APIs only render RemoteViews/Glance/WidgetKit and forward lifecycle events here.
 */
class WidgetConfigurationService(
    private val configStore: KeyValueStore,
    private val runtimeStore: KeyValueStore,
    repository: HackerNewsRepository,
) {
    private val feed = WidgetFeedUseCase(repository)

    fun configuration(widgetId: Int): WidgetConfiguration {
        val storyType = widgetStoryTypeForUrl(
            configStore.getString(key(FEED_TYPE, widgetId), StoryType.TOP_STORIES.hackerNewsUrl),
        )
        val rawCount = configStore.getInt(
            key(STORY_COUNT, widgetId),
            WidgetConfiguration.DEFAULT_STORY_COUNT,
        )
        return WidgetConfiguration(
            storyType = storyType,
            feedName = configStore.getString(key(FEED_NAME, widgetId)),
            visibleStoryCount = normalizeStoryCount(rawCount),
        )
    }

    fun save(widgetId: Int, configuration: WidgetConfiguration) {
        configStore.putString(key(FEED_TYPE, widgetId), configuration.feedUrl)
        configStore.putString(key(FEED_NAME, widgetId), configuration.feedName)
        configStore.putInt(
            key(STORY_COUNT, widgetId),
            normalizeStoryCount(configuration.visibleStoryCount),
        )
    }

    fun runtime(widgetId: Int): WidgetRuntimeState = WidgetRuntimeState(
        skipFetch = runtimeStore.getBoolean(key(SKIP_FETCH, widgetId), false),
        refreshing = runtimeStore.getBoolean(key(REFRESHING, widgetId), false),
        lastUpdatedMillis = runtimeStore.getLong(key(LAST_UPDATED, widgetId), 0L),
    )

    fun setSkipFetch(widgetId: Int, skip: Boolean) =
        runtimeStore.putBoolean(key(SKIP_FETCH, widgetId), skip)

    fun setRefreshing(widgetId: Int, refreshing: Boolean) =
        runtimeStore.putBoolean(key(REFRESHING, widgetId), refreshing)

    fun markUpdated(widgetId: Int, nowMillis: Long) =
        runtimeStore.putLong(key(LAST_UPDATED, widgetId), nowMillis)

    suspend fun load(widgetId: Int): WidgetFeedResult {
        val configuration = configuration(widgetId)
        return feed.load(
            WidgetFeedRequest(
                storyType = configuration.storyType,
                fetchCount = configuration.fetchStoryCount,
                visibleCount = configuration.visibleStoryCount,
            ),
        )
    }

    fun clear(widgetId: Int) {
        listOf(FEED_TYPE, FEED_NAME, STORY_COUNT).forEach {
            configStore.remove(key(it, widgetId))
        }
        listOf(LAST_UPDATED, SKIP_FETCH, REFRESHING).forEach {
            runtimeStore.remove(key(it, widgetId))
        }
    }

    private fun normalizeStoryCount(value: Int): Int = when (value) {
        in WidgetConfiguration.allowedStoryCounts -> value
        10 -> WidgetConfiguration.STORY_COUNT_SMALL
        20 -> WidgetConfiguration.STORY_COUNT_MEDIUM
        30, 40 -> WidgetConfiguration.STORY_COUNT_LARGE
        else -> WidgetConfiguration.DEFAULT_STORY_COUNT
    }

    private fun key(prefix: String, widgetId: Int): String = prefix + widgetId

    private companion object {
        const val FEED_TYPE = "feed_type_"
        const val FEED_NAME = "feed_name_"
        const val STORY_COUNT = "story_count_"
        const val LAST_UPDATED = "last_updated_"
        const val SKIP_FETCH = "skip_fetch_"
        const val REFRESHING = "refreshing_"
    }
}
