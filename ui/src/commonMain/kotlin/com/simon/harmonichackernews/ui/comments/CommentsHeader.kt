package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.presentation.CommentsSheetAction
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.StoryTitleText
import com.simon.harmonichackernews.ui.content.storyTitlePresentation
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Platform-neutral comments header. The preview slot uses shared Coil and Harmonic palette UI;
 * the host supplies only surrounding platform actions such as opening links.
 */
@Composable
fun CommentsHeader(
    controller: CommentsComposeController,
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
    headerPreviewImage: @Composable (visibleBackground: Color, onTintLoaded: (Int) -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    // Keep derived header objects keyed to the immutable story revision supplied by the store.
    val story = remember(controller.story, contentVersion) { controller.story }
    val storyTitle = storyTitlePresentation(
        title = story.title,
        pdfTitle = story.pdfTitle,
        videoTitle = story.videoTitle,
    )
    val pollOptions = remember(story.pollOptionArrayList, contentVersion) {
        story.pollOptionArrayList?.map { option ->
            PollOptionUi(
                id = option.id,
                loaded = option.loaded,
                loadFailed = option.loadFailed,
                text = option.text,
                points = option.points,
            )
        }
    }
    val colors = HarmonicTheme.colors
    val headerTypography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    val showHeaderShimmer = !story.loaded && story.title.isNullOrBlank() && !controller.loadingFailed
    var loadedTint by remember(story.id, contentVersion, initialTint) {
        mutableStateOf(initialTint)
    }
    val normalBackground = colors.background
    val targetBackground = if (settings.tintHeader && !showHeaderShimmer) {
        loadedTint?.let(::Color) ?: Color(tintBaseColor)
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
        controller.sheetSlideOffset,
    )
    val summaryContainerColor = if (settings.tintHeader) {
        lerpCommentsColor(colors.surfaceContainerHigh, visibleHeaderBackground, 0.52f)
    } else {
        colors.surfaceContainerHigh
    }
    LaunchedEffect(visibleHeaderBackground) {
        controller.updateStatusBarHeaderColor(visibleHeaderBackground)
        controller.listener.onHeaderColorChanged(visibleHeaderBackground.toArgb())
    }
    val topSpacer = if (includeStatusBarSpacer) {
        with(density) {
            (WindowInsets.statusBars.getTop(this) * controller.sheetSlideOffset).roundToInt().toDp()
        }
    } else {
        0.dp
    }
    val sideMarginStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val sideMarginEnd = with(density) { controller.contentInsetRightPx.toDp() }
    val backButtonTitleClearance = if (settings.showUpButton && !controller.integratedWebView) {
        16.dp
    } else {
        0.dp
    }
    val titleTopPadding = (if (settings.showUpButton && !headerPreviewImageDisplayed) {
        16.dp
    } else {
        0.dp
    }) + backButtonTitleClearance
    val shimmerTopPadding = titleTopPadding + if (settings.showUpButton) 8.dp else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(normalBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visibleHeaderBackground),
        ) {
            Spacer(Modifier.height(topSpacer))
            if (controller.integratedWebView && controller.showSheetControls) {
                CommentsSheetControls(
                    readerModeAvailable = controller.readerModeAvailable,
                    readerModeEnabled = controller.readerModeEnabled,
                    showInvert = settings.showInvert,
                    progress = 1f - controller.sheetSlideOffset,
                    contentAlpha = if (controller.predictiveBackActive) {
                        1f - controller.predictiveBackProgress * 0.7f
                    } else {
                        1f
                    },
                    onAction = controller.listener::onSheetAction,
                    onBrowserBack = onBrowserBack,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideMarginStart, end = sideMarginEnd)
                    .padding(top = headerTopPadding),
            ) {
                AnimatedContent(
                    targetState = showHeaderShimmer,
                    modifier = Modifier.graphicsLayer(
                        alpha = if (controller.predictiveBackActive) {
                            controller.predictiveBackProgress * 0.7f
                        } else {
                            1f
                        },
                    ),
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 40)) togetherWith fadeOut(tween(180))).using(
                            SizeTransform(clip = false) { _, _ -> tween(260) },
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = story.isLink,
                                        onClick = controller.listener::onHeaderClick,
                                        onLongClick = null,
                                    )
                                    .semantics(mergeDescendants = true) {
                                        if (story.isLink) {
                                            contentDescription = "Open article: " +
                                                storyTitle.text
                                            role = Role.Button
                                        }
                                    },
                            ) {
                                headerPreviewImage(visibleHeaderBackground) { loadedTint = it }
                                StoryTitleText(
                                    text = storyTitle.text,
                                    badge = storyTitle.badge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 16.dp,
                                            top = titleTopPadding,
                                            end = 16.dp,
                                        )
                                        .semantics { heading() },
                                    color = colors.contentPrimary,
                                    fontFamily = headerTypography.family,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = headerTypography.commentsHeaderTitleSize.sp,
                                    style = textStyle,
                                )
                                CommentsPreviewPlatformProvider(previewPlatform) {
                                    HeaderLinkInfo(story = story, settings = settings)
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
                                    LinkPreviewContent(story, contentVersion, settings)
                                }
                                PollOptions(
                                    pollOptions,
                                    controller.pollVoteInFlightOptionId,
                                    controller.listener::onPollOption,
                                    typography = headerTypography,
                                )
                            }
                            // Keep selectable summary text outside the article click target so a
                            // long press starts text selection instead of opening the WebView.
                            CommentsPreviewPlatformProvider(previewPlatform) {
                                StorySummary(
                                    story = story,
                                    settings = settings,
                                    onOpenLink = previewPlatform.openLink,
                                    diagnostics = controller.summaryDiagnostics,
                                    streaming = controller.storySummaryLoading,
                                    containerColor = summaryContainerColor,
                                )
                            }
                            if (story.isComment) {
                                val actions = buildList {
                                    if (story.parentId > 0) add(Triple("Open parent", Res.drawable.ic_reply, CommentsMoreAction.OPEN_PARENT))
                                    if (story.commentMasterId > 0) add(Triple("Open top level", Res.drawable.ic_arrow_upward, CommentsMoreAction.OPEN_TOP_LEVEL))
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
                                                containerColor = HarmonicTheme.colors.secondaryContainer,
                                                contentColor = HarmonicTheme.colors.onSecondaryContainer,
                                            ),
                                        ) {
                                            Icon(painterResource(icon), null, Modifier.size(18.dp))
                                            Text(label, Modifier.padding(start = 6.dp), fontFamily = ProductSansFontFamily,
                                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            HeaderMeta(
                                story = story,
                                settings = settings,
                                storyPosterTag = storyPosterTag,
                                textStyle = textStyle,
                            )
                            HeaderActions(
                                controller = controller,
                                settings = settings,
                                contentVersion = contentVersion,
                                bookmarksEnabled = bookmarksEnabled,
                            )
                        }
                    }
                }
            }
        }
        val fadeBrush = remember(visibleHeaderBackground, normalBackground) {
            Brush.verticalGradient(
                0f to visibleHeaderBackground,
                0.25f to lerpCommentsColor(visibleHeaderBackground, normalBackground, 0.16f),
                0.55f to lerpCommentsColor(visibleHeaderBackground, normalBackground, 0.58f),
                0.88f to normalBackground,
                1f to normalBackground,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(fadeBrush),
        )
        OpFilterBanner(controller)
        HeaderStatus(controller = controller, lastRefreshedText = lastRefreshedText)
    }
}

