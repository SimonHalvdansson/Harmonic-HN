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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.layout.defaultMinSize
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
import com.simon.harmonichackernews.ui.common.HarmonicTooltip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.util.lerp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.settings.StoriesHeaderPill
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.contentTween
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
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
private val LocalStoriesHeaderIconColor = staticCompositionLocalOf<Color?> { null }

@Composable
internal fun storiesHeaderIconColor(): Color =
    LocalStoriesHeaderIconColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun storiesHeaderSideInset(settings: com.simon.harmonichackernews.presentation.StoryDisplaySettings): androidx.compose.ui.unit.Dp {
    val inset by animateDpAsState(
        if (settings.headerPill == StoriesHeaderPill.OFF) 16.dp
        else if (settings.hasBackground || settings.tintCardsFromImages) 12.dp else 8.dp,
        contentTween(), label = "Header pill outer margin",
    )
    return inset
}

@Composable
private fun storiesHeaderPillColors(pill: StoriesHeaderPill): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (pill) {
        StoriesHeaderPill.OFF -> colors.surface.copy(alpha = 0f) to colors.onSurface
        StoriesHeaderPill.PRIMARY -> colors.primary to colors.onPrimary
        StoriesHeaderPill.SECONDARY -> colors.secondary to colors.onSecondary
        StoriesHeaderPill.TERTIARY -> colors.tertiary to colors.onTertiary
    }
}

internal class StoriesHeaderMenus {
    var typesExpanded by mutableStateOf(false)
    var moreExpanded by mutableStateOf(false)
}

internal class FeedControlsReveal(private val threshold: Float) {
    private var travel = 0f
    var visible by mutableStateOf(false)
        private set

    fun reset() {
        travel = 0f
        visible = false
    }

