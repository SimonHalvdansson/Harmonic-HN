package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.ui.common.relativeTimeOnResume
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.layoutHeight
import com.simon.harmonichackernews.ui.common.layoutVerticalPadding
import com.simon.harmonichackernews.ui.content.StoryTitleText
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.storyTitlePresentation
import com.simon.harmonichackernews.ui.theme.pageBackground

internal val LocalCommentsPeekHeader = staticCompositionLocalOf<CommentsPeekHeaderState?> { null }

/** Geometry is relative to the lazy header, so it remains useful when that item is recycled. */
@Stable
internal class CommentsPeekHeaderState(
    private val list: LazyListState,
    private val topInsetPx: Int,
    private val directionThresholdPx: Float,
) : NestedScrollConnection {
    var headerCoordinates: LayoutCoordinates? = null
    var titleBounds by mutableStateOf<Rect?>(null)
    private var surfaceBottom by mutableStateOf<Float?>(null)
    var requested by mutableStateOf(false)
        private set
    val revealAnimation = Animatable(0f)
    val reveal: Float get() = revealAnimation.value
    // Composition observes only these thresholds. Pixel offsets and animation frames
    // are read by graphics layers, not by the text/composable subtree.
    val visible by computedStateOf { reveal > 0f && handoff < 1f }
    val interactive by computedStateOf { reveal > 0.5f && handoff < 0.8f }
    private var directionTravel = 0f

    private val header get() = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }

    val handoff: Float
        get() {
            val item = header ?: return 0f
            val title = titleBounds ?: return 1f
            return commentsPeekHandoff(item.offset.toFloat(), title, topInsetPx.toFloat())
        }

    val coverage: Float get() = reveal.let { if (it == 0f) 0f else it * (1f - handoff) }

    fun isShadowExposed(peekBottom: Float): Boolean {
        val item = header ?: return true
        val bottom = surfaceBottom ?: return true
        // Once the surfaces meet, only the scrolling header's outer edge casts a shadow.
        return item.offset + bottom < peekBottom
    }

    fun measureSurface(coordinates: LayoutCoordinates) {
        val parent = headerCoordinates?.takeIf { it.isAttached } ?: return
        surfaceBottom = parent.localPositionOf(coordinates, Offset.Zero).y + coordinates.size.height
    }

    suspend fun dismissForNavigation(toHeader: Boolean) {
        requested = false
        directionTravel = 0f
        // Comment jumps must expose their target immediately, including non-animated
        // restores. Top-of-story jumps can retain the normal fade into the real header.
        if (!toHeader) revealAnimation.snapTo(0f)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Only actual user scrolling can reveal the peek. Restoring a reading position,
        // search results, row resizing and navigation animations must not summon it.
        if (source != NestedScrollSource.UserInput || consumed.y == 0f) return Offset.Zero
        if (directionTravel * consumed.y < 0f) directionTravel = 0f
        directionTravel += consumed.y
        if (directionTravel <= -directionThresholdPx) requested = false
        if (directionTravel >= directionThresholdPx) {
            val item = header
            if (item == null || item.offset + item.size <= topInsetPx) requested = true
            if (handoff >= 1f) requested = false
        }
        return Offset.Zero
    }

    fun measureTitle(coordinates: LayoutCoordinates) {
        val parent = headerCoordinates?.takeIf { it.isAttached } ?: return
        val topLeft = parent.localPositionOf(coordinates, Offset.Zero)
        titleBounds = Rect(topLeft, androidx.compose.ui.geometry.Size(
            coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
        ))
    }
}

/** Crossfade only as the actual title returns, including headers with long bodies or images. */
internal fun commentsPeekHandoff(headerOffset: Float, title: Rect, topInset: Float): Float =
    ((headerOffset + title.bottom - topInset) / title.height.coerceAtLeast(1f)).coerceIn(0f, 1f)

internal fun Modifier.measureCommentsPeekTitle(state: CommentsPeekHeaderState?): Modifier =
    if (state == null) this else peekGeometryAnchor(state::measureTitle).graphicsLayer {
        // Keep the real title in its natural scrolling position. Translating/scaling
        // independently laid-out titles creates diagonal travel and clipped glyphs.
        val titleReveal = ((state.handoff - 0.4f) / 0.6f).coerceIn(0f, 1f)
        alpha = 1f - state.reveal * (1f - titleReveal)
    }

internal fun Modifier.measureCommentsPeekSurface(state: CommentsPeekHeaderState?): Modifier =
    if (state == null) this else peekGeometryAnchor(state::measureSurface)

