package com.simon.harmonichackernews.presentation

data class StoryFrontDatePickerRequest(
    val initialDay: Long,
    val earliestDay: Long,
    val latestDay: Long,
)

data class StoryScrollRequest(val serial: Int, val delta: LayoutDelta) {
    val dy: Int get() = delta.value
}

data class StoryPredictiveBackSettleRequest(val serial: Int, val target: Float)

data class StoryPreviewOverlayState(
    val stories: List<StoryListItemSnapshot>,
    val cardBackgrounds: List<ArgbColor>,
    val initialPage: Int,
) {
    val cardColors: List<Int> = cardBackgrounds.map(ArgbColor::value)
}

data class StoryPreviewTarget(val story: StoryListItemSnapshot)

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
    val storyPagingAlphas: Map<Int, Float> = emptyMap(),
    val storyPreviewOverlay: StoryPreviewOverlayState? = null,
    val storyPreviewDismissRequestVersion: Int = 0,
    val storyPreviewBackGesture: BackGesture = BackGesture(),
    val storyPreviewPredictiveBackSettleRequest: StoryPredictiveBackSettleRequest? = null,
    val storyPreviewVoteLoadingId: Int = -1,
    val storyPreviewFavoriteLoadingId: Int = -1,
    val visibleStoryPreviewId: Int = -1,
    val suppressedStoryIds: Set<Int> = emptySet(),
) {
    val storyPreviewPredictiveBackProgress: Float get() = storyPreviewBackGesture.progress
    val storyPreviewPredictiveBackEdge: Int
        get() = when (storyPreviewBackGesture.edge) {
            BackGestureEdge.LEFT -> 0
            BackGestureEdge.RIGHT -> 1
            BackGestureEdge.UNKNOWN -> -1
        }
    val storyPreviewPredictiveBackTouchY: Float get() = storyPreviewBackGesture.pointerY
}

/**
 * Platform-neutral interaction state machine for the stories screen and its preview overlay.
 * Platform rendering retains measured source bounds, while request ordering, paging, search/back
 * transitions, and estimated paging distance are shared by every UI shell.
 */
class StoriesInteractionStore(
    private val defaultStoryExtent: LayoutDistance,
) {
    constructor(defaultStoryHeightPx: Int) : this(LayoutDistance(defaultStoryHeightPx))
    var state = StoriesInteractionState()
        private set

    private var requestSerial = 0
    private var mainStories: List<StoryListItemSnapshot> = emptyList()
    private var searchStories: List<StoryListItemSnapshot> = emptyList()
    private val storyItemExtents = mutableMapOf<Int, LayoutDistance>()

    init {
        require(defaultStoryExtent.value > 0) { "A positive default story height is required" }
    }

    fun updateContent(
        mainStories: List<StoryListItemSnapshot>,
        searchStories: List<StoryListItemSnapshot>,
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
        storyItemExtents.keys.retainAll(currentStoryIds)
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
        requestScrollBy(LayoutDelta(dy))
    }

    fun requestScrollBy(delta: LayoutDelta) {
        if (delta.value == 0) return
        state = state.copy(
            scrollRequest = StoryScrollRequest(
                serial = ++requestSerial,
                delta = LayoutDelta((state.scrollRequest?.dy ?: 0) + delta.value),
            ),
        )
    }

    fun consumeScrollRequest(request: StoryScrollRequest, consumedDy: Int = request.dy) {
        val current = state.scrollRequest ?: return
        if (current.serial < request.serial || consumedDy == 0) return
        val remainingDy = current.dy - consumedDy
        state = state.copy(
            scrollRequest = if (remainingDy == 0) {
                null
            } else {
                StoryScrollRequest(current.serial, LayoutDelta(remainingDy))
            },
        )
    }

    fun showStoryPreview(
        stories: List<StoryListItemSnapshot>,
        cardColors: List<Int>,
        openedStoryId: Int,
    ): Boolean {
        return showStoryPreviewWithBackgrounds(
            stories,
            cardColors.map(::ArgbColor),
            openedStoryId,
        )
    }

    fun showStoryPreviewWithBackgrounds(
        stories: List<StoryListItemSnapshot>,
        cardBackgrounds: List<ArgbColor>,
        openedStoryId: Int,
    ): Boolean {
        if (stories.isEmpty() || stories.size != cardBackgrounds.size) {
            return false
        }
        val initialPage = stories.indexOfFirst { it.id == openedStoryId }.takeIf { it >= 0 } ?: 0
        state = state.copy(
            storyPreviewDismissRequestVersion = 0,
            storyPreviewBackGesture = BackGesture(),
            storyPreviewPredictiveBackSettleRequest = null,
            storyPreviewOverlay = StoryPreviewOverlayState(
                stories = stories.toList(),
                cardBackgrounds = cardBackgrounds.toList(),
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
            storyPreviewBackGesture = BackGesture(),
            storyPreviewPredictiveBackSettleRequest = null,
            storyPreviewVoteLoadingId = -1,
            storyPreviewFavoriteLoadingId = -1,
            visibleStoryPreviewId = -1,
            storyPagingAlphas = emptyMap(),
            suppressedStoryIds = emptySet(),
            scrollRequest = null,
        )
        return true
    }

    fun updateStoryPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        updateStoryPreviewBackGesture(
            BackGesture(
                progress = progress.coerceIn(0f, 1f),
                edge = BackGestureEdge.fromLegacyValue(edge),
                pointerY = touchY,
            ),
        )
    }

    fun updateStoryPreviewBackGesture(gesture: BackGesture) {
        if (state.storyPreviewOverlay == null || state.storyPreviewDismissRequestVersion != 0) {
            return
        }
        state = state.copy(
            storyPreviewPredictiveBackSettleRequest = null,
            storyPreviewBackGesture = gesture,
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
            storyPreviewBackGesture = state.storyPreviewBackGesture.copy(
                progress = request.target,
            ),
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
        return StoryPreviewTarget(story)
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

    fun updateStoryItemHeight(storyId: Int, heightPx: Int): Boolean =
        updateStoryItemExtentValue(storyId, heightPx.coerceAtLeast(0))

    fun updateStoryItemExtent(storyId: Int, extent: LayoutDistance): Boolean =
        updateStoryItemExtentValue(storyId, extent.value)

    private fun updateStoryItemExtentValue(storyId: Int, extentValue: Int): Boolean {
        if (extentValue <= 0 || storyItemExtents[storyId]?.value == extentValue) return false
        storyItemExtents[storyId] = LayoutDistance(extentValue)
        return true
    }

    fun getAdjacentStoryPagingDistance(precedingStoryId: Int): Int {
        return storyItemExtents[precedingStoryId]
            ?.value
            ?.coerceAtLeast(1)
            ?: averageStoryHeight()
    }

    private fun averageStoryHeight(): Int {
        var total = 0
        var count = 0
        for (extent in storyItemExtents.values) {
            if (extent.value > 0) {
                total += extent.value
                count++
            }
        }
        return if (count == 0) defaultStoryExtent.value else total / count
    }
}
