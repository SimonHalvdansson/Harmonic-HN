@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.ui.content.ContentTypography
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AgePolicy
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun PollOptions(options: List<PollOptionUi>?, onVote: (Int) -> Unit) {
    if (options == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            if (option.loaded) {
                OutlinedButton(
                    onClick = { onVote(option.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${option.text} (${option.points} ${if (option.points == 1) "point" else "points"})")
                }
            } else if (option.loadFailed) {
                Text(
                    "Unable to load this option",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    color = HarmonicTheme.colors.textSecondary,
                )
            } else {
                HarmonicLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(42.dp),
                )
            }
        }
    }
}

data class PollOptionUi(
    val id: Int,
    val loaded: Boolean,
    val loadFailed: Boolean,
    val text: String?,
    val points: Int,
)

@Composable
fun StorySummary(
    story: Story,
    settings: CommentDisplaySettings,
) {
    AnimatedVisibility(
        visible = !story.summary.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.ic_auto_awesome),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(14.dp),
                )
                Text(
                    "Summary",
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = HarmonicTheme.colors.storyNormal,
                )
            }
            Text(
                story.summary.orEmpty(),
                modifier = Modifier.padding(top = 4.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = rememberContentTypography(settings.font).family,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun HeaderMeta(
    story: Story,
    settings: CommentDisplaySettings,
    storyPosterTag: String = "",
    textStyle: TextStyle,
) {
    if (!story.loaded) return
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(settings.font)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!story.isComment) {
                HeaderMetaItem(Res.drawable.ic_thumb_up, story.score.toString(), typography, textStyle)
            }
            HeaderMetaItem(Res.drawable.ic_comment, story.descendants.toString(), typography, textStyle)
            HeaderMetaItem(Res.drawable.ic_schedule, story.timeFormatted, typography, textStyle)
            val posterLabel = buildString {
                append(story.by.orEmpty())
                if (storyPosterTag.isNotBlank()) {
                    append(" (").append(storyPosterTag).append(')')
                }
            }
            HeaderMetaItem(Res.drawable.ic_account_circle, posterLabel, typography, textStyle)
        }
        Spacer(Modifier.weight(1f))
        if (story.isLink) {
            Icon(
                painterResource(Res.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = HarmonicTheme.colors.drawable,
            )
        }
    }
}

