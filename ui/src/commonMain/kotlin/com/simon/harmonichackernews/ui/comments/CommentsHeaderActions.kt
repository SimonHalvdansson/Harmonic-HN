@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments

import kotlin.time.Duration.Companion.milliseconds

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import com.simon.harmonichackernews.presentation.CommentsShareAction
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.common.AnimatedBookmarkIcon
import com.simon.harmonichackernews.utils.AgePolicy
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentsHeaderActions(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
    bookmarksEnabled: Boolean,

) {
    val story = controller.story
    val hasAccount = settings.hasAccountDetails
    val canReply = hasAccount && !AgePolicy.isOlderThanTwoWeeks(story.createdAtEpochSeconds)
    var shareExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var archiveExpanded by remember { mutableStateOf(false) }
    val dismissMenus = {
        shareExpanded = false
        moreExpanded = false
    }
    LaunchedEffect(moreExpanded) {
        if (!moreExpanded) {
            // Keep the submenu composed through DropdownMenu's exit animation. Resetting it in the
            // same frame briefly reveals the parent menu behind the fading popup.
            delay(500.milliseconds)
            sortExpanded = false
            archiveExpanded = false
        }
    }
    val menuVisible = shareExpanded || moreExpanded
    // The desktop window receives an unconsumed Escape after Compose dismisses a dropdown. Keep
    // the host-facing flag synchronized after composition so that same key cannot also navigate
    // away from the story.
    LaunchedEffect(menuVisible) {
        controller.updateHeaderMenuVisibility(menuVisible)
    }
    LaunchedEffect(controller.headerMenuDismissRequestVersion) {
        if (controller.headerMenuDismissRequestVersion > 0) dismissMenus()
    }
    DisposableEffect(controller) {
        onDispose { controller.updateHeaderMenuVisibility(false) }
    }
    val upvoted = controller.isUpvoted(story.id, story.isComment)
    val favorited = controller.isFavorited(story.id)
    val bookmarked = remember(contentVersion, story.id) {
        controller.isBookmarked(story.id)
    }
    val actions = buildList {
        add(HeaderAction(Res.drawable.ic_account_circle, "User", CommentsHeaderAction.USER))
        if (canReply) add(HeaderAction(Res.drawable.ic_comment, if (story.isComment) "Reply to comment" else "Reply to post", CommentsHeaderAction.REPLY))
        if (hasAccount) add(HeaderAction(if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up, if (upvoted) "Remove vote" else "Upvote", CommentsHeaderAction.VOTE, controller.storyVoteLoading))
        if (hasAccount) add(HeaderAction(if (favorited) Res.drawable.ic_star_filled else Res.drawable.ic_star, if (favorited) "Remove favorite" else "Favorite", CommentsHeaderAction.FAVORITE, controller.storyFavoriteLoading))
        if (bookmarksEnabled && !hasAccount) add(HeaderAction(if (bookmarked) Res.drawable.ic_bookmark_filled else Res.drawable.ic_bookmark, if (bookmarked) "Remove bookmark" else "Bookmark", CommentsHeaderAction.BOOKMARK))
        if (story.isLink && settings.canProvideSummary && !story.summaryGeneratedSuccessfully) add(HeaderAction(Res.drawable.ic_auto_awesome, "Summarize", CommentsHeaderAction.SUMMARIZE, controller.storySummaryLoading))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val actionCount = actions.size + if (hasAccount) 2 else 3
        val actionHorizontalPadding = commentActionPadding(maxWidth.value, actionCount).dp
        val actionButtonModifier = Modifier
            .width(commentActionButtonWidth(maxWidth.value, actionCount).dp)
            .height(CommentsHeaderActionButtonSize)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = actionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.Center,
        ) {
            actions.forEach { action ->
                HeaderActionButton(action, actionButtonModifier, story.id) {
                    controller.listener.onHeaderAction(action.action)
                }
            }
            Box(
                actionButtonModifier,
                contentAlignment = Alignment.Center,
            ) {
                CommentsTooltip("Share") {
                    IconButton(
                        onClick = { shareExpanded = true },
                        modifier = actionButtonModifier,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_share),
                            contentDescription = "Share",
                            modifier = Modifier.size(24.dp),
                            tint = HarmonicTheme.colors.iconTint,
                        )
                    }
                }
                ShareMenu(
                    expanded = shareExpanded,
                    isLink = story.isLink,
                    onDismiss = { shareExpanded = false },
                    onAction = controller.listener::onShareAction,
                )
            }
            if (!hasAccount) {
                CommentsTooltip("Refresh") {
                    IconButton(
                        onClick = {
                            controller.beginHeaderRefresh(showProgressInButton = true)
                            controller.listener.onHeaderAction(CommentsHeaderAction.REFRESH)
                        },
                        modifier = actionButtonModifier,
                    ) {
                        AnimatedContent(
                            targetState = controller.refreshButtonInProgress,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                            },
                            label = "Refresh loading transition",
                        ) { refreshing ->
                            if (refreshing) {
                                HarmonicLoadingIndicator(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .semantics { contentDescription = "Refresh" },
                                )
                            } else {
                                Icon(
                                    painterResource(Res.drawable.ic_refresh),
                                    contentDescription = "Refresh",
                                    modifier = Modifier.size(24.dp),
                                    tint = HarmonicTheme.colors.iconTint,
                                )
                            }
                        }
                    }
                }
            }
            Box(
                actionButtonModifier,
                contentAlignment = Alignment.Center,
            ) {
                CommentsTooltip("More options") {
                    IconButton(
                        onClick = {
                            sortExpanded = false
                            archiveExpanded = false
                            moreExpanded = true
                        },
                        modifier = actionButtonModifier,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_more_vert),
                            contentDescription = "More options",
                            modifier = Modifier.size(24.dp),
                            tint = HarmonicTheme.colors.iconTint,
                        )
                    }
                }
                MoreMenu(
                    expanded = moreExpanded,
                    sortExpanded = sortExpanded,
                    archiveExpanded = archiveExpanded,
                    controller = controller,
                    settings = settings,
                    bookmarksEnabled = bookmarksEnabled,
                    contentVersion = contentVersion,
                    onDismiss = dismissMenus,
                    onSortExpanded = { sortExpanded = true },
                    onArchiveExpanded = { archiveExpanded = true },
                    onSubmenuBack = {
                        sortExpanded = false
                        archiveExpanded = false
                    },
                )
            }
        }
    }
}