@Composable
private fun CommentsSheetControls(
    readerModeAvailable: Boolean,
    readerModeEnabled: Boolean,
    showInvert: Boolean,
    progress: Float,
    contentAlpha: Float,
    onAction: (CommentsSheetAction) -> Unit,
    onBrowserBack: (() -> Unit)?,
) {
    val colors = HarmonicTheme.colors
    val collapsedProgress = progress.coerceIn(0f, 1f)
    val navigationBarClearance = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = CommentsSheetHandleTopPadding, bottom = CommentsSheetHandleBottomPadding)
                .align(Alignment.CenterHorizontally)
                .size(width = 50.dp, height = CommentsSheetHandleHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.mutedText.copy(alpha = 0.6f)),
        )
        val actionAlpha = collapsedProgress * collapsedProgress * collapsedProgress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CommentsSheetButtonSize * collapsedProgress)
                .graphicsLayer(alpha = actionAlpha * contentAlpha)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBrowserBack != null) {
                SheetButtonSlot(Res.drawable.ic_arrow_back, "Back") { onBrowserBack() }
            }
            SheetButtonSlot(Res.drawable.ic_refresh, "Refresh website") {
                onAction(CommentsSheetAction.REFRESH)
            }
            SheetButtonSlot(Res.drawable.ic_arrow_upward, "Show comments") {
                onAction(CommentsSheetAction.EXPAND)
            }
            SheetButtonSlot(Res.drawable.ic_public, "Open in browser") {
                onAction(CommentsSheetAction.BROWSER)
            }
            val readerModeSlotWeight by animateFloatAsState(
                targetValue = if (readerModeAvailable) 1f else 0.001f,
                animationSpec = tween(if (readerModeAvailable) 180 else 140),
                label = "reader mode action slot width",
            )
            Box(
                modifier = Modifier.weight(readerModeSlotWeight),
                contentAlignment = Alignment.Center,
            ) {
                ReaderModeSheetButton(
                    visible = readerModeAvailable,
                    enabled = readerModeEnabled,
                    tint = if (readerModeEnabled) MaterialTheme.colorScheme.secondary else colors.iconTint,
                    onClick = { onAction(CommentsSheetAction.READER) },
                )
            }
            if (showInvert) {
                SheetButtonSlot(Res.drawable.ic_invert_colors, "Invert colors") {
                    onAction(CommentsSheetAction.INVERT)
                }
            }
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
    tint: Color = HarmonicTheme.colors.iconTint,
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
private fun ReaderModeSheetButton(
    visible: Boolean,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.8f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.8f),
    ) {
        SheetButtonContent(
            Res.drawable.ic_chrome_reader_mode,
            if (enabled) "Reader mode on" else "Reader mode",
            tint,
            onClick,
        )
    }
}

@Composable
private fun CommentsHeaderShimmer() {
    val colors = HarmonicTheme.colors
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

private fun lerpCommentsColor(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction,
)