@Composable
private fun HeaderMetaItem(
    icon: DrawableResource,
    label: String,
    typography: ContentTypography,
    textStyle: TextStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            label,
            modifier = Modifier.padding(start = 3.dp),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = typography.family,
            fontSize = typography.commentsHeaderMetaSize.sp,
            style = textStyle,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderActions(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
    bookmarksEnabled: Boolean,
    actionHorizontalPadding: Dp,
) {
    val story = controller.story
    val hasAccount = settings.hasAccountDetails
    val canReply = hasAccount && !AgePolicy.isOlderThanTwoWeeks(story.time)
    var shareExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var archiveExpanded by remember { mutableStateOf(false) }
    val upvoted = controller.isUpvoted(story.id, story.isComment)
    val favorited = controller.isFavorited(story.id)
    val bookmarked = remember(contentVersion, story.id) {
        controller.isBookmarked(story.id)
    }
    val actions = buildList {
        add(HeaderAction(Res.drawable.ic_account_circle, "User", CommentsComposeController.HEADER_ACTION_USER))
        if (canReply) add(HeaderAction(Res.drawable.ic_comment, if (story.isComment) "Reply to comment" else "Reply to post", CommentsComposeController.HEADER_ACTION_REPLY))
        if (hasAccount) add(HeaderAction(if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up, if (upvoted) "Remove vote" else "Vote", CommentsComposeController.HEADER_ACTION_VOTE, controller.storyVoteLoading))
        if (hasAccount) add(HeaderAction(if (favorited) Res.drawable.ic_star_filled else Res.drawable.ic_star, if (favorited) "Remove favorite" else "Favorite", CommentsComposeController.HEADER_ACTION_FAVORITE, controller.storyFavoriteLoading))
        if (bookmarksEnabled && !hasAccount) add(HeaderAction(if (bookmarked) Res.drawable.ic_bookmark_filled else Res.drawable.ic_bookmark, if (bookmarked) "Remove bookmark" else "Bookmark", CommentsComposeController.HEADER_ACTION_BOOKMARK))
        if (story.isLink && settings.canProvideSummary && !story.summaryGeneratedSuccessfully) add(HeaderAction(Res.drawable.ic_auto_awesome, "Summarize", CommentsComposeController.HEADER_ACTION_SUMMARIZE, controller.storySummaryLoading))
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = actionHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.Center,
    ) {
        actions.forEach { action ->
            HeaderActionButton(action) {
                controller.listener.onHeaderAction(action.action)
            }
        }
        Box(
            Modifier.size(width = 48.dp, height = 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            CommentsTooltip("Share") {
                IconButton(
                    onClick = { shareExpanded = true },
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_share),
                        contentDescription = "Share",
                        modifier = Modifier.size(24.dp),
                        tint = HarmonicTheme.colors.drawable,
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
                    onClick = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                    modifier = Modifier.size(width = 48.dp, height = 58.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = Modifier.size(24.dp),
                        tint = HarmonicTheme.colors.drawable,
                    )
                }
            }
        }
        Box(
            Modifier.size(width = 48.dp, height = 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            CommentsTooltip("More options") {
                IconButton(
                    onClick = { moreExpanded = true },
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_more_vert),
                        contentDescription = "More options",
                        modifier = Modifier.size(24.dp),
                        tint = HarmonicTheme.colors.drawable,
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
                onDismiss = {
                    moreExpanded = false
                    sortExpanded = false
                    archiveExpanded = false
                },
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

private data class HeaderAction(
    val icon: DrawableResource,
    val label: String,
    val action: Int,
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
    onClick: () -> Unit,
) {
    CommentsTooltip(action.label) {
        IconButton(
            onClick = onClick,
            enabled = !action.loading,
            modifier = Modifier.size(width = 48.dp, height = 58.dp),
        ) {
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
                        tint = HarmonicTheme.colors.drawable,
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
    onAction: (Int) -> Unit,
) {
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss) {
        @Composable fun action(label: String, id: Int) {
            DropdownMenuItem(
                text = { CommentsMenuText(label) },
                onClick = {
                    onDismiss()
                    onAction(id)
                },
            )
        }
        if (isLink) {
            action("Article link", CommentsComposeController.SHARE_ARTICLE)
            action("Article link and title", CommentsComposeController.SHARE_ARTICLE_TITLE)
        }
        action("HN link", CommentsComposeController.SHARE_HN)
        action("HN link and title", CommentsComposeController.SHARE_HN_TITLE)
        if (isLink) action("Article + HN link and title", CommentsComposeController.SHARE_ALL)
    }
}

@Composable
private fun MoreMenu(
    expanded: Boolean,
    sortExpanded: Boolean,
    archiveExpanded: Boolean,
    controller: CommentsComposeController,
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
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss) {
        if (sortExpanded || archiveExpanded) {
            DropdownMenuItem(
                text = {
                    CommentsMenuText(
                        if (sortExpanded) "Sort comments" else "View on archive",
                    )
                },
                leadingIcon = {
                    Icon(
                        painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                onClick = onSubmenuBack,
            )
            HorizontalDivider(color = HarmonicTheme.colors.commentDivider)
        }

        if (sortExpanded) {
            val options = stringArrayResource(Res.array.comment_sorting)
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        CommentsMenuText(
                            if (option == controller.currentSorting) "✓ $option" else option,
                        )
                    },
                    onClick = {
                        onDismiss()
                        controller.listener.onSortComments(option)
                    },
                )
            }
            return@HarmonicDropdownMenu
        }

        if (archiveExpanded) {
            @Composable fun archive(label: String, action: Int) {
                DropdownMenuItem(
                    text = { CommentsMenuText(label) },
                    onClick = {
                        onDismiss()
                        controller.listener.onMoreAction(action)
                    },
                )
            }
            archive("archive.org", CommentsComposeController.MORE_ARCHIVE_ORG)
            archive("archive.is", CommentsComposeController.MORE_ARCHIVE_IS)
            archive("archive.today", CommentsComposeController.MORE_ARCHIVE_TODAY)
            return@HarmonicDropdownMenu
        }

        @Composable fun action(label: String, icon: DrawableResource, id: Int) {
            DropdownMenuItem(
                text = { CommentsMenuText(label) },
                leadingIcon = {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                onClick = {
                    onDismiss()
                    controller.listener.onMoreAction(id)
                },
            )
        }
        if (settings.hasAccountDetails) action("Refresh", Res.drawable.ic_refresh, CommentsComposeController.MORE_REFRESH)
        if (story.isComment && story.parentId > 0) action("Open parent", Res.drawable.ic_reply, CommentsComposeController.MORE_OPEN_PARENT)
        if (story.isComment && story.commentMasterId > 0) action("Open top level", Res.drawable.ic_arrow_upward, CommentsComposeController.MORE_OPEN_TOP_LEVEL)
        if (settings.hasAccountDetails && bookmarksEnabled) {
            action(
                if (bookmarked) "Remove bookmark" else "Bookmark",
                if (bookmarked) Res.drawable.ic_bookmark_filled else Res.drawable.ic_bookmark,
                CommentsComposeController.MORE_TOGGLE_BOOKMARK,
            )
        }
        if (commentsCount > 1) action("Search comments", Res.drawable.ic_search, CommentsComposeController.MORE_SEARCH)
        if (commentsCount > 2) {
            DropdownMenuItem(
                text = { CommentsMenuText("Sort comments") },
                leadingIcon = {
                    Icon(
                        painterResource(Res.drawable.ic_filter_list),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                trailingIcon = {
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                onClick = onSortExpanded,
            )
        }
        if (!controller.commentsByOpFilterActive && controller.hasCommentsByOp) {
            action("Comments by OP", Res.drawable.ic_person, CommentsComposeController.MORE_COMMENTS_BY_OP)
        }
        action("Open in browser", Res.drawable.ic_open_in_browser, CommentsComposeController.MORE_OPEN_BROWSER)
        if (controller.adBlockActive) {
            action("Disable AdBlock", Res.drawable.ic_block, CommentsComposeController.MORE_DISABLE_ADBLOCK)
        }
        if (story.isLink) {
            DropdownMenuItem(
                text = { CommentsMenuText("View on archive") },
                leadingIcon = {
                    Icon(
                        painterResource(Res.drawable.ic_history),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                trailingIcon = {
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                onClick = onArchiveExpanded,
            )
        }
    }
}

@Composable
private fun CommentsMenuText(text: String) {
    HarmonicMenuText(text)
}

@Composable
fun OpFilterBanner(controller: CommentsComposeController) {
    AnimatedVisibility(
        visible = controller.commentsByOpFilterActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HarmonicTheme.colors.surfaceContainerHigh)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Showing comments by OP",
                modifier = Modifier.weight(1f),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
            CommentsTooltip("Show all comments") {
                IconButton(
                    onClick = { controller.listener.onMoreAction(CommentsComposeController.MORE_COMMENTS_BY_OP) },
                ) {
                    Icon(painterResource(Res.drawable.ic_close), contentDescription = "Show all comments")
                }
            }
        }
    }
}

private enum class HeaderStatusState {
    Loading,
    Failed,
    Empty,
    Refresh,
    None,
}

@Composable
fun HeaderStatus(controller: CommentsComposeController, lastRefreshedText: String?) {
    val showLoading = !controller.loadingFailed &&
        (!controller.commentsLoaded || controller.commentsRefreshInProgress)
    val showEmpty = !controller.loadingFailed && controller.commentsLoaded &&
        controller.comments.size <= 1
    AnimatedContent(
        targetState = when {
            controller.loadingFailed -> HeaderStatusState.Failed
            showLoading -> HeaderStatusState.Loading
            showEmpty -> HeaderStatusState.Empty
            controller.showUpdate -> HeaderStatusState.Refresh
            else -> HeaderStatusState.None
        },
        transitionSpec = {
            val exitFade = if (initialState == HeaderStatusState.Loading) {
                fadeOut(tween(durationMillis = 90))
            } else {
                fadeOut()
            }
            (fadeIn() + expandVertically()).togetherWith(exitFade + shrinkVertically())
        },
        label = "comments header status",
    ) { state ->
        when (state) {
            HeaderStatusState.Loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (controller.commentsLoaded) 16.dp else 44.dp, bottom = 18.dp),
                contentAlignment = Alignment.Center,
            ) { HarmonicLoadingIndicator(Modifier.size(42.dp)) }
            HeaderStatusState.Failed -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(Res.drawable.ic_cloud_off), null, Modifier.size(40.dp))
                Text(
                    if (controller.loadingFailedServerError) "Loading failed" else "No internet connection",
                    modifier = Modifier.padding(top = 6.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Try again") }
            }
            HeaderStatusState.Empty -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(Res.drawable.ic_comment), null, Modifier.size(42.dp))
                Text(
                    if (controller.story.isComment) "No replies" else "No comments",
                    modifier = Modifier.padding(top = 4.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
            HeaderStatusState.Refresh -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (lastRefreshedText != null) {
                    Text(
                        text = lastRefreshedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = HarmonicTheme.colors.textSecondary,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = HarmonicTheme.colors.overlayButton,
                    contentColor = Color.White,
                    icon = {
                        Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null)
                    },
                    text = {
                        Text(
                            "Tap to refresh",
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
            HeaderStatusState.None -> Spacer(Modifier.height(0.dp))
        }
    }
}
