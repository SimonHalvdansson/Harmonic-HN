package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.utils.AgePolicy
import com.simon.harmonichackernews.utils.HtmlTextUtils

data class FeatureDecision<Action, Effect>(
    val actions: List<Action> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val refreshState: Boolean = false,
    val refreshNavigation: Boolean = false,
)

enum class ArchiveProvider { ORG, IS, TODAY }

sealed interface CommentsPlatformEffect {
    data class OpenUser(val userName: String) : CommentsPlatformEffect
    data class OpenEditor(val destination: EditorDestination) : CommentsPlatformEffect
    data object RequestLogin : CommentsPlatformEffect
    data class ShowMessage(val message: String) : CommentsPlatformEffect
    data class ShareText(val text: String) : CommentsPlatformEffect
    data class CopyText(val label: String, val text: String) : CommentsPlatformEffect
    data object ReloadLinkPreviews : CommentsPlatformEffect
    data object Summarize : CommentsPlatformEffect
    data class OpenStory(val destination: StoryDestination) : CommentsPlatformEffect
    data class OpenExternalLink(
        val url: String,
        val preferInApp: Boolean = true,
    ) : CommentsPlatformEffect
    data object ShowSearch : CommentsPlatformEffect
    data object DisableAdBlock : CommentsPlatformEffect
    data object ReloadWebsite : CommentsPlatformEffect
    data object ExpandSheet : CommentsPlatformEffect
    data object OpenWebsiteInBrowser : CommentsPlatformEffect
    data object ToggleReaderMode : CommentsPlatformEffect
    data object ToggleDarkMode : CommentsPlatformEffect
}

data class CommentsHeaderContext(
    val story: Story,
    val hasAccount: Boolean,
)

data class CommentActionContext(
    val comment: Comment,
    val storyTitle: String?,
    val hasAccount: Boolean,
    val voteLoading: Boolean,
    val upvoted: Boolean,
    val downvoted: Boolean,
    val nowMillis: Long,
)

/**
 * Common comments interaction policy. It translates shared UI intents into model work and a
 * deliberately small set of effects that each platform implements.
 */
object CommentsUiOrchestrator {
    private const val HN_ITEM_URL = "https://news.ycombinator.com/item?id="

    fun header(
        action: CommentsHeaderAction,
        context: CommentsHeaderContext,
    ): FeatureDecision<CommentsAction, CommentsPlatformEffect> {
        val story = context.story
        return when (action) {
            CommentsHeaderAction.USER -> effectIfNotBlank(story.by) {
                CommentsPlatformEffect.OpenUser(it)
            }
            CommentsHeaderAction.REPLY -> if (!context.hasAccount) {
                effect(CommentsPlatformEffect.RequestLogin)
            } else {
                effect(
                    CommentsPlatformEffect.OpenEditor(
                        EditorDestination(
                            type = EditorType.TOP_LEVEL_COMMENT,
                            itemId = story.id,
                            parentText = story.title,
                            postTitle = story.title,
                        ),
                    ),
                )
            }
            CommentsHeaderAction.VOTE -> accountAction(context.hasAccount) {
                CommentsAction.ToggleStoryVote(story.id, story.isComment)
            }
            CommentsHeaderAction.FAVORITE -> accountAction(context.hasAccount) {
                CommentsAction.ToggleStoryFavorite(story.id, story.isComment)
            }
            CommentsHeaderAction.BOOKMARK -> action(
                CommentsAction.ToggleBookmark(story.id),
                refreshState = true,
            )
            CommentsHeaderAction.SUMMARIZE -> effect(CommentsPlatformEffect.Summarize)
            CommentsHeaderAction.REFRESH -> FeatureDecision()
        }
    }

    fun share(action: CommentsShareAction, story: Story): CommentsPlatformEffect.ShareText {
        val hnUrl = "$HN_ITEM_URL${story.id}"
        val text = when (action) {
            CommentsShareAction.ARTICLE -> story.url.orEmpty()
            CommentsShareAction.ARTICLE_WITH_TITLE -> "${story.title} | ${story.url}"
            CommentsShareAction.HN -> hnUrl
            CommentsShareAction.HN_WITH_TITLE -> "${story.title} | $hnUrl"
            CommentsShareAction.ARTICLE_AND_HN ->
                "${story.title.orEmpty()} | ${story.url}\n\n---\n\nHacker News Comments | $hnUrl"
        }
        return CommentsPlatformEffect.ShareText(text)
    }

