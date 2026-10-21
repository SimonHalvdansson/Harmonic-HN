@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.settings.CommentsHeaderButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import com.simon.harmonichackernews.ui.content.ContentMotionEasing
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.simon.harmonichackernews.ui.common.AnimatedBookmarkIcon
import com.simon.harmonichackernews.utils.AgePolicy
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun CommentsHeaderActions(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
    bookmarksEnabled: Boolean,
    previewCommentActions: Boolean = false,
    onButtonsOverflowChanged: (List<CommentsHeaderButton>) -> Unit = {},
) {
    val story = controller.story
    val hasAccount = settings.hasAccountDetails
    val canReply = hasAccount && !AgePolicy.isOlderThanTwoWeeks(story.createdAtEpochSeconds)
    val combineActions = hasAccount && settings.combineHeaderActions
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
    // Keep the same keyed button compositions as availability and placement change.
    val actions = CommentsHeaderButton.entries.map { button ->
        val available = when (button) {
            CommentsHeaderButton.USER -> !combineActions
            CommentsHeaderButton.COMMENT -> canReply && !combineActions
            CommentsHeaderButton.VOTE -> hasAccount && !combineActions
            CommentsHeaderButton.FAVORITE -> hasAccount
            CommentsHeaderButton.BOOKMARK -> bookmarksEnabled
            CommentsHeaderButton.SUMMARIZE -> story.isLink && settings.canProvideSummary && !story.summaryGeneratedSuccessfully
            CommentsHeaderButton.SIDE_BY_SIDE -> controller.sideBySideAvailable
            CommentsHeaderButton.OPEN_PARENT -> story.isComment && story.parentId > 0
            CommentsHeaderButton.OPEN_TOP_LEVEL -> story.isComment && story.rootStoryId > 0
            CommentsHeaderButton.SEARCH -> previewCommentActions || controller.comments.size > 1
            CommentsHeaderButton.SORT -> previewCommentActions || controller.comments.size > 2
            CommentsHeaderButton.COMMENTS_BY_OP -> previewCommentActions ||
                (!controller.opThreadFilterEnabled && controller.hasCommentsByOp)
            CommentsHeaderButton.COMMENTS_BY_ME -> previewCommentActions ||
                (!controller.userThreadFilterEnabled && controller.hasCommentsByUser)
            CommentsHeaderButton.DISABLE_AD_BLOCK -> controller.adBlockActive
            CommentsHeaderButton.ARCHIVE -> story.isLink
            else -> true
        }
        val label = when (button) {
            CommentsHeaderButton.COMMENT -> if (story.isComment) "Reply to comment" else "Reply to post"
            CommentsHeaderButton.VOTE -> if (upvoted) "Remove vote" else "Upvote"
            CommentsHeaderButton.FAVORITE -> if (favorited) "Remove favorite" else "Favorite"
            CommentsHeaderButton.BOOKMARK -> if (bookmarked) "Remove bookmark" else "Bookmark"
            CommentsHeaderButton.SIDE_BY_SIDE -> if (controller.sideBySideActive) "Exit side by side" else button.label
            else -> button.label
        }
        val icon = when {
            button == CommentsHeaderButton.VOTE && upvoted -> Res.drawable.ic_thumb_up_filled
            button == CommentsHeaderButton.FAVORITE && favorited -> Res.drawable.ic_star_filled
            button == CommentsHeaderButton.BOOKMARK && bookmarked -> Res.drawable.ic_bookmark_filled
            else -> button.icon
        }
        val loading = when (button) {
            CommentsHeaderButton.VOTE -> controller.storyVoteLoading
            CommentsHeaderButton.FAVORITE -> controller.storyFavoriteLoading
            CommentsHeaderButton.SUMMARIZE -> controller.storySummaryLoading
            CommentsHeaderButton.REFRESH -> controller.refreshButtonInProgress
            else -> false
        }
        HeaderAction(icon, label, button, loading, available,
            active = button == CommentsHeaderButton.SIDE_BY_SIDE && controller.sideBySideActive)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(CommentsHeaderActionButtonSize)) {
        // Animate absolute positions once, including the Large-details inset. Animating the
        // parent inset too would continually retarget the buttons and prolong their motion.
        val inset = if (combineActions) 16f else 0f
        val contentWidth = maxWidth.value - inset * 2
        val capacity = (contentWidth / 48f).toInt().coerceAtLeast(2)
        val inline = settings.headerButtons.inlineButtons(actions.filter { it.available }.map { it.action },
            capacity, hasAccount, combineActions)
        val overflow = actions.filter { it.available && it.action !in inline }
        val selectedOverflow = overflow.filter {
            settings.headerButtons.isShown(it.action, hasAccount, combineActions)
        }.map { it.action }
        SideEffect { onButtonsOverflowChanged(selectedOverflow) }
        val count = inline.size + 1
        val padding = commentActionPadding(contentWidth, count)
        val buttonWidth = commentActionButtonWidth(contentWidth, count)
        val gap = ((contentWidth - padding * 2 - count * buttonWidth) / (count + 1)).coerceAtLeast(0f)
        fun position(index: Int) = (inset + padding + gap + index * (buttonWidth + gap)).dp
        val onAction: (CommentsHeaderButton) -> Unit = { button ->
            // Bookmark remains open so its changed state is visible in the menu.
            if (button != CommentsHeaderButton.BOOKMARK) dismissMenus()
            when (button) {
                CommentsHeaderButton.USER -> controller.listener.onHeaderAction(CommentsHeaderAction.USER)
                CommentsHeaderButton.COMMENT -> controller.listener.onHeaderAction(CommentsHeaderAction.REPLY)
                CommentsHeaderButton.VOTE -> controller.listener.onHeaderAction(CommentsHeaderAction.VOTE)
                CommentsHeaderButton.FAVORITE -> controller.listener.onHeaderAction(CommentsHeaderAction.FAVORITE)
                CommentsHeaderButton.BOOKMARK -> controller.listener.onHeaderAction(CommentsHeaderAction.BOOKMARK)
                CommentsHeaderButton.SUMMARIZE -> controller.listener.onHeaderAction(CommentsHeaderAction.SUMMARIZE)
                CommentsHeaderButton.SHARE -> shareExpanded = true
                CommentsHeaderButton.REFRESH -> {
                    controller.beginHeaderRefresh(showProgressInButton = button in inline)
                    controller.listener.onHeaderAction(CommentsHeaderAction.REFRESH)
                }
                CommentsHeaderButton.SIDE_BY_SIDE -> controller.toggleSideBySide()
                CommentsHeaderButton.SORT -> { sortExpanded = true; archiveExpanded = false; moreExpanded = true }
                CommentsHeaderButton.ARCHIVE -> { archiveExpanded = true; sortExpanded = false; moreExpanded = true }
                else -> controller.listener.onMoreAction(when (button) {
                    CommentsHeaderButton.OPEN_PARENT -> CommentsMoreAction.OPEN_PARENT
                    CommentsHeaderButton.OPEN_TOP_LEVEL -> CommentsMoreAction.OPEN_TOP_LEVEL
                    CommentsHeaderButton.SEARCH -> CommentsMoreAction.SEARCH
                    CommentsHeaderButton.COMMENTS_BY_OP -> CommentsMoreAction.COMMENTS_BY_OP
                    CommentsHeaderButton.COMMENTS_BY_ME -> CommentsMoreAction.COMMENTS_BY_ME
                    CommentsHeaderButton.OPEN_BROWSER -> CommentsMoreAction.OPEN_BROWSER
                    CommentsHeaderButton.DISABLE_AD_BLOCK -> CommentsMoreAction.DISABLE_AD_BLOCK
                })
            }
        }
        actions.forEach { action -> key(action.action) {
            val index = inline.indexOf(action.action)
            // Retained buttons keep moving while additions gently fade into their new slots.
            val lastPosition = remember { mutableStateOf(position(index.coerceAtLeast(0))) }
            if (index >= 0) lastPosition.value = position(index)
            val x by key(index >= 0) {
                animateDpAsState(lastPosition.value, tween(220, delayMillis = 80, easing = ContentMotionEasing), label = "${action.action} position")
            }
            AnimatedVisibility(
                visible = index >= 0,
                modifier = Modifier.offset(x = x),
                enter = fadeIn(tween(180, delayMillis = 120)) +
                    scaleIn(tween(180, delayMillis = 120), initialScale = 0.85f),
                exit = fadeOut(tween(80)),
            ) {
                Box {
                    HeaderActionButton(action, Modifier.width(buttonWidth.dp).height(CommentsHeaderActionButtonSize), story.id) {
                        onAction(action.action)
                    }
                    if (action.action == CommentsHeaderButton.SHARE) {
                        ShareMenu(shareExpanded, story.isLink, { shareExpanded = false }, controller.listener::onShareAction)
                    }
                }
            }
        } }
        val moreX by animateDpAsState(position(inline.size), tween(220, delayMillis = 80, easing = ContentMotionEasing), label = "More position")
        Box(Modifier.offset(x = moreX).width(buttonWidth.dp).height(CommentsHeaderActionButtonSize)) {
            CommentsTooltip("More options") {
                IconButton(onClick = {
                    sortExpanded = false
                    archiveExpanded = false
                    moreExpanded = true
                }, modifier = Modifier.width(buttonWidth.dp).height(CommentsHeaderActionButtonSize)) {
                    Icon(painterResource(Res.drawable.ic_more_vert), "More options", Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (CommentsHeaderButton.SHARE !in inline) {
                ShareMenu(shareExpanded, story.isLink, { shareExpanded = false }, controller.listener::onShareAction)
            }
            MoreMenu(
                expanded = moreExpanded,
                sortExpanded = sortExpanded,
                archiveExpanded = archiveExpanded,
                controller = controller,
                overflowActions = overflow,
                onAction = onAction,
                onDismiss = dismissMenus,
                onSubmenuBack = { sortExpanded = false; archiveExpanded = false },
            )
        }
    }
}

private val CommentsHeaderActionButtonSize = 54.dp

private data class HeaderAction(
    val icon: DrawableResource,
    val label: String,
    val action: CommentsHeaderButton,
    val loading: Boolean = false,
    val available: Boolean = true,
    val active: Boolean = false,
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
            if (action.action == CommentsHeaderButton.BOOKMARK) {
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
                // Keep the 28dp spinner and 24dp icon centered while the content size animates.
                contentAlignment = Alignment.Center,
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
                        tint = if (action.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    overflowActions: List<HeaderAction>,
    onAction: (CommentsHeaderButton) -> Unit,
    onDismiss: () -> Unit,
    onSubmenuBack: () -> Unit,
) {
    val story = controller.story
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
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
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
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        overflowActions.forEach { item ->
                            DropdownMenuItem(
                                text = { CommentsMenuText(item.label) },
                                leadingIcon = {
                                    if (item.action == CommentsHeaderButton.BOOKMARK) {
                                        AnimatedBookmarkIcon(item.icon == Res.drawable.ic_bookmark_filled, story.id, description = null)
                                    } else if (item.loading) HarmonicLoadingIndicator(Modifier.size(24.dp))
                                    else Icon(painterResource(item.icon), null)
                                },
                                trailingIcon = if (item.action in listOf(CommentsHeaderButton.SHARE,
                                        CommentsHeaderButton.SORT, CommentsHeaderButton.ARCHIVE)) {
                                    { Icon(painterResource(Res.drawable.ic_chevron_right), null) }
                                } else null,
                                enabled = !item.loading,
                                onClick = { onAction(item.action) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class MoreMenuPage { Root, Sort, Archive }

@Composable
private fun SubmenuHeader(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { CommentsMenuText(title) },
        leadingIcon = {
            Icon(
                painterResource(Res.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
