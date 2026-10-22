@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.settings.CommentsHeaderButton

import com.simon.harmonichackernews.ui.navigation.LocalBrowserPaneControls
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.presentation.CommentsSheetAction
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import com.simon.harmonichackernews.ui.content.ContentMotionEasing
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import com.simon.harmonichackernews.ui.content.contentTween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.flow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import androidx.compose.material3.Text
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.simon.harmonichackernews.settings.CommentsHeaderTint
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo
import com.simon.harmonichackernews.settings.CommentsHeaderLayout
import com.simon.harmonichackernews.settings.CommentsHeaderEdge
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.StoryTitleText
import com.simon.harmonichackernews.ui.content.storyTitlePresentation
import com.simon.harmonichackernews.ui.theme.rememberStoryTintColor
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal const val CommentsHeaderRevealDurationMillis = 320

/**
 * Platform-neutral comments header. The preview slot uses shared Coil and Harmonic palette UI;
 * the host supplies only surrounding platform actions such as opening links.
 */
@Composable
fun CommentsHeader(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
    storyPosterTag: String,
    tintBaseColor: Int,
    initialTint: Int?,
    headerTopPadding: Dp,
    bookmarksEnabled: Boolean,
    lastRefreshedText: String?,
    textStyle: TextStyle,
    previewPlatform: CommentsPreviewPlatform,
    includeStatusBarSpacer: Boolean = true,
    headerPreviewImageDisplayed: Boolean = false,
    onBrowserBack: (() -> Unit)? = null,
    headerTintProgress: Float? = null,
    faviconPreview: Painter? = null,
    previewCommentActions: Boolean = false,
    onButtonsOverflowChanged: (List<CommentsHeaderButton>) -> Unit = {},
    headerPreviewImage: @Composable (modifier: Modifier, visibleBackground: Color, onTintLoaded: (Int) -> Unit) -> Unit,
) {
    val effectiveMetaInfo = settings.headerMetaInfo.resolve(settings.hasAccountDetails)
    val detailSettings = settings.copy(headerMetaInfo = effectiveMetaInfo)
    val headerSheetProgress = if (controller.sideBySideActive) 1f else controller.sheetSlideOffset
    val density = LocalDensity.current
    // Keep derived header objects keyed to the immutable story revision supplied by the store.
    val story = remember(controller.story, contentVersion) { controller.story }
    val storyTitle = storyTitlePresentation(
        title = story.title,
        pdfTitle = story.pdfTitle,
        videoTitle = story.videoTitle,
    )
    val pollOptions = remember(story.pollOptions, contentVersion) {
        story.pollOptions?.map { option ->
            PollOptionUi(
                id = option.id,
                loaded = option.loaded,
                loadFailed = option.loadFailed,
                text = option.text,
                points = option.points,
            )
        }
    }
    val colors = MaterialTheme.colorScheme
    val headerTypography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    val showHeaderShimmer = !story.loaded && story.title.isNullOrBlank() && !controller.loadingFailed
    var loadedTint by remember(story.id, contentVersion, initialTint) {
        mutableStateOf(initialTint)
    }
    val normalBackground = colors.pageBackground
    val correctedTint = rememberStoryTintColor(loadedTint, settings.paletteTintMode)
    val targetBackground = if (settings.tintHeader && !showHeaderShimmer) {
        if (settings.headerTint == CommentsHeaderTint.THEME) colors.secondaryContainer
        else correctedTint ?: Color(tintBaseColor)
    } else {
        normalBackground
    }
    val headerBackground by androidx.compose.animation.animateColorAsState(
        targetValue = targetBackground,
        label = "comments header tint",
    )
    val visibleHeaderBackground = lerpCommentsColor(
        normalBackground,
        headerBackground,
        headerTintProgress ?: headerSheetProgress,
    )
    val summaryContainerColor = if (settings.tintHeader) {
        lerpCommentsColor(colors.surfaceContainerHigh, visibleHeaderBackground, 0.52f)
    } else {
        colors.surfaceContainerHigh
    }
    LaunchedEffect(visibleHeaderBackground, headerBackground) {
        controller.updateStatusBarHeaderColor(visibleHeaderBackground, headerBackground)
        controller.listener.onHeaderColorChanged(visibleHeaderBackground.toArgb())
    }
    val topSpacer = if (includeStatusBarSpacer) {
        with(density) {
            (WindowInsets.statusBars.getTop(this) * headerSheetProgress).roundToInt().toDp()
        }
    } else {
        0.dp
    }
    val sideMarginStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val sideMarginEnd = with(density) { controller.contentInsetRightPx.toDp() }
    val sheetHandleInset = if (controller.integratedWebView && controller.showSheetControls) {
        (CommentsSheetHandleTopPadding + CommentsSheetHandleHeight + CommentsSheetHandleBottomPadding) *
            if (controller.sideBySideActive) controller.sheetSlideOffset else 1f
    } else 0.dp
    val bottomRadius by animateDpAsState(
        if (settings.tintHeader && settings.headerEdge == CommentsHeaderEdge.ROUNDED) 24.dp else 0.dp,
        tween(240), label = "header bottom corners",
    )
    val bottomPadding by animateDpAsState(
        if (settings.tintHeader && settings.headerEdge != CommentsHeaderEdge.FADE) 12.dp else 0.dp,
        tween(240), label = "header bottom breathing room",
    )
    val headerShape = RoundedCornerShape(bottomStart = bottomRadius, bottomEnd = bottomRadius)
    val shimmerTopPadding by animateDpAsState(
        24.dp + if (settings.showUpButton) 45.dp - sheetHandleInset else 0.dp,
        contentTween(), label = "header shimmer clearance",
    )
    val shadowElevation by animateDpAsState(
        if (settings.tintHeader && settings.headerEdge == CommentsHeaderEdge.SHADOW) 1.5.dp else 0.dp,
        tween(240), label = "header shadow",
    )
    val edgeHeight by animateDpAsState(
        when {
            !settings.tintHeader -> 8.dp
            settings.headerEdge == CommentsHeaderEdge.FADE -> 32.dp
            else -> 16.dp
        },
        tween(240), label = "header edge spacing",
    )
    val fadeAlpha by animateFloatAsState(
        if (settings.tintHeader && settings.headerEdge == CommentsHeaderEdge.FADE) 1f else 0f,
        tween(240), label = "header fade",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                // Keep one layer through zero elevation; inserting/removing a shadow layer
                // during the corner animation can briefly redraw the old outline.
                .graphicsLayer {
                    this.shadowElevation = shadowElevation.toPx()
                    shape = headerShape
                    clip = true
                }
                .background(visibleHeaderBackground)
                .padding(bottom = bottomPadding),
        ) {
            Spacer(Modifier.height(topSpacer))
            if (controller.integratedWebView && controller.showSheetControls) {
                CommentsSheetControls(
                    sideBySideAvailable = controller.sideBySideAvailable,
                    sideBySideActive = controller.sideBySideActive,
                    onSideBySide = controller::toggleSideBySide,
                    readerModeAvailable = controller.readerModeAvailable,
                    readerModeEnabled = controller.readerModeEnabled,
                    showInvert = settings.showInvert,
                    progress = 1f - headerSheetProgress,
                    contentAlpha = if (controller.sideBySideActive) {
                        0f
                    } else if (controller.predictiveBackActive) {
                        1f - controller.predictiveBackProgress * 0.7f
                    } else {
                        1f
                    },
                    onAction = controller.listener::onSheetAction,
                    onBrowserBack = onBrowserBack,
                    modifier = Modifier.padding(start = sideMarginStart, end = sideMarginEnd)
                        .graphicsLayer { alpha = if (controller.sideBySideActive) 0f else 1f }
                        .then(if (controller.sideBySideActive) Modifier.clearAndSetSemantics { } else Modifier)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = if (controller.sideBySideActive) {
                                (placeable.height * controller.sheetSlideOffset).roundToInt()
                            } else placeable.height
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        },
                )
            }

            Column(
                modifier = Modifier
                    .commentsReadingWidth()
                    .padding(start = sideMarginStart, end = sideMarginEnd),
            ) {
                AnimatedContent(
                    targetState = showHeaderShimmer,
                    modifier = Modifier.graphicsLayer(
                        alpha = if (controller.predictiveBackActive && !controller.sideBySideActive) {
                            controller.predictiveBackProgress * 0.7f
                        } else {
                            1f
                        },
                    ),
                    transitionSpec = {
                        (fadeIn(tween(240, delayMillis = 80)) togetherWith fadeOut(tween(120))).using(
                            SizeTransform(clip = false) { _, _ ->
                                tween(CommentsHeaderRevealDurationMillis, easing = FastOutSlowInEasing)
                            },
                        )
                    },
                    label = "comments story header reveal",
                ) { loadingHeader ->
                    if (loadingHeader) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    enabled = story.isLink,
                                    onClick = controller.listener::onHeaderClick,
                                    onLongClick = null,
                                )
                                .semantics(mergeDescendants = true) {
                                    if (story.isLink) {
                                        contentDescription = "Open article"
                                        role = Role.Button
                                    }
                                },
                        ) {
                            Column(Modifier.padding(top = shimmerTopPadding)) {
                                CommentsHeaderShimmer()
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            StoryHeaderClickArea(
                                enabled = story.isLink,
                                title = storyTitle.text,
                                onClick = controller.listener::onHeaderClick,
                            ) {
                                SharedTransitionLayout {
                                    val boundsTransform = BoundsTransform { _, _ -> headerLayoutTween() }
                                    AnimatedContent(
                                        targetState = HeaderVisualLayout(
                                            settings.headerLayout, headerPreviewImageDisplayed, effectiveMetaInfo,
                                        ),
                                        transitionSpec = {
                                            // Clear outgoing text before the image moves through its space;
                                            // reveal rewrapped text when that movement is nearly complete.
                                            (fadeIn(tween(80, delayMillis = 220)) togetherWith fadeOut(tween(80))).using(
                                                SizeTransform(clip = true) { _, _ -> headerLayoutTween() },
                                            )
                                        },
                                        label = "header layout",
                                    ) { visual ->
                                        val visibility = this
                                        val visualSettings = detailSettings.copy(headerMetaInfo = visual.metaInfo)
                                        val sideImage = visual.layout == CommentsHeaderLayout.BALANCED
                                        val titleLayout = HeaderTitleLayout(
                                            layout = visual.layout,
                                            sideImage = sideImage && visual.imageVisible,
                                            stackedMetrics = visual.metaInfo == CommentsHeaderMetaInfo.ON_IMAGE &&
                                                !visual.imageVisible,
                                        )
                                        val backClearance by animateDpAsState(
                                            if (settings.showUpButton && !(visual.imageVisible && !sideImage)) {
                                                45.dp - sheetHandleInset
                                            } else 0.dp,
                                            contentTween(), label = "header back clearance",
                                        )
                                        val title: @Composable (Modifier) -> Unit = { modifier ->
                                            StoryTitleText(
                                                text = storyTitle.text,
                                                badge = storyTitle.badge,
                                                // Preserve text when its wrapping is unchanged. When the
                                                // image crosses the title, fade through at the two endpoints.
                                                modifier = modifier.sharedElement(
                                                    rememberSharedContentState(titleLayout), visibility,
                                                    boundsTransform = boundsTransform,
                                                ).semantics { heading() },
                                                color = colors.onSurface,
                                                fontFamily = headerTypography.family,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (headerTypography.commentsHeaderTitleSize * if (sideImage) 0.85f else 1f).sp,
                                                style = textStyle,
                                            )
                                        }
                                        val linkInfo: @Composable () -> Unit = {
                                            Box(Modifier.sharedBounds(
                                                rememberSharedContentState("link info" to titleLayout), visibility,
                                                enter = fadeIn(tween(140, delayMillis = 80)),
                                                exit = fadeOut(tween(80)),
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                                boundsTransform = boundsTransform,
                                            )) {
                                                CommentsPreviewPlatformProvider(previewPlatform) {
                                                    HeaderLinkInfo(story, visualSettings, faviconPreview, storyPosterTag)
                                                }
                                            }
                                        }
                                        val preview: @Composable (Modifier) -> Unit = { modifier ->
                                            val imageHazeState = rememberHazeState()
                                            Box(modifier.sharedBounds(
                                                rememberSharedContentState("image bounds"), visibility,
                                                enter = fadeIn(tween(140, delayMillis = 160)),
                                                exit = fadeOut(tween(80)),
                                                boundsTransform = boundsTransform,
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            )) {
                                                headerPreviewImage(
                                                    Modifier.sharedElement(
                                                        rememberSharedContentState("image"), visibility,
                                                        boundsTransform = boundsTransform,
                                                    ).fillMaxSize()
                                                        .clip(RoundedCornerShape(8.dp)).hazeSource(imageHazeState),
                                                    visibleHeaderBackground,
                                                ) { loadedTint = it }
                                                HeaderImageMetrics(
                                                    controller, visualSettings, imageHazeState, textStyle,
                                                    Modifier.align(Alignment.BottomEnd)
                                                        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                                                        .animateEnterExit(
                                                            enter = fadeIn(tween(80, delayMillis = 220)),
                                                            exit = fadeOut(tween(80)),
                                                        ),
                                                )
                                            }
                                        }
                                        val stackedMetrics: @Composable () -> Unit = {
                                            Box(Modifier.animateEnterExit(
                                                enter = fadeIn(tween(140, delayMillis = 160)),
                                                exit = fadeOut(tween(80)),
                                            )) {
                                                HeaderStackedMetrics(
                                                    controller, visualSettings, textStyle, visibleHeaderBackground,
                                                    visual.metaInfo == CommentsHeaderMetaInfo.ON_IMAGE &&
                                                        !visual.imageVisible,
                                                )
                                            }
                                        }
                                        BoxWithConstraints(
                                            Modifier.fillMaxWidth().padding(
                                                top = if (visual.imageVisible && !sideImage) headerTopPadding else 0.dp,
                                            ),
                                        ) {
                                            val thumbnailHeight = if (maxWidth < 360.dp) 88.dp else 104.dp
                                            val thumbnailWidth = if (maxWidth < 360.dp) 112.dp else 128.dp
                                            if (sideImage) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(top = 12.dp + backClearance),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        title(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                                                        linkInfo()
                                                    }
                                                    stackedMetrics()
                                                    if (visual.imageVisible) Box(Modifier.padding(end = 16.dp)) {
                                                        preview(Modifier.width(thumbnailWidth).height(thumbnailHeight))
                                                    }
                                                }
                                            } else {
                                                Column {
                                                    if (visual.imageVisible) {
                                                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                                            preview(Modifier.fillMaxWidth().height(216.dp))
                                                        }
                                                        Spacer(Modifier.height(10.dp))
                                                    }
                                                    Row(
                                                        Modifier.fillMaxWidth().padding(
                                                            top = (if (visual.imageVisible) 0.dp else 16.dp) + backClearance,
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Column(Modifier.weight(1f)) {
                                                            title(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                                                            linkInfo()
                                                        }
                                                        stackedMetrics()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            CommentsPreviewPlatformProvider(previewPlatform) {
                                HeaderStoryBody(
                                    story = story,
                                    settings = settings,
                                    suppressedReferenceUrl = controller.suppressedHeaderReferenceUrl,
                                    onReferenceLongClick = { link, bounds, sourceContentLayer ->
                                        controller.showReferencePreview(
                                            link = link,
                                            sourceBounds = bounds,
                                            headerReference = true,
                                            sourceContainerColor = visibleHeaderBackground,
                                            sourceContentLayer = sourceContentLayer,
                                        )
                                    },
                                    onLinkLongClick = { url, title, bounds ->
                                        controller.showReferencePreview(
                                            url = url,
                                            title = title,
                                            sourceBounds = bounds,
                                            headerReference = true,
                                        )
                                    },
                                )
                                LinkPreviewContent(story, contentVersion, settings, visibleHeaderBackground)
                            }
                            PollOptions(
                                pollOptions,
                                controller.pollVoteInFlightOptionId,
                                controller.listener::onPollOption,
                                typography = headerTypography,
                            )
                            // Keep selectable summary text outside the article click target so a
                            // long press starts text selection instead of opening the WebView.
                            CommentsPreviewPlatformProvider(previewPlatform) {
                                StoryAiSummary(
                                    story = story,
                                    settings = settings,
                                    onOpenLink = previewPlatform.openLink,
                                    diagnostics = controller.summaryDiagnostics,
                                    streaming = controller.storySummaryLoading,
                                    containerColor = summaryContainerColor,
                                    onAsk = controller::openPostAsk,
                                    askVisible = controller.postAsk?.coveringSource == true,
                                )
                            }
                            if (story.isComment) {
                                val actions = buildList {
                                    if (story.parentId > 0) add(Triple("Open parent", Res.drawable.ic_reply, CommentsMoreAction.OPEN_PARENT))
                                    if (story.rootStoryId > 0) add(Triple("Open top level", Res.drawable.ic_arrow_upward, CommentsMoreAction.OPEN_TOP_LEVEL))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    actions.forEachIndexed { index, (label, icon, action) ->
                                        Button(
                                            onClick = { controller.listener.onMoreAction(action) },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(
                                                topStart = if (index == 0) 24.dp else 8.dp,
                                                bottomStart = if (index == 0) 24.dp else 8.dp,
                                                topEnd = if (index == actions.lastIndex) 24.dp else 8.dp,
                                                bottomEnd = if (index == actions.lastIndex) 24.dp else 8.dp,
                                            ),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            ),
                                        ) {
                                            Icon(painterResource(icon), null, Modifier.size(18.dp))
                                            Text(label, Modifier.padding(start = 6.dp), fontFamily = ProductSansFontFamily,
                                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            AnimatedContent(
                                targetState = effectiveMetaInfo,
                                transitionSpec = { headerSettingsTransition() },
                                label = "header details",
                            ) { metaInfo ->
                                Column {
                                    val actionSettings = settings.copy(headerMetaInfo = metaInfo)
                                    CommentsHeaderMetadata(
                                        story = story,
                                        settings = actionSettings,
                                        storyPosterTag = storyPosterTag,
                                        textStyle = textStyle,
                                        onAuthorClick = { controller.listener.onHeaderAction(CommentsHeaderAction.USER) },
                                        onVoteClick = { controller.listener.onHeaderAction(CommentsHeaderAction.VOTE) },
                                        onReplyClick = { controller.listener.onHeaderAction(CommentsHeaderAction.REPLY) },
                                        upvoted = controller.isUpvoted(story.id, story.isComment),
                                        voteLoading = controller.storyVoteLoading,
                                        commented = controller.storyCommented,
                                        headerColor = visibleHeaderBackground,
                                    )
                                }
                            }
                            CommentsHeaderActions(
                                controller = controller,
                                settings = settings.copy(headerMetaInfo = effectiveMetaInfo),
                                contentVersion = contentVersion,
                                bookmarksEnabled = bookmarksEnabled,
                                previewCommentActions = previewCommentActions,
                                onButtonsOverflowChanged = onButtonsOverflowChanged,
                            )
                        }
                    }
                }
            }
        }
        val fadeBrush = remember(visibleHeaderBackground) {
            Brush.verticalGradient(
                0f to visibleHeaderBackground,
                0.25f to visibleHeaderBackground.copy(alpha = visibleHeaderBackground.alpha * 0.84f),
                0.55f to visibleHeaderBackground.copy(alpha = visibleHeaderBackground.alpha * 0.42f),
                1f to visibleHeaderBackground.copy(alpha = 0f),
            )
        }
        Box(Modifier.fillMaxWidth().height(edgeHeight)) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fadeAlpha }.background(fadeBrush))
        }
        Column(Modifier.commentsReadingWidth().padding(start = sideMarginStart, end = sideMarginEnd)) {
            OpFilterBanner(controller)
            CommentsHeaderStatus(controller = controller, lastRefreshedText = lastRefreshedText)
        }
    }
}

private data class HeaderVisualLayout(
    val layout: CommentsHeaderLayout,
    val imageVisible: Boolean,
    val metaInfo: CommentsHeaderMetaInfo,
)

private data class HeaderTitleLayout(
    val layout: CommentsHeaderLayout,
    val sideImage: Boolean,
    val stackedMetrics: Boolean,
)

private fun <T> headerLayoutTween() = tween<T>(220, delayMillis = 80, easing = ContentMotionEasing)

private fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.headerSettingsTransition() =
    (fadeIn(tween(140, delayMillis = 80)) togetherWith fadeOut(tween(80))).using(
        SizeTransform(clip = true) { _, _ -> contentTween() },
    )

/** Expands only the indication. The measured content and its siblings retain their positions. */
@Composable
internal fun StoryHeaderClickArea(
    enabled: Boolean,
    title: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val topInsetPx = with(LocalDensity.current) { 12.dp.roundToPx().toFloat() }
    val indicationSource = remember(interactionSource, topInsetPx) {
        object : InteractionSource {
            override val interactions = flow {
                val presses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()
                interactionSource.interactions.collect { interaction ->
                    emit(when (interaction) {
                        is PressInteraction.Press -> PressInteraction.Press(
                            interaction.pressPosition + Offset(0f, topInsetPx),
                        ).also { presses[interaction] = it }
                        is PressInteraction.Release -> PressInteraction.Release(
                            presses.remove(interaction.press) ?: interaction.press,
                        )
                        is PressInteraction.Cancel -> PressInteraction.Cancel(
                            presses.remove(interaction.press) ?: interaction.press,
                        )
                        else -> interaction
                    })
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = null,
                )
                .semantics(mergeDescendants = true) {
                    if (enabled) {
                        contentDescription = "Open article: $title"
                        role = Role.Button
                    }
                },
        ) { content() }
        if (enabled) {
            Box(
                Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        val top = 12.dp.roundToPx()
                        // Metadata begins with 6dp of padding; stop halfway through that gap.
                        val bottom = 3.dp.roundToPx()
                        val placeable = measurable.measure(constraints.copy(
                            minHeight = constraints.minHeight + top + bottom,
                            maxHeight = constraints.maxHeight + top + bottom,
                        ))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(0, -top)
                        }
                    }
                    .indication(indicationSource, ripple()),
            )
        }
    }
}

@Composable
fun CommentsSheetControls(
    readerModeAvailable: Boolean,
    readerModeEnabled: Boolean,
    sideBySideAvailable: Boolean = false,
    sideBySideActive: Boolean = false,
    onSideBySide: () -> Unit = {},
    showInvert: Boolean,
    progress: Float,
    contentAlpha: Float,
    onAction: (CommentsSheetAction) -> Unit,
    onBrowserBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val collapsedProgress = progress.coerceIn(0f, 1f)
    val navigationBarClearance = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .testTag("comments-sheet-handle")
                .padding(top = CommentsSheetHandleTopPadding, bottom = CommentsSheetHandleBottomPadding)
                .align(Alignment.CenterHorizontally)
                .size(width = 50.dp, height = CommentsSheetHandleHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.onSurfaceVariant.copy(alpha = 0.6f)),
        )
        val actionAlpha = collapsedProgress * collapsedProgress * collapsedProgress
        val actionsVisible = collapsedProgress >= 0.001f && contentAlpha > 0f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CommentsSheetButtonSize * collapsedProgress)
                .then(if (!actionsVisible) Modifier.clearAndSetSemantics { } else Modifier)
                .graphicsLayer(alpha = actionAlpha * contentAlpha)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Alpha and a zero-height row do not remove IconButton touch targets or tooltips.
            // Keep the row's layout animation, but only compose actions while they are visible.
            if (!actionsVisible) return@Row
            val paneControls = LocalBrowserPaneControls.current
            AnimatedSheetButtonSlot(
                visible = paneControls.restoreVisible,
                icon = Res.drawable.ic_arrow_forward,
                description = "Restore two panes",
                onClick = paneControls.restore,
            )
            if (sideBySideAvailable) {
                SheetButtonSlot(
                    Res.drawable.ic_chrome_reader_mode,
                    if (sideBySideActive) "Exit side by side" else "Read side by side",
                    tint = if (sideBySideActive) colors.primary else colors.onSurfaceVariant,
                    onClick = onSideBySide,
                )
            }
            if (onBrowserBack != null) {
                SheetButtonSlot(Res.drawable.ic_arrow_back, "Back") { onBrowserBack() }
            }
            SheetButtonSlot(Res.drawable.ic_refresh, "Refresh website") {
                onAction(CommentsSheetAction.REFRESH)
            }
            if (!sideBySideAvailable) {
                SheetButtonSlot(Res.drawable.ic_arrow_upward, "Show comments") {
                    onAction(CommentsSheetAction.EXPAND)
                }
            }
            SheetButtonSlot(Res.drawable.ic_public, "Open in browser") {
                onAction(CommentsSheetAction.BROWSER)
            }
            AnimatedSheetButtonSlot(
                visible = readerModeAvailable,
                icon = Res.drawable.ic_book_ribbon,
                description = if (readerModeEnabled) "Reader mode on" else "Reader mode",
                tint = if (readerModeEnabled) colors.secondary else colors.onSurfaceVariant,
                onClick = { onAction(CommentsSheetAction.READER) },
            )
            AnimatedSheetButtonSlot(
                visible = showInvert,
                icon = Res.drawable.ic_invert_colors,
                description = "Invert colors",
                onClick = { onAction(CommentsSheetAction.INVERT) },
            )
        }
        // The peek height includes the system navigation inset. Keep header content below that
        // inset while collapsed, then remove the clearance as the comments sheet expands.
        Spacer(Modifier.height(navigationBarClearance * collapsedProgress))
    }
}

