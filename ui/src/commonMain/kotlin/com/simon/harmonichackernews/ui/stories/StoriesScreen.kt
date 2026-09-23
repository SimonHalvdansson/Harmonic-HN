@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories



import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.StoryRowModel
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.ui.common.ModalControlScrim
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors

internal val StoriesEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val NoTapToUpdateExitProgress: () -> Float = { 0f }

@Composable
fun StoriesScreen(
    controller: StoriesScreenController,
    mainListState: LazyListState = rememberLazyListState(),
    showTapToUpdateButton: Boolean = true,
    storyItemModelCacheKey: Int,
    storyItemModel: (
        StoryListItemSnapshot,
        Int,
        StoryDisplaySettings,
        StoryPreviewResourceState?,
        Long,
    ) -> StoryRowModel,
    filterColors: HarmonicFilterButtonColors,
    pullToRefreshEnabled: Boolean = true,
    showRefreshMenuItem: Boolean = false,
    onVisibleStoriesChanged: (List<StoryListItemSnapshot>) -> Unit = {},
) {
    val settings = controller.displaySettings ?: return
    val localHazeState = rememberHazeState()
    val hazeState = currentSharedHazeState() ?: localHazeState
    val mainState = mainListState
    val searchState = rememberLazyListState()
    val tapToUpdateExitClock = remember { Animatable(0f) }
    val tapToUpdateExitProgress = remember(tapToUpdateExitClock) {
        { tapToUpdateExitClock.value.coerceIn(0f, 1f) }
    }
    var suppressTapToUpdateRowExit by remember { mutableStateOf(false) }

    val tapToUpdateExitRequestVersion = controller.tapToUpdateExitRequestVersion
    LaunchedEffect(tapToUpdateExitRequestVersion) {
        if (tapToUpdateExitRequestVersion <= 0) return@LaunchedEffect
        var refreshStarted = false
        try {
            suppressTapToUpdateRowExit = true
            tapToUpdateExitClock.snapTo(0f)
            // Fade the existing list as one stable layer before the refresh reaches the store.
            tapToUpdateExitClock.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SavedListTransitionDurationMillis,
                    easing = StoriesEasing,
                ),
            )
            // Keep the list anchored until its rows have faded, then let the collapsed header
            // return with normal scroll motion instead of jumping fully into view in one frame.
            mainState.animateScrollToItem(0)
            controller.completeTapToUpdateExit()
            refreshStarted = true
            snapshotFlow { controller.tapToUpdateExitInProgress }.first { inProgress ->
                !inProgress
            }
            // Let the replacement publication commit while the layer is hidden, then reverse the
            // same layer transition. The retained keyed rows are not additions from LazyList's
            // perspective, so animateItem cannot provide their entrance animation.
            withFrameNanos { }
            tapToUpdateExitClock.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SavedListTransitionDurationMillis,
                    easing = StoriesEasing,
                ),
            )
        } finally {
            if (!refreshStarted) controller.cancelTapToUpdateExit()
            tapToUpdateExitClock.snapTo(0f)
            suppressTapToUpdateRowExit = false
        }
    }

    val scrollToTopRequestVersion = controller.scrollToTopRequestVersion
    var appliedTopAnchorVersion by remember(mainState) { mutableIntStateOf(0) }
    SideEffect {
        if (scrollToTopRequestVersion != appliedTopAnchorVersion) {
            appliedTopAnchorVersion = scrollToTopRequestVersion
            if (mainState.firstVisibleItemIndex == 0 &&
                mainState.firstVisibleItemScrollOffset == 0
            ) {
                // Keep the viewport at the top in the SAME measurement as the new keys.
                // Retaining the old first key would briefly collapse the header when that
                // story moves down the feed. Keeping the same index preserves row animations.
                mainState.requestScrollToItem(0)
            }
        }
    }
    LaunchedEffect(scrollToTopRequestVersion) {
        if (scrollToTopRequestVersion <= 0 || controller.mainStories.isEmpty()) {
            return@LaunchedEffect
        }
        // Menu refreshes can start partway down the feed; return with normal scroll motion.
        mainState.animateScrollToItem(0)
    }

    val settleRequest = controller.predictiveBackSettleRequest
    LaunchedEffect(settleRequest?.serial) {
        val request = settleRequest ?: return@LaunchedEffect
        val start = controller.predictiveBackProgress.coerceIn(0f, 1f)
        val distance = kotlin.math.abs(request.target - start)
        val animation = Animatable(start)
        animation.animateTo(
            targetValue = request.target,
            animationSpec = tween(
                durationMillis = (180 * distance).roundToInt().coerceAtLeast(1),
                easing = StoriesEasing,
            ),
        ) {
            controller.updatePredictiveBack(value)
        }
        if (request.target == 1f) {
            controller.listener.onCloseSearch()
            // Keep the completed predictive frame in place until the feature state confirms the
            // search has closed. Resetting the gesture after an arbitrary frame can briefly reveal
            // the still-active search layer when that state update arrives a frame later.
            snapshotFlow { controller.searching }.first { searching -> !searching }
        }
        controller.endPredictiveBack(request)
    }

    val scrollRequest = controller.scrollByRequest
    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { request ->
            // dispatchRawDelta is synchronous: applying and acknowledging this accumulated delta
            // cannot be split by cancellation when a newer pager sample arrives.
            val consumed = (if (controller.searching) searchState else mainState)
                .dispatchRawDelta(request.dy.toFloat())
                .roundToInt()
            // A list boundary cannot consume the requested movement. Treat that request as handled
            // so an unreachable anchor cannot keep getting re-issued.
            controller.consumeScrollBy(
                request,
                consumedDy = consumed.takeIf { it != 0 } ?: request.dy,
            )
        }
    }

    StoriesRoot(
        searching = controller.searching,
        suppressSearchAutoFocus = controller.suppressSearchAutoFocus,
        predictiveBackActive = controller.predictiveBackActive,
        predictiveBackProgress = controller.predictiveBackProgress,
        backgroundColor = HarmonicTheme.colors.background,
        mainLayer = {
            Box(Modifier.fillMaxSize().sharedHazeSource(hazeState)) {
                StoriesList(
                    controller = controller,
                    settings = settings,
                    stories = controller.mainStories,
                    listState = mainState,
                    searchMode = false,
                    // The animation clock, rather than the controller hand-off flag, owns visibility.
                    // This keeps the old rows hidden until the replacement frame has been committed.
                    tapToUpdateExitProgress = tapToUpdateExitProgress,
                    suppressTapToUpdateRowExit = suppressTapToUpdateRowExit,
                    storyItemModelCacheKey = storyItemModelCacheKey,
                    storyItemModel = storyItemModel,
                    filterColors = filterColors,
                    pullToRefreshEnabled = pullToRefreshEnabled,
                    showRefreshMenuItem = showRefreshMenuItem,
                    onVisibleStoriesChanged = onVisibleStoriesChanged,
                )
            }
        },
        searchLayer = {
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.searchStories,
                listState = searchState,
                searchMode = true,
                tapToUpdateExitProgress = NoTapToUpdateExitProgress,
                suppressTapToUpdateRowExit = false,
                storyItemModelCacheKey = storyItemModelCacheKey,
                storyItemModel = storyItemModel,
                filterColors = filterColors,
                pullToRefreshEnabled = pullToRefreshEnabled,
                showRefreshMenuItem = showRefreshMenuItem,
                onVisibleStoriesChanged = {},
            )
        },
        overlay = {
            if (showTapToUpdateButton) {
                StoryTapToUpdateButton(
                    controller = controller,
                    mainListState = mainState,
                    modifier = Modifier.zIndex(2f),
                    hazeState = hazeState,
                )
            }
        },
    )

    controller.frontDatePickerRequest?.let { request ->
        FrontPageDatePickerDialog(
            request = request,
            onDismiss = controller::dismissFrontDatePicker,
            onSelected = controller::selectFrontDate,
        )
    }
}

