package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.utils.AgePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScreenOrchestratorsTest {
    @Test
    fun storySearchOptionsMapToTypedPresenterActions() {
        assertEquals(
            StoriesAction.SelectSearchSort(2),
            StoriesUiOrchestrator.searchOption(StorySearchOption.SORT, 2),
        )
        assertEquals(
            StoriesAction.SelectSearchDateRange(1),
            StoriesUiOrchestrator.searchOption(StorySearchOption.DATE, 1),
        )
        assertEquals(
            StoriesAction.SelectSearchMinimumPoints(3),
            StoriesUiOrchestrator.searchOption(StorySearchOption.POINTS, 3),
        )
        assertEquals(
            StoriesAction.SelectSearchMinimumComments(4),
            StoriesUiOrchestrator.searchOption(StorySearchOption.COMMENTS, 4),
        )
    }

    @Test
    fun storyMenusBecomePlatformEffects() {
        assertEquals(
            StoriesPlatformEffect.OpenSettings,
            StoriesUiOrchestrator.menu(StoriesMenuAction.SETTINGS, null),
        )
        assertEquals(
            StoriesPlatformEffect.OpenSubmitEditor,
            StoriesUiOrchestrator.menu(StoriesMenuAction.SUBMIT, null),
        )
        assertEquals(
            StoriesPlatformEffect.ClearHistory,
            StoriesUiOrchestrator.menu(StoriesMenuAction.CLEAR_HISTORY, null),
        )
        assertEquals(
            StoriesPlatformEffect.RequestLogin,
            StoriesUiOrchestrator.menu(StoriesMenuAction.ACCOUNT, null),
        )
        assertEquals(
            StoriesPlatformEffect.Logout,
            StoriesUiOrchestrator.menu(StoriesMenuAction.ACCOUNT, "simon"),
        )
        assertEquals(
            StoriesPlatformEffect.OpenProfile("simon"),
            StoriesUiOrchestrator.menu(StoriesMenuAction.PROFILE, "simon"),
        )
    }

    @Test
    fun commentsHeaderRoutesModelWorkAndAccountGates() {
        val story = story()
        val loggedIn = CommentsHeaderContext(story, hasAccount = true)

        assertEquals(
            listOf(CommentsAction.ToggleStoryVote(story.id, false)),
            CommentsUiOrchestrator.header(CommentsHeaderAction.VOTE, loggedIn).actions,
        )
        assertEquals(
            listOf(CommentsAction.ToggleBookmark(story.id)),
            CommentsUiOrchestrator.header(CommentsHeaderAction.BOOKMARK, loggedIn).actions,
        )
        assertEquals(
            listOf(CommentsPlatformEffect.RequestLogin),
            CommentsUiOrchestrator.header(
                CommentsHeaderAction.FAVORITE,
                loggedIn.copy(hasAccount = false),
            ).effects,
        )

        val editor = assertIs<CommentsPlatformEffect.OpenEditor>(
            CommentsUiOrchestrator.header(CommentsHeaderAction.REPLY, loggedIn).effects.single(),
        )
        assertEquals(EditorType.TOP_LEVEL_COMMENT, editor.destination.type)
        assertEquals(story.id, editor.destination.itemId)
    }

    @Test
    fun commentsMoreFilterPolicyIsShared() {
        val story = story()
        val show = CommentsUiOrchestrator.more(
            CommentsMoreAction.COMMENTS_BY_OP,
            story,
            commentsByOpActive = false,
        )
        val reset = CommentsUiOrchestrator.more(
            CommentsMoreAction.COMMENTS_BY_OP,
            story,
            commentsByOpActive = true,
        )

        assertEquals(listOf(CommentsAction.ShowCommentsByOp), show.actions)
        assertEquals(listOf(CommentsAction.ResetCommentsByOp), reset.actions)
        assertTrue(show.refreshState)
        assertTrue(show.refreshNavigation)
    }

    @Test
    fun commentReplyPolicyHandlesLoginAgeAndEditorDestination() {
        val now = 2_000_000_000_000L
        val comment = comment(time = (now / 1_000L).toInt())
        fun context(hasAccount: Boolean, time: Int = comment.time) = CommentActionContext(
            comment = comment.apply { this.time = time },
            storyTitle = "Post title",
            hasAccount = hasAccount,
            voteLoading = false,
            upvoted = false,
            downvoted = false,
            nowMillis = now,
        )

        assertEquals(
            listOf(CommentsPlatformEffect.RequestLogin),
            CommentsUiOrchestrator.comment(
                CommentMenuAction.REPLY,
                context(hasAccount = false),
            ).effects,
        )

        val oldTime = ((now - AgePolicy.TWO_WEEKS_MILLIS - 1L) / 1_000L).toInt()
        assertIs<CommentsPlatformEffect.ShowMessage>(
            CommentsUiOrchestrator.comment(
                CommentMenuAction.REPLY,
                context(hasAccount = true, time = oldTime),
            ).effects.single(),
        )

        comment.time = (now / 1_000L).toInt()
        val editor = assertIs<CommentsPlatformEffect.OpenEditor>(
            CommentsUiOrchestrator.comment(
                CommentMenuAction.REPLY,
                context(hasAccount = true),
            ).effects.single(),
        )
        assertEquals(EditorType.COMMENT_REPLY, editor.destination.type)
        assertEquals(comment.id, editor.destination.itemId)
        assertEquals(comment.text, editor.destination.parentText)
    }

    @Test
    fun commentVotingAndCopyArePortablePolicies() {
        val comment = comment()
        val context = CommentActionContext(
            comment = comment,
            storyTitle = "Post title",
            hasAccount = true,
            voteLoading = false,
            upvoted = false,
            downvoted = true,
            nowMillis = 2_000_000_000_000L,
        )

        val vote = assertIs<CommentsAction.VoteComment>(
            CommentsUiOrchestrator.comment(CommentMenuAction.UPVOTE, context).actions.single(),
        )
        assertEquals(VoteDirection.UP.wireValue, vote.direction)
        assertTrue(vote.previousDownvoted)

        val copy = assertIs<CommentsPlatformEffect.CopyText>(
            CommentsUiOrchestrator.comment(CommentMenuAction.COPY, context).effects.single(),
        )
        assertEquals("Hello & goodbye", copy.text)
    }

    @Test
    fun storyShareTextMatchesExistingAndroidFormatting() {
        val story = story()
        assertEquals(
            "Post title | https://example.com\n\n---\n\n" +
                "Hacker News Comments | https://news.ycombinator.com/item?id=123",
            CommentsUiOrchestrator.share(
                CommentsShareAction.ARTICLE_AND_HN,
                story,
            ).text,
        )
    }

    private fun story() = Story().apply {
        id = 123
        title = "Post title"
        by = "author"
        url = "https://example.com"
        isLink = true
    }

    private fun comment(time: Int = 2_000_000_000) = Comment().apply {
        id = 456
        by = "commenter"
        text = "<p>Hello &amp; <b>goodbye</b></p>"
        this.time = time
    }
}