@Composable
private fun RowScope.SheetButtonSlot(
    icon: DrawableResource,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        SheetButtonContent(icon, description, tint, onClick)
    }
}

@Composable
private fun SheetButtonContent(
    icon: DrawableResource,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    CommentsTooltip(description) {
        IconButton(onClick = onClick, modifier = Modifier.size(CommentsSheetButtonSize)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun RowScope.AnimatedSheetButtonSlot(
    visible: Boolean,
    icon: DrawableResource,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    val slotWeight by animateFloatAsState(
        targetValue = if (visible) 1f else 0.001f,
        animationSpec = tween(if (visible) 180 else 140),
        label = "browser action slot width",
    )
    Box(Modifier.weight(slotWeight), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.8f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.8f),
        ) {
            SheetButtonContent(icon, description, tint, onClick)
        }
    }
}

@Composable
private fun CommentsHeaderShimmer() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .size(width = 260.dp, height = 31.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHighest),
        )
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(width = 112.dp, height = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceContainerHighest),
        )
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 50.dp, height = 14.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.surfaceContainerHighest),
                )
            }
        }
        Spacer(Modifier.height(42.dp))
    }
}

/** Shared with platform sheet controls so adjoining surfaces use the same color interpolation. */
fun lerpCommentsColor(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction,
)
