@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.flow.first
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton

internal inline fun calculateStoriesHeaderCollapsePx(
    headerHeightPx: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    precedingItemHeightPx: (Int) -> Int,
): Int {
    if (headerHeightPx <= 0) return 0

    var collapsePx = firstVisibleItemScrollOffset.coerceAtLeast(0)
    var precedingIndex = 0
    while (precedingIndex < firstVisibleItemIndex && collapsePx < headerHeightPx) {
        collapsePx += precedingItemHeightPx(precedingIndex).coerceAtLeast(0)
        precedingIndex++
    }
    return collapsePx.coerceAtMost(headerHeightPx)
}

@Composable
internal fun StoriesHeader(
    controller: StoriesScreenController,
    onTypeSelected: (Int) -> Unit,
    searchMode: Boolean,
    tapToUpdateExitProgress: () -> Float,
    suppressLastUpdated: Boolean,
    filterColors: HarmonicFilterButtonColors,
    showRefreshMenuItem: Boolean,
    showFailureStatus: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
    val settings = controller.displaySettings ?: return
    val compact = settings.compactHeader
    val topSpacing = if (compact) 8.dp else 28.dp
    val bottomSpacing = if (compact) 4.dp else 8.dp

    val sideStart = 16.dp + startInset + safeStart
    val sideEnd = 16.dp + safeEnd
    // Status content grows over rows retained by animateItem during their exit fade. Only the
    // controls need an opaque surface; extending it behind the spinner wipes those rows away.
    Column(modifier = modifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HarmonicTheme.colors.background)
                .padding(top = topInset + topSpacing),
        ) {
            if (searchMode) {
                SearchHeader(controller, sideStart, sideEnd)
            } else {
                MainHeader(
                    controller = controller,
                    onTypeSelected = onTypeSelected,
                    showRefreshMenuItem = showRefreshMenuItem,
                    modifier = Modifier.padding(start = sideStart, end = sideEnd),
                )
                if (settings.listSelector == StoryListSelector.CHIPS) {
                    StoryTypeChips(
                        labels = controller.typeLabels,
                        selectedIndex = controller.selectedTypeIndex,
                        fontFamily = rememberContentTypography(
                            settings.font,
                            settings.storyTextSize,
                        ).family,
                        contentPadding = PaddingValues(start = sideStart, end = sideEnd),
                        onSelected = onTypeSelected,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            AnimatedVisibility(visible = !searchMode && controller.showSavedFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 10.dp, end = sideEnd)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SavedFilterButton(
                        "Stories",
                        Res.drawable.ic_newspaper,
                        SavedItemFilter.STORIES,
                        0,
                        controller,
                        filterColors,
                        Modifier.weight(1f),
                    )
                    SavedFilterButton(
                        "All",
                        Res.drawable.ic_stacks,
                        SavedItemFilter.BOTH,
                        1,
                        controller,
                        filterColors,
                        Modifier.weight(1f),
                    )
                    SavedFilterButton(
                        "Comments",
                        Res.drawable.ic_comment,
                        SavedItemFilter.COMMENTS,
                        2,
                        controller,
                        filterColors,
                        Modifier.weight(1f),
                    )
                }
            }

            AnimatedVisibility(visible = !searchMode && controller.showFrontDate) {
                val dateButtonColors = ButtonDefaults.filledTonalButtonColors()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 10.dp, end = sideEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { controller.listener.onShiftFrontDate(-1) },
                        enabled = controller.frontPreviousEnabled,
                        colors = dateButtonColors,
                        elevation = null,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_chevron_left), "Previous front page day")
                    }
                    Button(
                        onClick = controller.listener::onPickFrontDate,
                        colors = dateButtonColors,
                        elevation = null,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_calendar_today), null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            controller.frontDateLabel,
                            fontFamily = rememberContentTypography(settings.font).family,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Button(
                        onClick = { controller.listener.onShiftFrontDate(1) },
                        enabled = controller.frontNextEnabled,
                        colors = dateButtonColors,
                        elevation = null,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_chevron_right), "Next front page day")
                    }
                }
            }

            val lastUpdated = controller.lastUpdatedText.takeIf { !searchMode }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = tween(220, easing = StoriesEasing),
                        alignment = Alignment.TopCenter,
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (lastUpdated == null) {
                    Spacer(Modifier.height(if (compact) 6.dp else 18.dp))
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = lastUpdated != null,
                    enter = fadeIn(tween(160, easing = StoriesEasing)),
                    exit = fadeOut(tween(120, easing = StoriesEasing)),
                ) {
                    Text(
                        text = lastUpdated.orEmpty(),
                        color = HarmonicTheme.colors.mutedText,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = sideStart, top = 4.dp, end = sideEnd)
                            .graphicsLayer {
                                val progress = tapToUpdateExitProgress()
                                alpha = if (suppressLastUpdated) {
                                    0f
                                } else {
                                    1f - progress
                                }
                                translationY = -8.dp.toPx() * progress
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AnimatedVisibility(
                visible = !searchMode && controller.cacheProgressVisible,
                enter = fadeIn(
                    tween(
                        durationMillis = 180,
                        delayMillis = 220,
                        easing = StoriesEasing,
                    ),
                ) + expandVertically(
                    animationSpec = tween(220, easing = StoriesEasing),
                ),
                exit = fadeOut(
                    tween(140, easing = StoriesEasing),
                ) + shrinkVertically(
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = 140,
                        easing = StoriesEasing,
                    ),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 8.dp, end = sideEnd),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedContent(
                        targetState = controller.cacheProgressStatus,
                        transitionSpec = {
                            fadeIn(
                                tween(
                                    durationMillis = 120,
                                    delayMillis = 90,
                                    easing = StoriesEasing,
                                ),
                            ) togetherWith fadeOut(
                                tween(90, easing = StoriesEasing),
                            )
                        },
                        label = "story cache status",
                    ) { status ->
                        Text(
                            text = status,
                            color = HarmonicTheme.colors.mutedText,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    val targetProgress =
                        (controller.cacheProgress.toFloat() / controller.cacheProgressMax)
                            .coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(300, easing = StoriesEasing),
                        label = "story cache progress",
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

        }

        Box(Modifier.padding(start = sideStart, end = sideEnd)) {
            HeaderStatus(
                controller = controller,
                searchMode = searchMode,
                showFailure = showFailureStatus,
            )
        }
    }
}

@Composable
private fun MainHeader(
    controller: StoriesScreenController,
    onTypeSelected: (Int) -> Unit,
    showRefreshMenuItem: Boolean,
    modifier: Modifier = Modifier,
) {
    var typesExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    val menuVisible = typesExpanded || moreExpanded
    LaunchedEffect(menuVisible) {
        controller.updateHeaderMenuVisibility(menuVisible)
    }
    LaunchedEffect(controller.headerMenuDismissRequestVersion) {
        if (controller.headerMenuDismissRequestVersion > 0) {
            typesExpanded = false
            moreExpanded = false
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.updateHeaderMenuVisibility(false) }
    }
    val settings = controller.displaySettings ?: return
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val useDropdown = settings.listSelector == StoryListSelector.DROPDOWN
    LaunchedEffect(useDropdown) {
        if (!useDropdown) typesExpanded = false
    }
    val density = LocalDensity.current
    val title = if (controller.showingCached) {
        "Cached stories"
    } else {
        controller.typeLabels.getOrNull(controller.selectedTypeIndex) ?: "Stories"
    }
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().height(56.dp)) {
        val preferredSize = typography.storiesDropdownSelectedSize
        val minimumSize = typography.storiesDropdownSelectedSize * 0.65f
        val titleWidth = with(density) {
            textMeasurer.measure(
                title,
                TextStyle(
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = preferredSize.dp.toSp(),
                    letterSpacing = 0.sp,
                ),
                softWrap = false,
            ).size.width.toDp().value
        }
        // Leave a pixel-rounding margin, and use the same tracking for measuring and drawing.
        val sizing = storyHeaderSizing(maxWidth.value, titleWidth + 1f, minimumSize / preferredSize)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        // Grow the hit/ripple bounds around the existing text origin.
                        .offset(x = (-4).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (useDropdown) Modifier.combinedClickable(
                                onClick = { typesExpanded = true },
                                onLongClick = null,
                            ) else Modifier,
                        )
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = Triple(title, sizing, controller.selectedTypeIndex),
                        contentKey = { it.first },
                        modifier = Modifier.weight(1f, fill = false),
                        contentAlignment = Alignment.CenterStart,
                        transitionSpec = {
                            val direction = when {
                                targetState.third > initialState.third -> 1
                                targetState.third < initialState.third -> -1
                                else -> 0
                            }
                            val travel = with(density) { 8.dp.roundToPx() } * direction
                            val enter = fadeIn(tween(120, delayMillis = 310, easing = LinearEasing)) +
                                slideInVertically(
                                    tween(120, delayMillis = 310, easing = FastOutSlowInEasing),
                                    initialOffsetY = { travel },
                                )
                            val exit = fadeOut(tween(90, easing = LinearEasing)) +
                                slideOutVertically(
                                    tween(90, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -travel },
                                )
                            (enter togetherWith exit)
                                .using(SizeTransform(clip = false) { _, _ ->
                                    tween(220, delayMillis = 90, easing = FastOutSlowInEasing)
                                })
                        },
                        label = "story section title",
                    ) { (visibleTitle, visibleSizing) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = visibleTitle,
                                color = HarmonicTheme.colors.contentPrimary,
                                fontFamily = typography.family,
                                fontWeight = FontWeight.Bold,
                                fontSize = with(density) {
                                    (preferredSize * visibleSizing.textScale).dp.toSp()
                                },
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                            )
                            Spacer(Modifier.width(visibleSizing.arrowGap.dp))
                        }
                    }
                    if (useDropdown) {
                        Icon(
                            painterResource(Res.drawable.ic_keyboard_arrow_down),
                            contentDescription = "Choose story list",
                            modifier = Modifier.size(24.dp),
                            tint = HarmonicTheme.colors.iconTint,
                        )
                    }
                }
                StoryTypeDropdownMenu(
                    expanded = useDropdown && typesExpanded,
                    onDismiss = { typesExpanded = false },
                    types = controller.typeLabels.map(StoryType::fromLabel),
                    selectedType = StoryType.fromLabel(controller.typeLabels.getOrNull(controller.selectedTypeIndex)),
                    fontFamily = typography.family,
                    fontSize = typography.storiesDropdownItemSize.sp,
                    onSelected = { type ->
                        typesExpanded = false
                        onTypeSelected(controller.typeLabels.indexOf(type.label))
                    },
                )
            }
            if (!sizing.searchInMenu) StoriesTooltip("Search") {
                IconButton(
                    onClick = controller.listener::onOpenSearch,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_search),
                        "Search",
                        tint = HarmonicTheme.colors.iconTint,
                    )
                }
            }
            Box {
                StoriesTooltip("More options") {
                    IconButton(
                        onClick = { moreExpanded = true },
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_more_vert),
                            "More options",
                            tint = HarmonicTheme.colors.iconTint,
                        )
                    }
                }
                StoriesMoreMenu(
                    controller = controller,
                    expanded = moreExpanded,
                    showRefreshItem = showRefreshMenuItem,
                    showSearchItem = sizing.searchInMenu,
                    dismiss = { moreExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun StoriesTooltip(
    description: String,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = tooltipState,
        content = content,
    )
}

@Composable
private fun SearchHeader(
    controller: StoriesScreenController,
    sideStart: androidx.compose.ui.unit.Dp,
    sideEnd: androidx.compose.ui.unit.Dp,
) {
    val colors = HarmonicTheme.colors
    StorySearchHeader(
        state = StorySearchPresentationState(
            active = controller.searching,
            draft = controller.searchDraft,
            suppressAutoFocus = controller.suppressSearchAutoFocus,
            sortLabel = controller.searchSortLabel,
            dateLabel = controller.searchDateLabel,
            pointsLabel = controller.searchPointsLabel,
            commentsLabel = controller.searchCommentsLabel,
            sortLabels = controller.searchSortLabels,
            dateLabels = controller.searchDateLabels,
            pointsLabels = controller.searchPointsLabels,
            commentsLabels = controller.searchCommentsLabels,
            onlyRead = controller.searchOnlyRead,
        ),
        sideStart = sideStart,
        sideEnd = sideEnd,
        iconColor = colors.iconTint,
        menuColor = colors.popupMenuBackground,
        menuTextColor = colors.textPrimary,
        fontFamily = ProductSansFontFamily,
        onDraftChanged = controller::updateSearchDraft,
        onSearch = controller.listener::onSearch,
        onClose = controller.listener::onCloseSearch,
        onOptionSelected = controller.listener::onSearchOption,
        onToggleOnlyRead = controller.listener::onToggleOnlyRead,
    )
}

@Composable
private fun StoriesMoreMenu(
    controller: StoriesScreenController,
    expanded: Boolean,
    showRefreshItem: Boolean,
    showSearchItem: Boolean,
    dismiss: () -> Unit,
) {
    HarmonicDropdownMenu(
        expanded = expanded,
        onDismiss = dismiss,
        modifier = Modifier.width(196.dp),
    ) {
        if (showSearchItem) {
            DropdownMenuItem(
                text = { HarmonicMenuText("Search") },
                onClick = {
                    dismiss()
                    controller.listener.onOpenSearch()
                },
            )
        }
        if (showRefreshItem) {
            DropdownMenuItem(
                text = { HarmonicMenuText("Refresh") },
                onClick = {
                    dismiss()
                    controller.refresh()
                },
            )
        }
        if (controller.loggedIn) {
            MoreItem("Profile", StoriesMenuAction.PROFILE, controller, dismiss)
            MoreItem("Submit", StoriesMenuAction.SUBMIT, controller, dismiss)
        }
        MoreItem(if (controller.loggedIn) "Log out" else "Log in", StoriesMenuAction.ACCOUNT, controller, dismiss)
        if (controller.canCache) {
            MoreItem("Cache stories", StoriesMenuAction.CACHE, controller, dismiss)
        }
        if (controller.canClearHistory) {
            MoreItem("Clear history", StoriesMenuAction.CLEAR_HISTORY, controller, dismiss)
        }
        MoreItem("Settings", StoriesMenuAction.SETTINGS, controller, dismiss)
    }
}

@Composable
private fun MoreItem(
    label: String,
    action: StoriesMenuAction,
    controller: StoriesScreenController,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { HarmonicMenuText(label) },
        onClick = {
            dismiss()
            controller.listener.onMoreAction(action)
        },
    )
}

