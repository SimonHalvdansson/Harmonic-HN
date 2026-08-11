package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Platform-neutral comments header. Image loading and palette extraction remain a slot supplied by
 * the platform shell; all visible header state, controls, and transitions live here.
 */
@Composable
fun SharedCommentsHeader(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
    storyPosterTag: String,
    tintBaseColor: Int,
    initialTint: Int?,
    headerTopPadding: Dp,
    actionHorizontalPadding: Dp,
    bookmarksEnabled: Boolean,
    lastRefreshedText: String?,
    textStyle: TextStyle,
    previewPlatform: CommentsPreviewPlatform,
    headerPreviewImage: @Composable (visibleBackground: Color, onTintLoaded: (Int) -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    // Story is a mutable model. Include the bridge revision whenever taking derived snapshots.
    val story = remember(controller.story, contentVersion) { controller.story }
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
    LaunchedEffect(visibleHeaderBackground) {
        controller.updateStatusBarHeaderColor(visibleHeaderBackground)
        controller.listener.onHeaderColorChanged(visibleHeaderBackground.toArgb())
    }
    val topSpacer = with(density) {
        (WindowInsets.statusBars.getTop(this) * controller.sheetSlideOffset).roundToInt().toDp()
    }
    val sideMarginStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val sideMarginEnd = with(density) { controller.contentInsetRightPx.toDp() }

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
            if (controller.integratedWebView) {
                SharedCommentsSheetControls(
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
                                ),
                        ) {
                            SharedCommentsHeaderShimmer()
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
                                    ),
                            ) {
                                headerPreviewImage(visibleHeaderBackground) { loadedTint = it }
                                Text(
                                    text = story.pdfTitle ?: story.videoTitle ?: story.title.orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .semantics { heading() },
                                    color = colors.storyNormal,
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
                                        onReferenceLongClick = { link, bounds ->
                                            controller.showReferencePreview(
                                                link = link,
                                                sourceBounds = bounds,
                                                headerReference = true,
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
                                PollOptions(pollOptions, controller.listener::onPollOption)
                                StorySummary(story, settings)
                                HeaderMeta(
                                    story = story,
                                    settings = settings,
                                    storyPosterTag = storyPosterTag,
                                    textStyle = textStyle,
                                )
                            }
                            HeaderActions(
                                controller = controller,
                                settings = settings,
                                contentVersion = contentVersion,
                                bookmarksEnabled = bookmarksEnabled,
                                actionHorizontalPadding = actionHorizontalPadding,
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
private fun SharedCommentsSheetControls(
    readerModeAvailable: Boolean,
    readerModeEnabled: Boolean,
    showInvert: Boolean,
    progress: Float,
    contentAlpha: Float,
    onAction: (Int) -> Unit,
) {
    val colors = HarmonicTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 50.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.storyDisabled.copy(alpha = 0.6f)),
        )
        val actionAlpha = progress.coerceIn(0f, 1f).let { it * it * it }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height((56f * progress).dp)
                .graphicsLayer(alpha = actionAlpha * contentAlpha)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetButtonSlot(Res.drawable.ic_refresh, "Refresh website") {
                onAction(CommentsComposeController.SHEET_REFRESH)
            }
            SheetButtonSlot(Res.drawable.ic_arrow_upward, "Show comments") {
                onAction(CommentsComposeController.SHEET_EXPAND)
            }
            SheetButtonSlot(Res.drawable.ic_public, "Open in browser") {
                onAction(CommentsComposeController.SHEET_BROWSER)
            }
            if (readerModeAvailable) {
                SheetButtonSlot(
                    Res.drawable.ic_chrome_reader_mode,
                    if (readerModeEnabled) "Reader mode on" else "Reader mode",
                    tint = if (readerModeEnabled) MaterialTheme.colorScheme.secondary else colors.drawable,
                ) {
                    onAction(CommentsComposeController.SHEET_READER)
                }
            }
            if (showInvert) {
                SheetButtonSlot(Res.drawable.ic_invert_colors, "Invert colors") {
                    onAction(CommentsComposeController.SHEET_INVERT)
                }
            }
        }
    }
}

@Composable
private fun RowScope.SheetButtonSlot(
    icon: DrawableResource,
    description: String,
    tint: Color = HarmonicTheme.colors.drawable,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        CommentsTooltip(description) {
            IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = description,
                    modifier = Modifier.size(24.dp),
                    tint = tint,
                )
            }
        }
    }
}

@Composable
private fun SharedCommentsHeaderShimmer() {
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
