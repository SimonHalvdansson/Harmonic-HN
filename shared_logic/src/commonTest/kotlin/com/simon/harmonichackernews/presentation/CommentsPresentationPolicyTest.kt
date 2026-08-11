package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CommentThreadLoadResult
import com.simon.harmonichackernews.network.CommentThreadSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentsPresentationPolicyTest {
    @Test
    fun updateAffordanceRequiresAStaleLoadAndARecentStoryUnlessForced() {
        val now = 10_000_000L
        val recentStorySeconds = (now / 1_000L).toInt()
        val oldStorySeconds = (
            (now - CommentsPresentationPolicy.MAX_STORY_AGE_FOR_UPDATE_MILLIS - 1) / 1_000L
            ).toInt()

        assertTrue(
            CommentsPresentationPolicy.shouldShowUpdateAffordance(
                now,
                now - CommentsPresentationPolicy.STALE_AFTER_MILLIS - 1,
                false,
                recentStorySeconds,
            ),
        )
        assertFalse(
            CommentsPresentationPolicy.shouldShowUpdateAffordance(
                now,
                now - CommentsPresentationPolicy.STALE_AFTER_MILLIS,
                false,
                recentStorySeconds,
            ),
        )
        assertFalse(
            CommentsPresentationPolicy.shouldShowUpdateAffordance(
                now,
                now - CommentsPresentationPolicy.STALE_AFTER_MILLIS - 1,
                false,
                oldStorySeconds,
            ),
        )
        assertTrue(
            CommentsPresentationPolicy.shouldShowUpdateAffordance(
                now,
                0,
                true,
                oldStorySeconds,
            ),
        )
    }

    @Test
    fun threadFailuresKeepTheExistingUiClassification() {
        assertEquals(
            StoryLoadFailure.NOT_FOUND,
            CommentsPresentationPolicy.failureFor(
                CommentThreadLoadResult.Failure(
                    noInternet = false,
                    source = CommentThreadSource.ALGOLIA,
                ),
            ),
        )
        assertEquals(
            StoryLoadFailure.GENERAL,
            CommentsPresentationPolicy.failureFor(
                CommentThreadLoadResult.Failure(
                    noInternet = true,
                    source = CommentThreadSource.ALGOLIA,
                ),
            ),
        )
    }

    @Test
    fun pollLoadingUsesKnownIdsBeforeAttemptingTitleBasedDiscovery() {
        val knownPoll = story("Poll: choose").also { it.pollOptions = intArrayOf(1, 2) }
        val possiblePoll = story("Ask HN: a poll about KMP")

        assertEquals(
            PollLoadAction.LOAD_KNOWN_OPTIONS,
            CommentsPresentationPolicy.nextPollLoadAction(true, false, false, knownPoll),
        )
        assertEquals(
            PollLoadAction.LOOK_UP_OPTIONS,
            CommentsPresentationPolicy.nextPollLoadAction(true, false, false, possiblePoll),
        )
        assertEquals(
            PollLoadAction.NONE,
            CommentsPresentationPolicy.nextPollLoadAction(true, false, true, possiblePoll),
        )
        possiblePoll.isComment = true
        assertEquals(
            PollLoadAction.NONE,
            CommentsPresentationPolicy.nextPollLoadAction(true, false, false, possiblePoll),
        )
    }

    @Test
    fun officialStoryHeaderMergeCopiesEveryFieldUsedByComments() {
        val target = story("Old")
        val source = story("New").also {
            it.by = "author"
            it.score = 42
            it.time = 123
            it.url = "https://example.com"
            it.isLink = true
            it.text = "body"
            it.kids = intArrayOf(7, 8)
            it.pollOptions = intArrayOf(9)
            it.descendants = 2
            it.parentId = 3
        }

        CommentsPresentationPolicy.mergeOfficialStoryHeader(target, source)

        assertEquals("New", target.title)
        assertEquals("author", target.by)
        assertEquals(42, target.score)
        assertEquals("https://example.com", target.url)
        assertContentEquals(intArrayOf(7, 8), target.kids)
        assertContentEquals(intArrayOf(9), target.pollOptions)
        assertEquals(2, target.descendants)
        assertTrue(target.loaded)
    }

    private fun story(title: String) = Story(title, 10, true, false)
}