    fun more(
        action: CommentsMoreAction,
        story: Story,
        commentsByOpActive: Boolean,
    ): FeatureDecision<CommentsAction, CommentsPlatformEffect> = when (action) {
        CommentsMoreAction.REFRESH -> FeatureDecision()
        CommentsMoreAction.OPEN_PARENT -> itemEffect(story.parentId)
        CommentsMoreAction.OPEN_TOP_LEVEL -> itemEffect(story.commentMasterId)
        CommentsMoreAction.TOGGLE_BOOKMARK -> action(
            CommentsAction.ToggleBookmark(story.id),
            refreshState = true,
        )
        CommentsMoreAction.SEARCH -> FeatureDecision(
            actions = listOf(CommentsAction.ResetCommentsByOp),
            effects = listOf(CommentsPlatformEffect.ShowSearch),
            refreshState = true,
            refreshNavigation = true,
        )
        CommentsMoreAction.COMMENTS_BY_OP -> action(
            if (commentsByOpActive) {
                CommentsAction.ResetCommentsByOp
            } else {
                CommentsAction.ShowCommentsByOp
            },
            refreshState = true,
            refreshNavigation = true,
        )
        CommentsMoreAction.OPEN_BROWSER -> effect(
            CommentsPlatformEffect.OpenExternalLink(
                url = "$HN_ITEM_URL${story.id}",
                preferInApp = false,
            ),
        )
        CommentsMoreAction.DISABLE_AD_BLOCK -> effect(CommentsPlatformEffect.DisableAdBlock)
        CommentsMoreAction.ARCHIVE_ORG,
        CommentsMoreAction.ARCHIVE_IS,
        CommentsMoreAction.ARCHIVE_TODAY -> FeatureDecision()
    }

    fun archiveProvider(action: CommentsMoreAction): ArchiveProvider? = when (action) {
        CommentsMoreAction.ARCHIVE_ORG -> ArchiveProvider.ORG
        CommentsMoreAction.ARCHIVE_IS -> ArchiveProvider.IS
        CommentsMoreAction.ARCHIVE_TODAY -> ArchiveProvider.TODAY
        else -> null
    }

    fun sheet(action: CommentsSheetAction): CommentsPlatformEffect = when (action) {
        CommentsSheetAction.REFRESH -> CommentsPlatformEffect.ReloadWebsite
        CommentsSheetAction.EXPAND -> CommentsPlatformEffect.ExpandSheet
        CommentsSheetAction.BROWSER -> CommentsPlatformEffect.OpenWebsiteInBrowser
        CommentsSheetAction.READER -> CommentsPlatformEffect.ToggleReaderMode
        CommentsSheetAction.INVERT -> CommentsPlatformEffect.ToggleDarkMode
    }