    fun onScroll(delta: Float, atTop: Boolean, headerHidden: Boolean) {
        if (atTop) {
            reset()
            return
        }
        if (delta == 0f) return
        if (delta * travel < 0f) travel = 0f
        travel = (travel + delta).coerceIn(-threshold, threshold)
        if (travel <= -threshold) visible = false
        if (travel >= threshold && headerHidden) visible = true
    }
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
internal fun frontpageHeaderIdleStatusSpacing(compact: Boolean) = if (compact) 6.dp else 18.dp

// Both the scrolling and peeking positions reach zero on the same frame as the list.
internal fun storiesControlsOffsetPx(collapsePx: Int, heightPx: Float, peekLiftPx: Float, reveal: Float): Float {
    if (collapsePx <= 0 || heightPx <= 0f) return 0f
    val travel = collapsePx.toFloat().coerceIn(0f, heightPx)
    val fraction = travel / heightPx
    val eased = fraction * fraction * (3f - 2f * fraction)
    return -lerp(travel, peekLiftPx * eased, reveal)
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
    showWelcomeHint: Boolean = false,
    onDismissWelcomeHint: () -> Unit = {},
    collapsePx: () -> Int,
    revealControls: Boolean,
) {
    val settings = controller.displaySettings ?: return
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val startInset = with(density) { controller.presentation.contentInsetStartPx.toDp() }
    val topSpacing = frontpageHeaderTopSpacing(settings.compactHeader)
    var controlsHeight by remember {
        mutableStateOf(if (settings.headerPill == StoriesHeaderPill.OFF) 56.dp else 68.dp)
    }
    val reveal = animateFloatAsState(if (revealControls) 1f else 0f, tween(150), label = "Feed controls")
    val background = MaterialTheme.colorScheme.pageBackground
    Box(modifier.fillMaxWidth()) {
        StoriesHeaderContent(
            controller, onTypeSelected, searchMode, tapToUpdateExitProgress, suppressLastUpdated,
            filterColors, showFailureStatus, controlsHeight,
            Modifier.graphicsLayer { translationY = -collapsePx().toFloat() },
        )
        if (!searchMode) {
            // Move the existing controls independently of the status and filter rows. Their menus
            // keep a single anchor as the floating controls meet the regular header at the top.
            Box(
                Modifier.fillMaxWidth().graphicsLayer {
                    translationY = storiesControlsOffsetPx(collapsePx(), size.height, (topSpacing - 4.dp).toPx(), reveal.value)
                }.drawWithCache {
                    val fadeHeight = 16.dp.toPx()
                    val brush = Brush.verticalGradient(
                        colors = listOf(background, background.copy(alpha = 0f)),
                        startY = size.height,
                        endY = size.height + fadeHeight,
                    )
                    val edge = Offset(0f, size.height)
                    val fadeSize = Size(size.width, fadeHeight)
                    onDrawBehind {
                        drawRect(background)
                        val alpha = reveal.value * (collapsePx() / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        if (alpha > 0f) drawRect(brush, topLeft = edge, size = fadeSize, alpha = alpha)
                    }
                }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(top = topInset + topSpacing),
            ) {
                MainHeader(
                    controller, onTypeSelected, showRefreshMenuItem, showWelcomeHint, onDismissWelcomeHint,
                    modifier = Modifier.padding(
                        start = startInset + storiesHeaderSideInset(settings), end = storiesHeaderSideInset(settings),
                    ).onSizeChanged { controlsHeight = with(density) { it.height.toDp() } },
                    forceDropdown = revealControls,
                )
            }
        }
    }
}

@Composable
private fun StoriesHeaderContent(
    controller: StoriesScreenController,
    onTypeSelected: (Int) -> Unit,
    searchMode: Boolean,
    tapToUpdateExitProgress: () -> Float,
    suppressLastUpdated: Boolean,
    filterColors: HarmonicFilterButtonColors,
    showFailureStatus: Boolean,
    controlsHeight: Dp,
    modifier: Modifier,
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
                Spacer(Modifier.height(controlsHeight))
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

            AnimatedVisibility(
                visible = !searchMode && controller.presentation.showSavedFilter,
                enter = expandVertically() + fadeIn(tween(220, delayMillis = 80)),
                exit = shrinkVertically() + fadeOut(tween(100)),
            ) {
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
                    Spacer(Modifier.height(frontpageHeaderIdleStatusSpacing(compact)))
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = lastUpdated != null,
                    enter = fadeIn(tween(160, easing = StoriesEasing)),
                    exit = fadeOut(tween(120, easing = StoriesEasing)),
                ) {
                    Text(
                        text = lastUpdated.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = AppUiFontFamily,
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
                            fontFamily = AppUiFontFamily,
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
    forceDropdown: Boolean = false,
) {
    val menus = LocalStoriesHeaderMenus.current ?: rememberStoriesHeaderMenus(controller)
    val settings = controller.displaySettings ?: return
    FrontpageHeaderControls(
        settings = settings,
        title = controller.presentation.typeLabels.getOrNull(controller.presentation.selectedTypeIndex)
            .let { selected ->
                if (controller.presentation.showingCached && selected != StoryType.DOWNLOADS.label) "Cached stories"
                else selected ?: "Stories"
            },
        typeLabels = controller.presentation.typeLabels,
        selectedTypeIndex = controller.presentation.selectedTypeIndex,
        menus = menus,
        onTypeSelected = onTypeSelected,
        showRefreshMenuItem = showRefreshMenuItem,
        refreshing = controller.presentation.refreshing,
        onSearch = controller.listener::onOpenSearch,
        onRefresh = controller::refresh,
        modifier = modifier,
        forceDropdown = forceDropdown,
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
    forceDropdown: Boolean = false,
    moreMenu: @Composable (showRefresh: Boolean, showSearch: Boolean) -> Unit,
) {
    DisposableEffect(menus) {
        onDispose { menus.typesExpanded = false }
    }
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val useDropdown = forceDropdown || settings.listSelector == StoryListSelector.DROPDOWN
    LaunchedEffect(useDropdown) {
        if (!useDropdown) menus.typesExpanded = false
    }
    val density = LocalDensity.current
    val searchInHeader = LocalStoriesSearchInHeader.current
    val moreInHeader = LocalStoriesMoreMenuInHeader.current
    val textMeasurer = rememberTextMeasurer()
    val pillColors = storiesHeaderPillColors(settings.headerPill)
    // Retain the last opaque hue when switching off; only opacity should fade to zero.
    var lastPill by remember { mutableStateOf(settings.headerPill.takeUnless { it == StoriesHeaderPill.OFF } ?: StoriesHeaderPill.PRIMARY) }
    if (settings.headerPill != StoriesHeaderPill.OFF) lastPill = settings.headerPill
    val opaqueColor by animateColorAsState(storiesHeaderPillColors(lastPill).first, contentTween(), label = "Header pill hue")
    val pillAlpha by animateFloatAsState(if (settings.headerPill == StoriesHeaderPill.OFF) 0f else 1f,
        contentTween(), label = "Header pill opacity")
    val textColor by animateColorAsState(pillColors.second, contentTween(), label = "Header pill text")
    val iconColor by animateColorAsState(
        if (settings.headerPill == StoriesHeaderPill.OFF) MaterialTheme.colorScheme.onSurfaceVariant
        else pillColors.second,
        contentTween(), label = "Header pill icons",
    )
    val pillInset by animateDpAsState(
        if (settings.headerPill == StoriesHeaderPill.OFF) 0.dp else 12.dp,
        contentTween(), label = "Header pill padding",
    )
    val pillVerticalInset by animateDpAsState(
        if (settings.headerPill == StoriesHeaderPill.OFF) 0.dp else 6.dp,
        contentTween(), label = "Header pill vertical padding",
    )
    val titleScale by animateFloatAsState(
        if (settings.headerPill == StoriesHeaderPill.OFF) 1f else .8f,
        contentTween(), label = "Header pill title size",
    )
    val pillElevation by animateDpAsState(
        if (settings.headerPill != StoriesHeaderPill.OFF && settings.headerPillElevation) 10.dp else 0.dp,
        contentTween(), label = "Header pill elevation",
    )
    Box(
        modifier.fillMaxWidth().height(56.dp + pillVerticalInset * 2)
            .testTag("stories-header-controls"),
    ) {
        // Fade the opaque surface and its shadow as one layer. Fading only the fill lets
        // the shadow show through it and creates a dark flash while switching off.
        Box(
            Modifier.matchParentSize()
                // Alpha layers clip to their bounds; reserve room for the shadow while fading.
                .layout { measurable, constraints ->
                    val outset = 24.dp.roundToPx()
                    val surface = measurable.measure(constraints.offset(outset * 2, outset * 2))
                    layout(surface.width - outset * 2, surface.height - outset * 2) {
                        surface.place(-outset, -outset)
                    }
                }
                .graphicsLayer { alpha = pillAlpha }
                .padding(24.dp)
                .shadow(pillElevation, RoundedCornerShape(50), clip = false)
                .background(opaqueColor, RoundedCornerShape(50)),
        )
        BoxWithConstraints(
            Modifier.fillMaxSize()
                // The hidden pill must not clip the dropdown's own rounded ripple.
                .then(if (pillAlpha > 0f) Modifier.clip(RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = pillInset, vertical = pillVerticalInset),
        ) {
            val preferredSize = typography.storiesDropdownSelectedSize * titleScale
            val minimumSize = preferredSize * 0.65f
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
            CompositionLocalProvider(LocalStoriesHeaderIconColor provides iconColor) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                // Grow the hit/ripple bounds around the existing text origin.
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .offset(x = (-4).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (useDropdown) Modifier.combinedClickable(
                                        role = androidx.compose.ui.semantics.Role.Button,
                                        onClickLabel = "Choose story list",
                                        onClick = { menus.typesExpanded = true },
                                        onLongClick = null,
                                    ) else Modifier,
                                )
                                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedContent(
                                targetState = title to selectedTypeIndex,
                                contentKey = { it.first },
                                modifier = Modifier.weight(1f, fill = false),
                                contentAlignment = Alignment.CenterStart,
                                transitionSpec = {
                                    val direction = when {
                                        targetState.second > initialState.second -> 1
                                        targetState.second < initialState.second -> -1
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
                            ) { (visibleTitle, _) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = visibleTitle,
                                        color = textColor,
                                        fontFamily = typography.family,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = with(density) {
                                            (preferredSize * sizing.textScale).dp.toSp()
                                        },
                                        letterSpacing = 0.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                                    )
                                    Spacer(Modifier.width(sizing.arrowGap.dp))
                                }
                            }
                            AnimatedVisibility(
                                visible = useDropdown,
                                enter = fadeIn(tween(160, easing = LinearEasing)),
                                exit = fadeOut(tween(160, easing = LinearEasing)),
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_keyboard_arrow_down),
                                    contentDescription = "Choose story list",
                                    modifier = Modifier.size(24.dp),
                                    tint = iconColor,
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
                    if (searchInHeader && !sizing.searchInMenu) HarmonicTooltip("Search") {
                        IconButton(
                            onClick = onSearch,
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_search),
                                "Search",
                                tint = iconColor,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showRefreshButton,
                        enter = expandHorizontally(tween(200), expandFrom = Alignment.End) + fadeIn(tween(150)),
                        exit = shrinkHorizontally(tween(200), shrinkTowards = Alignment.End) + fadeOut(tween(100)),
                    ) {
                        HarmonicTooltip("Refresh") {
                            IconButton(onClick = onRefresh, enabled = !refreshing) {
                                Icon(painterResource(Res.drawable.ic_refresh), "Refresh",
                                    tint = iconColor)
                            }
                        }
                    }
                    if (moreInHeader) {
                        moreMenu(showRefreshMenuItem && !showRefreshButton, searchInHeader && sizing.searchInMenu)
                    }
                }
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
                    tint = storiesHeaderIconColor(),
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
            source = controller.presentation.searchSource,
        ),
        sideStart = sideStart,
        sideEnd = sideEnd,
        iconColor = colors.onSurfaceVariant,
        menuColor = colors.menuBackground,
        menuTextColor = colors.onSurface,
        fontFamily = AppUiFontFamily,
        onDraftChanged = controller::updateSearchDraft,
        onSearch = controller.listener::onSearch,
        onClose = controller.listener::onCloseSearch,
        consumeFocusRequest = controller.listener::consumeSearchFocusRequest,
        onOptionSelected = controller.listener::onSearchOption,
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
    if (!searchMode && controller.presentation.showEmptyFeed) {
        val filtered = controller.canFilterNewStories && controller.newStoriesFilter.active
        val downloads = controller.presentation.isDownloads
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Text(
                when {
                    downloads -> "No downloaded stories to show"
                    filtered -> "No stories match these filters"
                    else -> "No stories to show"
                },
                color = colors.onSurface, fontFamily = AppUiFontFamily,
            )
            if (downloads || filtered) {
                Text(
                    if (downloads) "Use Cache stories on a feed. Downloaded and recently cached discussions appear here for seven days."
                    else "Try lower minimums or clear the filters.",
                    color = colors.onSurfaceVariant, fontFamily = AppUiFontFamily,
                )
            }
            if (filtered) {
                androidx.compose.material3.TextButton(onClick = { controller.applyNewStoriesFilter(NewStoriesFilter()) }) {
                    Text("Clear filters")
                }
            }
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
        fontFamily = AppUiFontFamily,
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
