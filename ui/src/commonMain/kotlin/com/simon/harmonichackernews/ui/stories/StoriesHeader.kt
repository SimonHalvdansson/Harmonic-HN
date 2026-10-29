@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.ui.content.AnimatedMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.IconButtonDefaults
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.theme.menuBackground
import com.simon.harmonichackernews.presentation.NewStoriesFilter

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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.simon.harmonichackernews.ui.content.contentTween
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

/** Hosts can provide a separate search control outside the scrolling header. */
internal val LocalStoriesSearchInHeader = staticCompositionLocalOf { true }
internal val LocalStoriesMoreMenuInHeader = staticCompositionLocalOf { true }
internal val LocalStoriesHeaderMenus = staticCompositionLocalOf<StoriesHeaderMenus?> { null }

internal class StoriesHeaderMenus {
    var typesExpanded by mutableStateOf(false)
    var moreExpanded by mutableStateOf(false)
}

@Composable
internal fun rememberStoriesHeaderMenus(controller: StoriesScreenController): StoriesHeaderMenus {
    val menus = remember(controller) { StoriesHeaderMenus() }
    LaunchedEffect(menus.typesExpanded, menus.moreExpanded) {
        controller.updateHeaderMenuVisibility(menus.typesExpanded || menus.moreExpanded)
    }
    LaunchedEffect(controller.headerMenuDismissRequestVersion) {
        if (controller.headerMenuDismissRequestVersion > 0) {
            menus.typesExpanded = false
            menus.moreExpanded = false
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.updateHeaderMenuVisibility(false) }
    }
    return menus
}