/** Measure the untransformed layout, with the animated layer inside this coordinator. */
private fun Modifier.peekGeometryAnchor(onPlaced: (LayoutCoordinates) -> Unit): Modifier =
    onPlaced(onPlaced).layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        layout(child.width, child.height) { child.place(0, 0) }
    }

@Composable
internal fun CommentsPeekHeader(
    state: CommentsPeekHeaderState,
    controller: CommentsScreenController,
    topInset: Dp,
    reserveUpButtonInset: Boolean,
    onClick: () -> Unit,
) {
    val settings = controller.displaySettings ?: return
    val enabled = settings.peekStoryHeader &&
        (controller.isSheetExpanded() || controller.sideBySideActive) &&
        !controller.initialScrollRestorationPending && !controller.predictiveBackActive
    LaunchedEffect(state, enabled, state.requested) {
        state.revealAnimation.animateTo(if (enabled && state.requested) 1f else 0f, tween(180))
    }
    if (!state.visible) return

    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val typography = rememberContentTypography(
        preferredFont = settings.font, commentTextSize = settings.preferredTextSize,
    )
    val title = storyTitlePresentation(controller.story.title, controller.story.pdfTitle, controller.story.videoTitle)
    if (title.text.isBlank()) return
    val edgeStyle = rememberCommentsHeaderEdgeStyle(settings.tintHeader, settings.headerEdge)
    val background = { controller.statusBarHeaderColor ?: colors.pageBackground }
    Column(
        Modifier.fillMaxWidth()
            .testTag("comments-peek-header")
            .graphicsLayer {
                val reveal = state.reveal
                alpha = reveal * (1f - state.handoff)
                translationY = -(1f - reveal) * size.height
            },
    ) {
        Box(
            Modifier.fillMaxWidth()
                .commentsHeaderSurface(edgeStyle, background) { height ->
                    val translation = -(1f - state.reveal) *
                        (height + with(density) { edgeStyle.edgeHeight.toPx() })
                    state.isShadowExposed(height + translation)
                }
                .padding(top = topInset)
                .clickable(
                    enabled = enabled && state.interactive,
                    role = Role.Button,
                    onClickLabel = "Scroll to top",
                    onClick = onClick,
                )
                .layoutVerticalPadding(bottom = { edgeStyle.bottomPadding }),
        ) {
            Box(
                Modifier.commentsReadingWidth()
                    .fillMaxWidth()
                    .padding(
                        start = with(density) { controller.contentInsetLeftPx.toDp() },
                        end = with(density) { controller.contentInsetRightPx.toDp() },
                    )
                    .heightIn(min = 56.dp)
                    // The host button spans 16..64dp; leave another 16dp before text.
                    .padding(start = if (reserveUpButtonInset) 80.dp else 16.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val story = controller.story
                val domain = remember(story.url) { story.getDisplayDomain(true)?.takeIf { it.isNotBlank() } }
                val favicon = remember(story.url, settings.faviconProvider) {
                    domain?.let { FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider) }
                }
                Row(
                    modifier = Modifier.padding(vertical = 4.dp).graphicsLayer {
                        alpha = (1f - state.handoff / 0.4f).coerceIn(0f, 1f)
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        StoryTitleText(
                            text = title.text,
                            badge = title.badge,
                            color = colors.onSurface,
                            fontFamily = typography.family,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val age = relativeTimeOnResume(story.createdAtEpochSeconds)
                        val metadataStyle = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = typography.family,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (domain != null) {
                                if (settings.showFavicons) {
                                    val fallback = painterResource(Res.drawable.ic_public)
                                    var faviconLoaded by remember(favicon) { mutableStateOf(false) }
                                    AsyncImage(
                                        model = favicon,
                                        contentDescription = null,
                                        placeholder = fallback,
                                        fallback = fallback,
                                        error = fallback,
                                        colorFilter = if (faviconLoaded) null else ColorFilter.tint(colors.onSurfaceVariant),
                                        onLoading = { faviconLoaded = false },
                                        onSuccess = { faviconLoaded = true },
                                        onError = { faviconLoaded = false },
                                        modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)),
                                    )
                                }
                                Text(
                                    domain,
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = metadataStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("\u00b7", style = metadataStyle)
                            }
                            Text(age, style = metadataStyle, maxLines = 1)
                        }
                    }
                    if (!story.isJob) HeaderPeekMetrics(controller, settings, background())
                }
            }
        }
        // Include the shadow's transparent clearance inside the fading parent layer.
        Box(Modifier.fillMaxWidth().layoutHeight { edgeStyle.edgeHeight }
            .commentsHeaderFade(edgeStyle, background))
    }
}
