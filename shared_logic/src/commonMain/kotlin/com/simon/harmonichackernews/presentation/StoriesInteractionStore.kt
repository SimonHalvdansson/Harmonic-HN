package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story

data class StoryFrontDatePickerRequest(
    val initialDay: Long,
    val earliestDay: Long,
    val latestDay: Long,
)

data class StoryScrollRequest(val serial: Int, val dy: Int)

data class StoryPredictiveBackSettleRequest(val serial: Int, val target: Float)

data class StoryPreviewOverlayState(
    val stories: List<Story>,
    val sourcePositions: List<Int>,
    val cardColors: List<Int>,
    val initialPage: Int,
)

data class StoryPreviewTarget(
    val story: Story,
    val sourcePosition: Int,
)

enum class StoryPreviewActionKind { Vote, Read, Bookmark, Favorite }

data class StoriesInteractionState(
    val searching: Boolean = false,
    val searchDraft: String = "",
    val suppressSearchAutoFocus: Boolean = false,
    val predictiveBackActive: Boolean = false,
    val predictiveBackProgress: Float = 0f,
    val predictiveBackSettleRequest: StoryPredictiveBackSettleRequest? = null,
    val frontDatePickerRequest: StoryFrontDatePickerRequest? = null,
    val scrollRequest: StoryScrollRequest? = null,
    val headerPinnedForPreview: Boolean = false,
    val storyPagingAlphas: Map<Int, Float> = emptyMap(),
    val storyPreviewOverlay: StoryPreviewOverlayState? = null,
    val storyPreviewDismissRequestVersion: Int = 0,
    val storyPreviewPredictiveBackProgress: Float = 0f,
    val storyPreviewPredictiveBackEdge: Int = 0,
    val storyPreviewPredictiveBackTouchY: Float = 0f,
    val storyPreviewPredictiveBackSettleRequest: StoryPredictiveBackSettleRequest? = null,
    val storyPreviewVoteLoadingId: Int = -1,
    val storyPreviewFavoriteLoadingId: Int = -1,
    val visibleStoryPreviewId: Int = -1,
    val suppressedStoryIds: Set<Int> = emptySet(),
)

/**
 * Platform-neutral interaction state machine for the stories screen and its preview overlay.
 * Platform rendering retains measured source bounds, while request ordering, paging, search/back
 * transitions, and estimated paging distance are shared by every UI shell.
 */