    fun comment(
        action: CommentMenuAction,
        context: CommentActionContext,
    ): FeatureDecision<CommentsAction, CommentsPlatformEffect> {
        val comment = context.comment
        return when (action) {
            CommentMenuAction.USER -> effectIfNotBlank(comment.by) {
                CommentsPlatformEffect.OpenUser(it)
            }
            CommentMenuAction.SHARE ->
                effect(CommentsPlatformEffect.ShareText("$HN_ITEM_URL${comment.id}"))
            CommentMenuAction.COPY -> effect(
                CommentsPlatformEffect.CopyText(
                    label = "Hacker News comment",
                    text = HtmlTextUtils.plainText(comment.text),
                ),
            )
            CommentMenuAction.BOOKMARK -> action(
                CommentsAction.ToggleBookmark(comment.id),
                refreshState = true,
            )
            CommentMenuAction.REPLY -> when {
                !context.hasAccount -> effect(CommentsPlatformEffect.RequestLogin)
                AgePolicy.isOlderThanTwoWeeks(comment.time, context.nowMillis) ->
                    effect(CommentsPlatformEffect.ShowMessage("This comment is too old to reply to"))
                else -> effect(
                    CommentsPlatformEffect.OpenEditor(
                        EditorDestination(
                            type = EditorType.COMMENT_REPLY,
                            itemId = comment.id,
                            parentText = comment.text,
                            postTitle = context.storyTitle,
                            userName = comment.by,
                        ),
                    ),
                )
            }
            CommentMenuAction.FAVORITE -> accountAction(context.hasAccount) {
                CommentsAction.ToggleCommentFavorite(comment.id)
            }
            CommentMenuAction.UPVOTE,
            CommentMenuAction.UNVOTE,
            CommentMenuAction.DOWNVOTE -> when {
                !context.hasAccount -> effect(CommentsPlatformEffect.RequestLogin)
                context.voteLoading -> FeatureDecision()
                else -> action(
                    CommentsAction.VoteComment(
                        commentId = comment.id,
                        direction = when (action) {
                            CommentMenuAction.UPVOTE -> VoteDirection.UP.wireValue
                            CommentMenuAction.DOWNVOTE -> VoteDirection.DOWN.wireValue
                            else -> VoteDirection.REMOVE.wireValue
                        },
                        previousDownvoted = !context.upvoted && context.downvoted,
                    ),
                )
            }
        }
    }

    private fun itemEffect(itemId: Int) = if (itemId > 0) {
        effect(CommentsPlatformEffect.OpenStory(StoryDestination(storyId = itemId)))
    } else {
        FeatureDecision()
    }

    private fun accountAction(
        hasAccount: Boolean,
        create: () -> CommentsAction,
    ) = if (hasAccount) action(create()) else effect(CommentsPlatformEffect.RequestLogin)

    private fun action(
        action: CommentsAction,
        refreshState: Boolean = false,
        refreshNavigation: Boolean = false,
    ) = FeatureDecision<CommentsAction, CommentsPlatformEffect>(
        actions = listOf(action),
        refreshState = refreshState,
        refreshNavigation = refreshNavigation,
    )

    private fun effect(effect: CommentsPlatformEffect) =
        FeatureDecision<CommentsAction, CommentsPlatformEffect>(effects = listOf(effect))

    private inline fun effectIfNotBlank(
        value: String?,
        create: (String) -> CommentsPlatformEffect,
    ) = value?.takeIf(String::isNotBlank)?.let { effect(create(it)) } ?: FeatureDecision()
}

sealed interface StoriesPlatformEffect {
    data object OpenSettings : StoriesPlatformEffect
    data object RequestLogin : StoriesPlatformEffect
    data class OpenProfile(val userName: String) : StoriesPlatformEffect
    data object ShowCacheDialog : StoriesPlatformEffect
    data object OpenSubmitEditor : StoriesPlatformEffect
}

object StoriesUiOrchestrator {
    fun searchOption(kind: StorySearchOption, index: Int): StoriesAction = when (kind) {
        StorySearchOption.SORT -> StoriesAction.SelectSearchSort(index)
        StorySearchOption.DATE -> StoriesAction.SelectSearchDateRange(index)
        StorySearchOption.POINTS -> StoriesAction.SelectSearchMinimumPoints(index)
        StorySearchOption.COMMENTS -> StoriesAction.SelectSearchMinimumComments(index)
    }

    fun menu(action: StoriesMenuAction, accountUser: String?): StoriesPlatformEffect? = when (action) {
        StoriesMenuAction.SETTINGS -> StoriesPlatformEffect.OpenSettings
        StoriesMenuAction.ACCOUNT -> if (accountUser.isNullOrBlank()) {
            StoriesPlatformEffect.RequestLogin
        } else {
            null
        }
        StoriesMenuAction.PROFILE -> accountUser?.takeIf(String::isNotBlank)?.let {
            StoriesPlatformEffect.OpenProfile(it)
        }
        StoriesMenuAction.CACHE -> StoriesPlatformEffect.ShowCacheDialog
        StoriesMenuAction.SUBMIT -> StoriesPlatformEffect.OpenSubmitEditor
        StoriesMenuAction.CLEAR_HISTORY -> null
    }
}