private val CommentsHeaderActionButtonSize = 54.dp

private data class HeaderAction(
    val icon: DrawableResource,
    val label: String,
    val action: CommentsHeaderAction,
    val loading: Boolean = false,
)

private data class HeaderActionVisual(
    val icon: DrawableResource,
    val label: String,
    val loading: Boolean,
)

@Composable
private fun HeaderActionButton(
    action: HeaderAction,
    modifier: Modifier,
    itemId: Int,
    onClick: () -> Unit,
) {
    CommentsTooltip(action.label) {
        IconButton(
            onClick = onClick,
            enabled = !action.loading,
            modifier = modifier,
        ) {
            if (action.action == CommentsHeaderAction.BOOKMARK) {
                AnimatedBookmarkIcon(
                    bookmarked = action.icon == Res.drawable.ic_bookmark_filled,
                    itemId = itemId,
                )
                return@IconButton
            }
            AnimatedContent(
                targetState = HeaderActionVisual(action.icon, action.label, action.loading),
                transitionSpec = {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                },
                label = "${action.label} loading transition",
            ) { visual ->
                if (visual.loading) {
                    HarmonicLoadingIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = visual.label },
                    )
                } else {
                    Icon(
                        painterResource(visual.icon),
                        contentDescription = visual.label,
                        modifier = Modifier.size(24.dp),
                        tint = HarmonicTheme.colors.iconTint,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsTooltip(
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
private fun ShareMenu(
    expanded: Boolean,
    isLink: Boolean,
    onDismiss: () -> Unit,
    onAction: (CommentsShareAction) -> Unit,
) {
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss) {
        @Composable fun action(label: String, id: CommentsShareAction) {
            DropdownMenuItem(
                text = { CommentsMenuText(label) },
                onClick = {
                    onDismiss()
                    onAction(id)
                },
            )
        }
        if (isLink) {
            action("Article link", CommentsShareAction.ARTICLE)
            action("Article link and title", CommentsShareAction.ARTICLE_WITH_TITLE)
        }
        action("HN link", CommentsShareAction.HN)
        action("HN link and title", CommentsShareAction.HN_WITH_TITLE)
        if (isLink) action("Article + HN link and title", CommentsShareAction.ARTICLE_AND_HN)
    }
}

@Composable
private fun MoreMenu(
    expanded: Boolean,
    sortExpanded: Boolean,
    archiveExpanded: Boolean,
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    bookmarksEnabled: Boolean,
    contentVersion: Int,
    onDismiss: () -> Unit,
    onSortExpanded: () -> Unit,
    onArchiveExpanded: () -> Unit,
    onSubmenuBack: () -> Unit,
) {
    val story = controller.story
    val commentsCount = controller.comments.size
    val bookmarked = remember(contentVersion, story.id) {
        controller.isBookmarked(story.id)
    }
    val page = when {
        sortExpanded -> MoreMenuPage.Sort
        archiveExpanded -> MoreMenuPage.Archive
        else -> MoreMenuPage.Root
    }
    HarmonicDropdownMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        // Parent and submenu labels used to change the popup width mid-animation, shifting both
        // popup edges diagonally. A stable width keeps the anchor and transform origin fixed.
        modifier = Modifier.width(248.dp),
    ) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val forward = initialState == MoreMenuPage.Root && targetState != MoreMenuPage.Root
                val direction = if (forward) 1 else -1
                val duration = if (forward) 220 else 190
                val fadeThroughDelay = if (forward) 80 else 70
                val enter = slideInHorizontally(
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                    initialOffsetX = { width -> direction * (width / 7).coerceAtLeast(1) },
                ) + fadeIn(
                    tween(
                        durationMillis = duration - fadeThroughDelay,
                        delayMillis = fadeThroughDelay,
                    ),
                )
                val exit = slideOutHorizontally(
                    animationSpec = tween(duration - 40, easing = FastOutSlowInEasing),
                    targetOffsetX = { width -> -direction * (width / 7).coerceAtLeast(1) },
                ) + fadeOut(tween(fadeThroughDelay))
                (enter togetherWith exit).using(
                    SizeTransform(clip = true) { _, _ ->
                        tween(duration, easing = FastOutSlowInEasing)
                    },
                )
            },
            label = "comments more submenu",
        ) { visiblePage ->
            Column(Modifier.fillMaxWidth()) {
                when (visiblePage) {
                    MoreMenuPage.Sort -> {
                        SubmenuHeader("Sort comments", onSubmenuBack)
                        Spacer(Modifier.height(8.dp))
                        val options = stringArrayResource(Res.array.comment_sorting)
                        options.forEach { option ->
                            val isSelected = option == controller.currentSorting
                            val selectionColor by animateColorAsState(
                                if (isSelected) HarmonicTheme.colors.accent.copy(alpha = 0.08f)
                                else HarmonicTheme.colors.accent.copy(alpha = 0f),
                                animationSpec = tween(180),
                                label = "comment sort selection",
                            )
                            DropdownMenuItem(
                                modifier = Modifier.padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(selectionColor)
                                    .semantics { selected = isSelected },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AnimatedVisibility(
                                            visible = isSelected,
                                            enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                                            exit = fadeOut(tween(180)) + shrinkHorizontally(tween(180)),
                                        ) {
                                            Icon(
                                                painterResource(Res.drawable.ic_check),
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                                                tint = HarmonicTheme.colors.iconTint,
                                            )
                                        }
                                        CommentsMenuText(option)
                                    }
                                },
                                onClick = {
                                    onDismiss()
                                    controller.listener.onSortComments(option)
                                },
                            )
                        }
                    }

                    MoreMenuPage.Archive -> {
                        SubmenuHeader("View on archive", onSubmenuBack)
                        @Composable fun archive(label: String, action: CommentsMoreAction) {
                            DropdownMenuItem(
                                text = { CommentsMenuText(label) },
                                onClick = {
                                    onDismiss()
                                    controller.listener.onMoreAction(action)
                                },
                            )
                        }
                        archive("archive.org", CommentsMoreAction.ARCHIVE_ORG)
                        archive("archive.is", CommentsMoreAction.ARCHIVE_IS)
                        archive("archive.today", CommentsMoreAction.ARCHIVE_TODAY)
                        archive("archive.ph", CommentsMoreAction.ARCHIVE_PH)
                    }

                    MoreMenuPage.Root -> {
                        @Composable fun action(
                            label: String,
                            icon: DrawableResource,
                            id: CommentsMoreAction,
                        ) {
                            DropdownMenuItem(
                                text = { CommentsMenuText(label) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(icon),
                                        contentDescription = null,
                                        tint = HarmonicTheme.colors.iconTint,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    if (id == CommentsMoreAction.REFRESH) {
                                        controller.beginHeaderRefresh()
                                    }
                                    controller.listener.onMoreAction(id)
                                },
                            )
                        }
                        if (settings.hasAccountDetails) {
                            action("Refresh", Res.drawable.ic_refresh, CommentsMoreAction.REFRESH)
                        }
                        if (story.isComment && story.parentId > 0) {
                            action("Open parent", Res.drawable.ic_reply, CommentsMoreAction.OPEN_PARENT)
                        }
                        if (story.isComment && story.rootStoryId > 0) {
                            action("Open top level", Res.drawable.ic_arrow_upward, CommentsMoreAction.OPEN_TOP_LEVEL)
                        }
                        if (settings.hasAccountDetails && bookmarksEnabled) {
                            DropdownMenuItem(
                                text = {
                                    CommentsMenuText(if (bookmarked) "Remove bookmark" else "Bookmark")
                                },
                                leadingIcon = {
                                    AnimatedBookmarkIcon(bookmarked, story.id, description = null)
                                },
                                // This local toggle stays visible to show the saved state and its motion.
                                onClick = {
                                    controller.listener.onMoreAction(CommentsMoreAction.TOGGLE_BOOKMARK)
                                },
                            )
                        }
                        AnimatedCommentMenuItem(visible = commentsCount > 1) {
                            action("Search comments", Res.drawable.ic_search, CommentsMoreAction.SEARCH)
                        }
                        AnimatedCommentMenuItem(visible = commentsCount > 2) {
                            SubmenuEntry("Sort comments", Res.drawable.ic_filter_list, onSortExpanded)
                        }
                        AnimatedCommentMenuItem(
                            visible = !controller.opThreadFilterEnabled && controller.hasCommentsByOp,
                        ) {
                            action("Comments by OP", Res.drawable.ic_person, CommentsMoreAction.COMMENTS_BY_OP)
                        }
                        action("Open in browser", Res.drawable.ic_open_in_browser, CommentsMoreAction.OPEN_BROWSER)
                        if (controller.adBlockActive) {
                            action("Disable AdBlock", Res.drawable.ic_block, CommentsMoreAction.DISABLE_AD_BLOCK)
                        }
                        if (story.isLink) {
                            SubmenuEntry("View on archive", Res.drawable.ic_history, onArchiveExpanded)
                        }
                    }
                }
            }
        }
    }
}