@Composable
private fun SavedFilterButton(
    label: String,
    icon: DrawableResource,
    value: SavedItemFilter,
    position: Int,
    controller: StoriesScreenController,
    colors: HarmonicFilterButtonColors,
    modifier: Modifier,
) {
    HarmonicFilterButton(
        label = label,
        selected = controller.savedFilter == value,
        onClick = { controller.listener.onSavedFilterSelected(value) },
        position = position,
        colors = colors,
        modifier = modifier,
        icon = icon,
    )
}

@Composable
internal fun HeaderStatus(
    controller: StoriesScreenController,
    searchMode: Boolean,
    centerFailure: Boolean = false,
    showFailure: Boolean = true,
) {
    val colors = HarmonicTheme.colors
    StoryListStatus(
        state = StoryListStatusState(
            loading = controller.loading,
            loadingFailed = controller.loadingFailed,
            serverError = controller.loadingFailedServerError,
            failureMessage = controller.loadingFailedMessage,
            showCachedAction = controller.showCachedAction,
            showEmptySavedList = controller.showEmptySavedList,
            emptySavedListText = controller.emptySavedListText,
            emptySavedListIcon = controller.emptySavedListIcon,
            showEmptySearch = controller.showEmptySearch,
        ),
        searchMode = searchMode,
        normalColor = colors.contentPrimary,
        disabledColor = colors.mutedText,
        fontFamily = ProductSansFontFamily,
        loadingIndicator = { HarmonicLoadingIndicator(Modifier.size(48.dp)) },
        centerFailure = centerFailure,
        showFailure = showFailure,
        onRetry = controller::refresh,
        onShowCached = controller.listener::onShowCached,
    )
}

internal data class StoryHeaderSizing(val textScale: Float, val arrowGap: Float, val searchInMenu: Boolean)

/** Prefer a 20dp caret gap, reducing it before shrinking the title or moving Search. */
internal fun storyHeaderSizing(width: Float, titleWidth: Float, minimumScale: Float): StoryHeaderSizing {
    // Title padding, dropdown arrow, and two 48dp action targets.
    val fixedWidth = 12f + 24f + 96f
    val minimumGap = 4f
    val minimum = minimumScale.coerceIn(0f, 1f)
    val searchInMenu = titleWidth * minimum + fixedWidth + minimumGap > width
    val available = (width - fixedWidth + if (searchInMenu) 48f else 0f).coerceAtLeast(0f)
    val scale = if (titleWidth > 0f) ((available - minimumGap) / titleWidth).coerceIn(minimum, 1f) else 1f
    val gap = (available - titleWidth * scale).coerceIn(minimumGap, 20f)
    return StoryHeaderSizing(scale, gap, searchInMenu)
}
