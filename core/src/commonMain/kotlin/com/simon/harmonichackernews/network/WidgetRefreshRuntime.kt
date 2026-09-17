package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CancellationException

sealed interface WidgetRefreshResult {
    data object UseExisting : WidgetRefreshResult
    data class Loaded(
        val stories: List<Story>,
        val availableStoryCount: Int,
        val failedStoryCount: Int,
        val timedOut: Boolean,
    ) : WidgetRefreshResult
    data class Failed(val cause: Throwable? = null) : WidgetRefreshResult
}

/** Portable refresh/skip/reconciliation workflow; widget toolkits only render the outcome. */
class WidgetRefreshRuntime(
    private val widgets: WidgetConfigurationService,
    private val nowMillis: () -> Long,
) {
    suspend fun refresh(widgetId: Int, hasExistingStories: Boolean): WidgetRefreshResult {
        val state = widgets.runtime(widgetId)
        if (state.skipFetch && hasExistingStories) {
            if (state.refreshing) widgets.setRefreshing(widgetId, false)
            return WidgetRefreshResult.UseExisting
        }
        return try {
            when (val result = widgets.load(widgetId)) {
                is WidgetFeedResult.Loaded -> {
                    widgets.markUpdated(widgetId, nowMillis())
                    widgets.setSkipFetch(widgetId, true)
                    widgets.setRefreshing(widgetId, false)
                    WidgetRefreshResult.Loaded(
                        stories = result.stories,
                        availableStoryCount = result.availableStoryCount,
                        failedStoryCount = result.failedStoryCount,
                        timedOut = result.timedOut,
                    )
                }
                else -> {
                    widgets.setRefreshing(widgetId, false)
                    WidgetRefreshResult.Failed()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            widgets.setRefreshing(widgetId, false)
            WidgetRefreshResult.Failed(error)
        }
    }
}
