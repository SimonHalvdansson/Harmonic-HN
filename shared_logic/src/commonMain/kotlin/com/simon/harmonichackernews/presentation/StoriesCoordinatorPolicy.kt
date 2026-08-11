package com.simon.harmonichackernews.presentation

private const val MILLIS_PER_DAY = 86_400_000L
private const val EARLIEST_FRONT_PAGE_EPOCH_DAY = 13_563L // 2007-02-19 UTC

/** Portable UTC-day state used by the historic front-page feed and its date picker. */
class FrontPageDayState(
    restoredMillis: Long,
    nowMillis: Long,
) {
    val earliestMillis: Long = EARLIEST_FRONT_PAGE_EPOCH_DAY * MILLIS_PER_DAY
    val latestMillis: Long = ((nowMillis / MILLIS_PER_DAY) - 1L)
        .coerceAtLeast(EARLIEST_FRONT_PAGE_EPOCH_DAY) * MILLIS_PER_DAY

    var selectedMillis: Long = restoredMillis
        .takeIf { it >= 0L }
        ?.let(::startOfUtcDay)
        ?.coerceIn(earliestMillis, latestMillis)
        ?: latestMillis
        private set

    val requestParameter: String
        get() = formatEpochDay(selectedMillis / MILLIS_PER_DAY)

    fun shift(days: Int): Boolean = select(selectedMillis + days * MILLIS_PER_DAY)

    fun select(millis: Long): Boolean {
        val next = startOfUtcDay(millis).coerceIn(earliestMillis, latestMillis)
        if (next == selectedMillis) return false
        selectedMillis = next
        return true
    }

    private fun startOfUtcDay(millis: Long): Long =
        (millis / MILLIS_PER_DAY) * MILLIS_PER_DAY

    private fun formatEpochDay(epochDay: Long): String {
        // Gregorian civil date conversion; keeps commonMain free of java.time/Calendar.
        val shifted = epochDay + 719_468L
        val era = if (shifted >= 0L) shifted / 146_097L else (shifted - 146_096L) / 146_097L
        val dayOfEra = shifted - era * 146_097L
        val yearOfEra = (
            dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L
        ) / 365L
        var year = yearOfEra + era * 400L
        val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
        val monthPiece = (5L * dayOfYear + 2L) / 153L
        val day = dayOfYear - (153L * monthPiece + 2L) / 5L + 1L
        val month = monthPiece + if (monthPiece < 10L) 3L else -9L
        if (month <= 2L) year++
        return year.toString().padStart(4, '0') + "-" +
            month.toString().padStart(2, '0') + "-" +
            day.toString().padStart(2, '0')
    }
}

enum class SavedListKind {
    HISTORY,
    FAVORITES,
    UPVOTED,
    BOOKMARKS,
}

object SavedListPresentationPolicy {
    fun emptyMessage(
        kind: SavedListKind,
        filter: SavedItemFilter,
        sourceHasItems: Boolean,
    ): String = when (kind) {
        SavedListKind.HISTORY -> "No history"
        SavedListKind.FAVORITES -> when {
            !sourceHasItems || filter == SavedItemFilter.BOTH -> "No favorites"
            filter == SavedItemFilter.STORIES -> "No favorite stories"
            else -> "No favorite comments"
        }
        SavedListKind.UPVOTED -> when {
            !sourceHasItems || filter == SavedItemFilter.BOTH -> "No upvoted items"
            filter == SavedItemFilter.STORIES -> "No upvoted stories"
            else -> "No upvoted comments"
        }
        SavedListKind.BOOKMARKS -> when {
            !sourceHasItems || filter == SavedItemFilter.BOTH -> "No bookmarks"
            filter == SavedItemFilter.STORIES -> "No bookmarked stories"
            else -> "No bookmarked comments"
        }
    }
}

data class SavedItemStoryReconciliation(
    val changed: Boolean,
    val stories: List<com.simon.harmonichackernews.data.Story>,
)

object SavedItemStoryReconciler {
    fun reconcile(
        currentStories: List<com.simon.harmonichackernews.data.Story>,
        currentCommentIds: Set<Int>,
        itemIds: List<Int>,
        commentIds: Set<Int>,
    ): SavedItemStoryReconciliation {
        val unchanged = currentCommentIds == commentIds &&
            currentStories.size == itemIds.size &&
            currentStories.indices.all { currentStories[it].id == itemIds[it] }
        if (unchanged) return SavedItemStoryReconciliation(false, currentStories)

        val existingById = currentStories.associateBy { it.id }
        val reconciled = itemIds.map { id ->
            (existingById[id] ?: com.simon.harmonichackernews.data.Story(
                "Loading...",
                id,
                false,
                false,
            )).also { story ->
                if (id in commentIds) story.isComment = true
            }
        }
        return SavedItemStoryReconciliation(true, reconciled)
    }
}

object StoryRowMergePolicy {
    fun mergeSummaryFields(
        target: com.simon.harmonichackernews.data.Story,
        source: com.simon.harmonichackernews.data.Story,
    ): Boolean {
        if (target.id != source.id) return false
        val changed = target.title != source.title ||
            target.descendants != source.descendants ||
            target.score != source.score ||
            target.time != source.time ||
            target.url != source.url
        if (changed) {
            target.title = source.title
            target.descendants = source.descendants
            target.score = source.score
            target.time = source.time
            target.url = source.url
        }
        return changed
    }
}

data class StoriesShellPresentationInput(
    val searching: Boolean,
    val submittedSearch: Boolean,
    val storyCount: Int,
    val searchLoading: Boolean,
    val loadingFailed: Boolean,
    val notFound: Boolean,
    val rateLimited: Boolean,
    val online: Boolean,
    val bookmarks: Boolean,
    val history: Boolean,
    val userItems: Boolean,
    val userItemsInitialLoadInProgress: Boolean,
    val refreshIndicatorShowing: Boolean,
    val showingCached: Boolean,
    val cacheInProgress: Boolean,
    val visibleStoryCount: Int,
)

data class StoriesShellPresentation(
    val showEmptySearch: Boolean,
    val showEmptySavedList: Boolean,
    val showLoading: Boolean,
    val loadingFailureMessage: String,
    val canCacheStories: Boolean,
)

/** Portable empty/loading/cache affordance decisions for the stories root shell. */
object StoriesShellPresentationPolicy {
    fun present(input: StoriesShellPresentationInput): StoriesShellPresentation {
        val empty = input.storyCount == 0
        val failed = input.loadingFailed || input.notFound
        return StoriesShellPresentation(
            showEmptySearch = input.searching && input.submittedSearch && empty &&
                !input.searchLoading && !failed,
            showEmptySavedList = !input.searching && empty && !failed && (
                input.bookmarks ||
                    input.history ||
                    input.userItems &&
                    !input.userItemsInitialLoadInProgress &&
                    !input.refreshIndicatorShowing
                ),
            showLoading = if (input.searching) {
                input.searchLoading
            } else {
                empty && !failed && !input.bookmarks && !input.history &&
                    (!input.userItems || input.userItemsInitialLoadInProgress)
            },
            loadingFailureMessage = when {
                input.rateLimited -> "Rate limited"
                !input.online -> "No internet connection"
                else -> "Loading failed"
            },
            canCacheStories = input.visibleStoryCount > 0 &&
                !input.showingCached && !input.cacheInProgress && input.online,
        )
    }
}