internal fun frontpageHeaderTopSpacing(compact: Boolean) = if (compact) 8.dp else 28.dp
internal fun frontpageHeaderBottomSpacing(compact: Boolean) = if (compact) 4.dp else 8.dp

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
    showWelcomeHint: Boolean = false,
    onDismissWelcomeHint: () -> Unit = {},
) {
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val startInset = with(density) { controller.presentation.contentInsetStartPx.toDp() }
    val settings = controller.displaySettings ?: return
    val compact = settings.compactHeader
    val topSpacing = frontpageHeaderTopSpacing(compact)
    val bottomSpacing = frontpageHeaderBottomSpacing(compact)

    val sideStart = 16.dp + startInset
    val sideEnd = 16.dp
    // Status content grows over rows retained by animateItem during their exit fade. Only the
    // controls need an opaque surface; extending it behind the spinner wipes those rows away.
    Column(modifier = modifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.pageBackground)
                // Inset the viewport, so scrolling chips cannot pass beneath side system UI.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = topInset + topSpacing),
        ) {
            if (searchMode) {
                SearchHeader(controller, sideStart, sideEnd)
            } else {
                MainHeader(
                    controller = controller,
                    onTypeSelected = onTypeSelected,
                    showRefreshMenuItem = showRefreshMenuItem,
                    showWelcomeHint = showWelcomeHint,
                    onDismissWelcomeHint = onDismissWelcomeHint,
                    modifier = Modifier.padding(start = sideStart, end = sideEnd),
                )
                if (controller.canFilterNewStories && controller.newStoriesFilter.active) {
                    NewStoriesFilterIndicator(
                        filter = controller.newStoriesFilter,
                        onEdit = controller::showNewStoriesFilterDialog,
                        onClear = { controller.applyNewStoriesFilter(NewStoriesFilter()) },
                        modifier = Modifier.padding(start = sideStart + 12.dp, end = sideEnd, top = 4.dp, bottom = 4.dp),
                    )
                }
                if (settings.listSelector == StoryListSelector.CHIPS) {
                    StoryTypeChips(
                        labels = controller.presentation.typeLabels,
                        selectedIndex = controller.presentation.selectedTypeIndex,
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

            AnimatedVisibility(visible = !searchMode && controller.presentation.showSavedFilter) {
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

            AnimatedVisibility(visible = !searchMode && controller.presentation.showFrontDate) {
                val dateButtonColors = ButtonDefaults.filledTonalButtonColors()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 10.dp, end = sideEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { controller.listener.onShiftFrontDate(-1) },
                        enabled = controller.presentation.frontPreviousEnabled,
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
                            controller.presentation.frontDateLabel,
                            fontFamily = rememberContentTypography(settings.font).family,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Button(
                        onClick = { controller.listener.onShiftFrontDate(1) },
                        enabled = controller.presentation.frontNextEnabled,
                        colors = dateButtonColors,
                        elevation = null,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_chevron_right), "Next front page day")
                    }
                }
            }

            val lastUpdated = controller.presentation.lastUpdatedText.takeIf { !searchMode }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                visible = !searchMode && controller.presentation.cacheProgressVisible,
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
                        targetState = controller.presentation.cacheProgressStatus,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    val targetProgress =
                        (controller.presentation.cacheProgress.toFloat() / controller.presentation.cacheProgressMax)
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
    showWelcomeHint: Boolean,
    onDismissWelcomeHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menus = LocalStoriesHeaderMenus.current ?: rememberStoriesHeaderMenus(controller)
    val settings = controller.displaySettings ?: return
    FrontpageHeaderControls(
        settings = settings,
        title = if (controller.presentation.showingCached) "Cached stories" else
            controller.presentation.typeLabels.getOrNull(controller.presentation.selectedTypeIndex) ?: "Stories",
        typeLabels = controller.presentation.typeLabels,
        selectedTypeIndex = controller.presentation.selectedTypeIndex,
        menus = menus,
        onTypeSelected = onTypeSelected,
        showRefreshMenuItem = showRefreshMenuItem,
        refreshing = controller.presentation.refreshing,
        onSearch = controller.listener::onOpenSearch,
        onRefresh = controller::refresh,
        modifier = modifier,
        moreMenu = { showRefresh, showSearch ->
            StoriesMoreMenuControl(
                controller = controller,
                menus = menus,
                showRefreshItem = showRefresh,
                showSearchItem = showSearch,
                showWelcomeHint = showWelcomeHint,
                onDismissWelcomeHint = onDismissWelcomeHint,
            )
        },
    )
}

/** The live frontpage and its settings preview share title sizing, selectors and control spacing. */
@Composable
internal fun FrontpageHeaderControls(
    settings: com.simon.harmonichackernews.presentation.StoryDisplaySettings,
    title: String,
    typeLabels: List<String>,
    selectedTypeIndex: Int,
    menus: StoriesHeaderMenus,
    onTypeSelected: (Int) -> Unit,
    showRefreshMenuItem: Boolean = false,
    refreshing: Boolean = false,
    onSearch: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
    moreMenu: @Composable (showRefresh: Boolean, showSearch: Boolean) -> Unit,
) {
    DisposableEffect(menus) {
        onDispose { menus.typesExpanded = false }
    }
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val useDropdown = settings.listSelector == StoryListSelector.DROPDOWN
    LaunchedEffect(useDropdown) {
        if (!useDropdown) menus.typesExpanded = false
    }
    val density = LocalDensity.current
    val searchInHeader = LocalStoriesSearchInHeader.current
    val moreInHeader = LocalStoriesMoreMenuInHeader.current
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
        val showRefreshButton = showRefreshMenuItem &&
            maxWidth.value >= titleWidth + 24f + 32f +
                (1 + (if (searchInHeader) 1 else 0) + (if (moreInHeader) 1 else 0)) * 48f
        val sizing = storyHeaderSizing(
            maxWidth.value - (if (showRefreshButton) 48f else 0f),
            titleWidth + 1f,
            minimumSize / preferredSize,
            searchInHeader = searchInHeader,
            moreInHeader = moreInHeader,
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        // Grow the hit/ripple bounds around the existing text origin.
                        .offset(x = (-4).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (useDropdown) Modifier.combinedClickable(
                                onClick = { menus.typesExpanded = true },
                                onLongClick = null,
                            ) else Modifier,
                        )
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = Triple(title, sizing, selectedTypeIndex),
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
                                color = MaterialTheme.colorScheme.onSurface,
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
                    AnimatedVisibility(
                        visible = useDropdown,
                        enter = expandHorizontally(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                        exit = shrinkHorizontally(tween(320, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_keyboard_arrow_down),
                            contentDescription = "Choose story list",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StoryTypeDropdownMenu(
                    expanded = useDropdown && menus.typesExpanded,
                    onDismiss = { menus.typesExpanded = false },
                    types = typeLabels.map(StoryType::fromLabel),
                    selectedType = StoryType.fromLabel(typeLabels.getOrNull(selectedTypeIndex)),
                    fontFamily = typography.family,
                    fontSize = typography.storiesDropdownItemSize.sp,
                    onSelected = { type ->
                        menus.typesExpanded = false
                        onTypeSelected(typeLabels.indexOf(type.label))
                    },
                )
            }
            if (searchInHeader && !sizing.searchInMenu) StoriesTooltip("Search") {
                IconButton(
                    onClick = onSearch,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_search),
                        "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = showRefreshButton,
                enter = expandHorizontally(tween(200), expandFrom = Alignment.End) + fadeIn(tween(150)),
                exit = shrinkHorizontally(tween(200), shrinkTowards = Alignment.End) + fadeOut(tween(100)),
            ) {
                StoriesTooltip("Refresh") {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(painterResource(Res.drawable.ic_refresh), "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (moreInHeader) {
                moreMenu(showRefreshMenuItem && !showRefreshButton, searchInHeader && sizing.searchInMenu)
            }
        }
    }
}

@Composable
internal fun StoriesMoreMenuControl(
    controller: StoriesScreenController,
    menus: StoriesHeaderMenus,
    showRefreshItem: Boolean = false,
    showSearchItem: Boolean = false,
    showWelcomeHint: Boolean = false,
    onDismissWelcomeHint: () -> Unit = {},
    buttonModifier: Modifier = Modifier,
) {
    DisposableEffect(menus) {
        onDispose { menus.moreExpanded = false }
    }
    Box {
        StoriesMenuWelcomeHint(showWelcomeHint, onDismissWelcomeHint) { highlighted ->
            val highlight by animateColorAsState(
                MaterialTheme.colorScheme.primary.copy(alpha = if (highlighted) .12f else 0f),
                animationSpec = contentTween(),
                label = "welcome menu highlight",
            )
            IconButton(
                onClick = {
                    onDismissWelcomeHint()
                    menus.moreExpanded = true
                },
                modifier = buttonModifier,
                colors = IconButtonDefaults.iconButtonColors(containerColor = highlight),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_more_vert),
                    "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StoriesMoreMenu(
            controller = controller,
            expanded = menus.moreExpanded,
            showRefreshItem = showRefreshItem,
            showSearchItem = showSearchItem,
            dismiss = { menus.moreExpanded = false },
        )
    }
}

@Composable
internal fun StoriesTooltip(
    description: String,
    enabled: Boolean = true,
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
        enableUserInput = enabled,
        content = content,
    )
}

@Composable
private fun SearchHeader(
    controller: StoriesScreenController,
    sideStart: androidx.compose.ui.unit.Dp,
    sideEnd: androidx.compose.ui.unit.Dp,
) {
    val colors = MaterialTheme.colorScheme
    StorySearchHeader(
        state = StorySearchPresentationState(
            active = controller.searching,
            draft = controller.searchDraft,
            suppressAutoFocus = controller.suppressSearchAutoFocus,
            sortLabel = controller.presentation.searchSortLabel,
            dateLabel = controller.presentation.searchDateLabel,
            pointsLabel = controller.presentation.searchPointsLabel,
            commentsLabel = controller.presentation.searchCommentsLabel,
            sortLabels = controller.presentation.searchSortLabels,
            dateLabels = controller.presentation.searchDateLabels,
            pointsLabels = controller.presentation.searchPointsLabels,
            commentsLabels = controller.presentation.searchCommentsLabels,
            onlyRead = controller.presentation.searchOnlyRead,
        ),
        sideStart = sideStart,
        sideEnd = sideEnd,
        iconColor = colors.onSurfaceVariant,
        menuColor = colors.menuBackground,
        menuTextColor = colors.onSurface,
        fontFamily = ProductSansFontFamily,
        onDraftChanged = controller::updateSearchDraft,
        onSearch = controller.listener::onSearch,
        onClose = controller.listener::onCloseSearch,
        consumeFocusRequest = controller.listener::consumeSearchFocusRequest,
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
                leadingIcon = { StoriesMenuIcon(Res.drawable.ic_search) },
                onClick = {
                    dismiss()
                    controller.listener.onOpenSearch()
                },
            )
        }
        if (controller.canFilterNewStories) {
            DropdownMenuItem(
                text = { HarmonicMenuText("Filter stories") },
                leadingIcon = { StoriesMenuIcon(Res.drawable.ic_filter_list) },
                onClick = {
                    dismiss()
                    controller.showNewStoriesFilterDialog()
                },
            )
        }
        if (showRefreshItem) {
            DropdownMenuItem(
                text = { HarmonicMenuText("Refresh") },
                leadingIcon = { StoriesMenuIcon(Res.drawable.ic_refresh) },
                onClick = {
                    dismiss()
                    controller.refresh()
                },
            )
        }
        AnimatedMenuItem(visible = controller.presentation.loggedIn) {
            Column {
                MoreItem("Profile", StoriesMenuAction.PROFILE, controller, dismiss)
                MoreItem("Submit", StoriesMenuAction.SUBMIT, controller, dismiss)
            }
        }
        MoreItem(if (controller.presentation.loggedIn) "Log out" else "Log in", StoriesMenuAction.ACCOUNT, controller, dismiss)
        AnimatedMenuItem(visible = controller.presentation.canCache) {
            MoreItem("Cache stories", StoriesMenuAction.CACHE, controller, dismiss)
        }
        AnimatedMenuItem(visible = controller.presentation.canClearHistory) {
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
        leadingIcon = {
            StoriesMenuIcon(when (action) {
                StoriesMenuAction.PROFILE -> Res.drawable.ic_account_circle
                StoriesMenuAction.SUBMIT -> Res.drawable.ic_send
                StoriesMenuAction.ACCOUNT -> if (controller.presentation.loggedIn) Res.drawable.ic_logout else Res.drawable.ic_login
                StoriesMenuAction.CACHE -> Res.drawable.ic_file_download
                StoriesMenuAction.CLEAR_HISTORY -> Res.drawable.ic_history
                StoriesMenuAction.SETTINGS -> Res.drawable.ic_settings
            })
        },
        onClick = {
            dismiss()
            controller.listener.onMoreAction(action)
        },
    )
}

@Composable
private fun StoriesMenuIcon(icon: DrawableResource) {
    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        selected = controller.presentation.savedFilter == value,
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
    val colors = MaterialTheme.colorScheme
    if (!searchMode && controller.canFilterNewStories && controller.newStoriesFilter.active &&
        controller.mainStories.isEmpty() && !controller.presentation.loading && !controller.presentation.loadingFailed
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Text("No stories match these filters", color = colors.onSurface, fontFamily = ProductSansFontFamily)
            Text("Try lower minimums or clear the filters.", color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily)
        }
    }
    StoryListStatus(
        state = StoryListStatusState(
            loading = controller.presentation.loading,
            loadingFailed = controller.presentation.loadingFailed,
            serverError = controller.presentation.loadingFailedServerError,
            failureMessage = controller.presentation.loadingFailedMessage,
            showCachedAction = controller.presentation.showCachedAction,
            showEmptySavedList = controller.presentation.showEmptySavedList,
            emptySavedListText = controller.presentation.emptySavedListText,
            emptySavedListIcon = controller.presentation.emptySavedListIcon,
            showEmptySearch = controller.presentation.showEmptySearch,
        ),
        searchMode = searchMode,
        normalColor = colors.onSurface,
        disabledColor = colors.onSurfaceVariant,
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
internal fun storyHeaderSizing(
    width: Float,
    titleWidth: Float,
    minimumScale: Float,
    searchInHeader: Boolean = true,
    moreInHeader: Boolean = true,
): StoryHeaderSizing {
    // Title padding, dropdown arrow, More, and an optional Search action.
    val fixedWidth = 12f + 24f + (if (moreInHeader) 48f else 0f) +
        if (searchInHeader) 48f else 0f
    val minimumGap = 4f
    val minimum = minimumScale.coerceIn(0f, 1f)
    val searchInMenu = searchInHeader && titleWidth * minimum + fixedWidth + minimumGap > width
    val available = (width - fixedWidth + if (searchInMenu) 48f else 0f).coerceAtLeast(0f)
    val scale = if (titleWidth > 0f) ((available - minimumGap) / titleWidth).coerceIn(minimum, 1f) else 1f
    val gap = (available - titleWidth * scale).coerceIn(minimumGap, 20f)
    return StoryHeaderSizing(scale, gap, searchInMenu)
}