private enum class MoreMenuPage { Root, Sort, Archive }

@Composable
private fun AnimatedCommentMenuItem(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = expandVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(150, delayMillis = 50)),
        exit = shrinkVertically(
            animationSpec = tween(190, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(tween(100)),
        label = "comment menu item",
    ) {
        content()
    }
}

@Composable
private fun SubmenuHeader(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { CommentsMenuText(title) },
        leadingIcon = {
            Icon(
                painterResource(Res.drawable.ic_arrow_back),
                contentDescription = null,
                tint = HarmonicTheme.colors.iconTint,
            )
        },
        onClick = onClick,
    )
    HorizontalDivider(color = HarmonicTheme.colors.commentDivider)
}

@Composable
private fun SubmenuEntry(title: String, icon: DrawableResource, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { CommentsMenuText(title) },
        leadingIcon = {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = HarmonicTheme.colors.iconTint,
            )
        },
        trailingIcon = {
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = HarmonicTheme.colors.iconTint,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun CommentsMenuText(text: String) {
    HarmonicMenuText(text)
}

/** Reduce only the width when the normal targets would force another row. */
internal fun commentActionButtonWidth(width: Float, actionCount: Int): Float =
    if (width >= actionCount * CommentsHeaderActionButtonSize.value) {
        CommentsHeaderActionButtonSize.value
    } else {
        48f
    }

/** Cap SpaceEvenly gaps at 24dp, centering the actions once they reach that spacing. */
internal fun commentActionPadding(width: Float, actionCount: Int): Float {
    val maxRowWidth = actionCount * CommentsHeaderActionButtonSize.value + (actionCount + 1) * 24f
    return ((width - maxRowWidth) / 2f).coerceAtLeast(0f)
}
