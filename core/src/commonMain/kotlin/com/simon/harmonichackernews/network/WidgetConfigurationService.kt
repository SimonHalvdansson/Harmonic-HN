package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.StoryPreferences

data class WidgetConfiguration(
    val storyType: StoryType = StoryType.TOP_STORIES,
    val feedName: String? = null,
    val visibleStoryCount: Int = DEFAULT_STORY_COUNT,
    val previewImageMode: StoryPreviewMode = StoryPreviewMode.OFF,
    val displayStyle: DisplayStyle = DisplayStyle.STANDARD,
    val tint: Boolean = false,
    val useHeadlineFont: Boolean = true,
) {
    // Keep official URLs for existing installations; other feeds use stable enum names.
    val feedUrl: String get() = storyType.hackerNewsUrl ?: storyType.name
    val fetchStoryCount: Int get() = visibleStoryCount.coerceIn(MIN_STORY_COUNT, MAX_STORY_COUNT) + 4

    companion object {
        const val MIN_STORY_COUNT = 8
        const val MAX_STORY_COUNT = 24
        const val DEFAULT_STORY_COUNT = 12

        fun fromStoryPreferences(preferences: StoryPreferences) = WidgetConfiguration(
            previewImageMode = preferences.previewImageMode.let {
                if (it == StoryPreviewMode.LARGE) StoryPreviewMode.MEDIUM else it
            },
            displayStyle = preferences.displayStyle,
            tint = preferences.tintCardUsingPreview,
        )
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
    feedLoader: suspend (StoryType, Int) -> StoryFeedResult = { type, _ ->
        StoryFeedResult.ItemIds(repository.getStoryIds(type))
    },
) {
    private val feed = WidgetFeedUseCase(repository, feedLoader)

    fun configuration(widgetId: Int, defaults: WidgetConfiguration = WidgetConfiguration()): WidgetConfiguration {
        // Only newly added widgets inherit current app preferences. Reconfiguration keeps the
        // independent choices saved for this widget, including older installations.
        if (!configStore.contains(key(FEED_TYPE, widgetId))) return defaults
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
            previewImageMode = StoryPreviewMode.fromStored(
                configStore.getString(key(PREVIEW_IMAGE, widgetId), StoryPreviewMode.OFF.storedValue),
            ).let { if (it == StoryPreviewMode.LARGE) StoryPreviewMode.MEDIUM else it },
            displayStyle = DisplayStyle.fromStored(configStore.getString(key(DISPLAY_STYLE, widgetId))),
            tint = configStore.getBoolean(key(TINT, widgetId), false),
            useHeadlineFont = configStore.getBoolean(key(HEADLINE_FONT, widgetId), true),
        )
    }

    fun save(widgetId: Int, configuration: WidgetConfiguration) {
        configStore.putString(key(FEED_TYPE, widgetId), configuration.feedUrl)
        configStore.putString(key(FEED_NAME, widgetId), configuration.feedName)
        configStore.putString(key(PREVIEW_IMAGE, widgetId), configuration.previewImageMode.storedValue)
        configStore.putString(key(DISPLAY_STYLE, widgetId), configuration.displayStyle.storedValue)
        configStore.putBoolean(key(TINT, widgetId), configuration.tint)
        configStore.putBoolean(key(HEADLINE_FONT, widgetId), configuration.useHeadlineFont)
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
        listOf(FEED_TYPE, FEED_NAME, STORY_COUNT, PREVIEW_IMAGE, DISPLAY_STYLE, TINT, HEADLINE_FONT).forEach {
            configStore.remove(key(it, widgetId))
        }
        listOf(LAST_UPDATED, SKIP_FETCH, REFRESHING).forEach {
            runtimeStore.remove(key(it, widgetId))
        }
    }

    private fun normalizeStoryCount(value: Int): Int =
        value.coerceIn(WidgetConfiguration.MIN_STORY_COUNT, WidgetConfiguration.MAX_STORY_COUNT)

    private fun key(prefix: String, widgetId: Int): String = prefix + widgetId

    private companion object {
        const val FEED_TYPE = "feed_type_"
        const val FEED_NAME = "feed_name_"
        const val STORY_COUNT = "story_count_"
        const val PREVIEW_IMAGE = "preview_image_"
        const val DISPLAY_STYLE = "display_style_"
        const val TINT = "tint_"
        const val HEADLINE_FONT = "headline_font_"
        const val LAST_UPDATED = "last_updated_"
        const val SKIP_FETCH = "skip_fetch_"
        const val REFRESHING = "refreshing_"
    }
}