/** A host-positionable update control that can stay above story preview transition content. */
@Composable
fun BoxScope.StoryTapToUpdateButton(
    controller: StoriesScreenController,
    mainListState: LazyListState,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
    hazeState: HazeState? = currentSharedHazeState(),
) {
    val shape = RoundedCornerShape(16.dp)
    AnimatedVisibility(
        visible = controller.showRefreshPrompt && !controller.searching &&
            !controller.tapToUpdateExitInProgress,
        enter = fadeIn(tween(180, easing = StoriesEasing)),
        exit = fadeOut(tween(140, easing = StoriesEasing)),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
    ) {
        Box {
            ExtendedFloatingActionButton(
                onClick = {
                    val alreadyAtTop = mainListState.firstVisibleItemIndex == 0 &&
                        mainListState.firstVisibleItemScrollOffset == 0
                    if (alreadyAtTop) {
                        controller.refresh()
                    } else {
                        controller.beginTapToUpdateExit()
                    }
                },
                modifier = Modifier.widthIn(min = 189.dp)
                    .shadow(if (LocalHazeGlassEnabled.current) 2.dp else 6.dp, shape, clip = false)
                    .sharedHazeBackground(
                        glassAppearance = HazeGlassAppearance.FloatingButton,
                        hazeState = hazeState,
                        surfaceColor = HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f),
                        shape = shape,
                    )
                    .semantics { contentDescription = "Tap to update" },
                shape = shape,
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                contentColor = HarmonicTheme.colors.overlayButtonContent,
                icon = {
                    Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null)
                },
                text = {
                    Text(
                        "Tap to update",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
            ModalControlScrim(modalScrimAlpha, shape, modalScrimActive)
        }
    }
}