class StoriesInteractionStore(
    private val defaultStoryHeightPx: Int,
) {
    var state = StoriesInteractionState()
        private set

    private var requestSerial = 0
    private var mainStories: List<Story> = emptyList()
    private var searchStories: List<Story> = emptyList()
    private val storyItemHeights = mutableMapOf<Int, Int>()

    init {
        require(defaultStoryHeightPx > 0) { "A positive default story height is required" }
    }

    fun updateContent(
        mainStories: List<Story>,
        searchStories: List<Story>,
        searching: Boolean,
        lastSearch: String,
    ) {
        val enteringSearch = !state.searching && searching
        this.mainStories = mainStories.toList()
        this.searchStories = searchStories.toList()
        val currentStoryIds = buildSet(mainStories.size + searchStories.size) {
            mainStories.forEach { add(it.id) }
            searchStories.forEach { add(it.id) }
        }
        storyItemHeights.keys.retainAll(currentStoryIds)
        state = state.copy(
            searching = searching,
            searchDraft = when {
                enteringSearch -> lastSearch
                !searching -> ""
                else -> state.searchDraft
            },
            suppressSearchAutoFocus = if (enteringSearch) {
                false
            } else {
                state.suppressSearchAutoFocus
            },
        )
    }

    fun updateSearchDraft(value: String) {
        state = state.copy(searchDraft = value)
    }

    fun showFrontDatePicker(initialDay: Long, earliestDay: Long, latestDay: Long) {
        require(earliestDay <= latestDay) { "The earliest day must not follow the latest day" }
        state = state.copy(
            frontDatePickerRequest = StoryFrontDatePickerRequest(
                initialDay = initialDay.coerceIn(earliestDay, latestDay),
                earliestDay = earliestDay,
                latestDay = latestDay,
            ),
        )
    }

    fun dismissFrontDatePicker() {
        state = state.copy(frontDatePickerRequest = null)
    }

    fun selectFrontDate(day: Long): Long? {
        val request = state.frontDatePickerRequest ?: return null
        state = state.copy(frontDatePickerRequest = null)
        return day.coerceIn(request.earliestDay, request.latestDay)
    }

    fun beginPredictiveBack(progress: Float) {
        state = state.copy(
            predictiveBackSettleRequest = null,
            suppressSearchAutoFocus = true,
            predictiveBackActive = true,
            predictiveBackProgress = progress.coerceIn(0f, 1f),
        )
    }

    fun updatePredictiveBack(progress: Float) {
        state = state.copy(predictiveBackProgress = progress.coerceIn(0f, 1f))
    }

    fun settlePredictiveBack(target: Float) {
        state = state.copy(
            predictiveBackSettleRequest = StoryPredictiveBackSettleRequest(
                serial = ++requestSerial,
                target = target.coerceIn(0f, 1f),
            ),
        )
    }

    fun endPredictiveBack(request: StoryPredictiveBackSettleRequest? = null) {
        if (request != null && state.predictiveBackSettleRequest != request) return
        state = state.copy(
            predictiveBackSettleRequest = null,
            predictiveBackActive = false,
            predictiveBackProgress = 0f,
            suppressSearchAutoFocus = if (request?.target == 0f) {
                false
            } else {
                state.suppressSearchAutoFocus
            },
        )
    }

    fun requestScrollBy(dy: Int) {
        if (dy == 0) return
        state = state.copy(
            headerPinnedForPreview = true,
            scrollRequest = StoryScrollRequest(
                serial = ++requestSerial,
                dy = (state.scrollRequest?.dy ?: 0) + dy,
            ),
        )
    }

    fun unpinPreviewHeader() {
        state = state.copy(headerPinnedForPreview = false)
    }

    fun consumeScrollRequest(request: StoryScrollRequest) {
        if (state.scrollRequest == request) state = state.copy(scrollRequest = null)
    }

    fun showStoryPreview(
        stories: List<Story>,
        sourcePositions: List<Int>,
        cardColors: List<Int>,
        openedStoryId: Int,
    ): Boolean {
        if (stories.isEmpty() || stories.size != sourcePositions.size ||
            stories.size != cardColors.size
        ) {
            return false
        }
        val initialPage = stories.indexOfFirst { it.id == openedStoryId }.takeIf { it >= 0 } ?: 0
        state = state.copy(
            storyPreviewDismissRequestVersion = 0,
            storyPreviewPredictiveBackProgress = 0f,
            storyPreviewPredictiveBackSettleRequest = null,
            storyPreviewOverlay = StoryPreviewOverlayState(
                stories = stories.toList(),
                sourcePositions = sourcePositions.toList(),
                cardColors = cardColors.toList(),
                initialPage = initialPage,
            ),
            visibleStoryPreviewId = stories[initialPage].id,
            suppressedStoryIds = setOf(stories[initialPage].id),
        )
        return true
    }

    fun requestDismissStoryPreview() {
        if (state.storyPreviewOverlay == null || state.storyPreviewDismissRequestVersion != 0) {
            return
        }
        state = state.copy(storyPreviewDismissRequestVersion = ++requestSerial)
    }

    fun completeStoryPreviewDismiss(): Boolean {
        if (state.storyPreviewOverlay == null) return false
        state = state.copy(
            storyPreviewOverlay = null,
            storyPreviewDismissRequestVersion = 0,
            storyPreviewPredictiveBackProgress = 0f,
            storyPreviewPredictiveBackSettleRequest = null,
            storyPreviewVoteLoadingId = -1,
            storyPreviewFavoriteLoadingId = -1,
            visibleStoryPreviewId = -1,
            storyPagingAlphas = emptyMap(),
            suppressedStoryIds = emptySet(),
            headerPinnedForPreview = false,
        )
        return true
    }

    fun updateStoryPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        if (state.storyPreviewOverlay == null || state.storyPreviewDismissRequestVersion != 0) {
            return
        }
        state = state.copy(
            storyPreviewPredictiveBackSettleRequest = null,
            storyPreviewPredictiveBackEdge = edge,
            storyPreviewPredictiveBackTouchY = touchY,
            storyPreviewPredictiveBackProgress = progress.coerceIn(0f, 1f),
        )
    }

    fun cancelStoryPreviewPredictiveBack() {
        if (state.storyPreviewOverlay == null || state.storyPreviewPredictiveBackProgress <= 0f) {
            return
        }
        state = state.copy(
            storyPreviewPredictiveBackSettleRequest = StoryPredictiveBackSettleRequest(
                serial = ++requestSerial,
                target = 0f,
            ),
        )
    }

    fun finishStoryPreviewPredictiveBackSettle(request: StoryPredictiveBackSettleRequest) {
        if (state.storyPreviewPredictiveBackSettleRequest != request) return
        state = state.copy(
            storyPreviewPredictiveBackProgress = request.target,
            storyPreviewPredictiveBackSettleRequest = null,
        )
    }

    fun updateStoryPreviewPagePosition(lowerPage: Int, upperPage: Int, offset: Float) {
        val overlay = state.storyPreviewOverlay ?: return
        val lower = overlay.stories.getOrNull(lowerPage) ?: return
        val upper = overlay.stories.getOrNull(upperPage) ?: lower
        val normalizedOffset = offset.coerceIn(0f, 1f)
        state = state.copy(
            suppressedStoryIds = emptySet(),
            storyPagingAlphas = buildMap {
                put(lower.id, if (upperPage == lowerPage) 0f else normalizedOffset)
                if (upperPage != lowerPage) put(upper.id, 1f - normalizedOffset)
            },
        )
    }

    fun settleStoryPreviewPage(page: Int) {
        val storyId = state.storyPreviewOverlay?.stories?.getOrNull(page)?.id ?: return
        state = state.copy(visibleStoryPreviewId = storyId)
    }

    fun storyPreviewTarget(page: Int): StoryPreviewTarget? {
        val overlay = state.storyPreviewOverlay ?: return null
        val story = overlay.stories.getOrNull(page) ?: return null
        val sourcePosition = overlay.sourcePositions.getOrNull(page) ?: return null
        return StoryPreviewTarget(story, sourcePosition)
    }

    fun beginStoryPreviewAction(page: Int, action: StoryPreviewActionKind): StoryPreviewTarget? {
        val target = storyPreviewTarget(page) ?: return null
        state = when (action) {
            StoryPreviewActionKind.Vote -> state.copy(storyPreviewVoteLoadingId = target.story.id)
            StoryPreviewActionKind.Favorite ->
                state.copy(storyPreviewFavoriteLoadingId = target.story.id)
            StoryPreviewActionKind.Read, StoryPreviewActionKind.Bookmark -> state
        }
        return target
    }

    fun finishStoryPreviewAction(storyId: Int, action: StoryPreviewActionKind) {
        state = when (action) {
            StoryPreviewActionKind.Vote -> if (state.storyPreviewVoteLoadingId == storyId) {
                state.copy(storyPreviewVoteLoadingId = -1)
            } else {
                state
            }
            StoryPreviewActionKind.Favorite -> if (state.storyPreviewFavoriteLoadingId == storyId) {
                state.copy(storyPreviewFavoriteLoadingId = -1)
            } else {
                state
            }
            StoryPreviewActionKind.Read, StoryPreviewActionKind.Bookmark -> state
        }
    }

    fun clearStoryPagingAlphas() {
        state = state.copy(storyPagingAlphas = emptyMap())
    }

    fun updateStoryItemHeight(storyId: Int, heightPx: Int) {
        if (heightPx > 0) storyItemHeights[storyId] = heightPx
    }

    fun getStoryPagingDistance(firstStoryId: Int, secondStoryId: Int): Int {
        val activeStories = if (state.searching) searchStories else mainStories
        val first = activeStories.indexOfFirst { it.id == firstStoryId }
        val second = activeStories.indexOfFirst { it.id == secondStoryId }
        if (first < 0 || second < 0 || first == second) return averageStoryHeight()
        val start = minOf(first, second)
        val end = maxOf(first, second)
        return (start until end).sumOf { index ->
            storyItemHeights[activeStories[index].id]?.coerceAtLeast(1) ?: averageStoryHeight()
        }
    }

    private fun averageStoryHeight(): Int {
        val heights = storyItemHeights.values.filter { it > 0 }
        return if (heights.isEmpty()) defaultStoryHeightPx else heights.sum() / heights.size
    }
}
