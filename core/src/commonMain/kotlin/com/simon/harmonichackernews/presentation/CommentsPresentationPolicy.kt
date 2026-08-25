package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CommentThreadLoadResult
import com.simon.harmonichackernews.network.CommentThreadSource
import com.simon.harmonichackernews.utils.AgePolicy
import com.simon.harmonichackernews.utils.StoryTitlePolicy

enum class PollLoadAction {
    NONE,
    LOAD_KNOWN_OPTIONS,
    LOOK_UP_OPTIONS,
}

/** Portable freshness, failure and header-merge policy for the comments feature. */
object CommentsPresentationPolicy {
    const val STALE_AFTER_MILLIS: Long = 60L * 60L * 1_000L
    const val MAX_STORY_AGE_FOR_UPDATE_MILLIS: Long = AgePolicy.TWO_HOURS_MILLIS

    fun shouldShowUpdateAffordance(
        nowMillis: Long,
        lastLoadedMillis: Long,
        alwaysShow: Boolean,
        storyTimeEpochSeconds: Int,
    ): Boolean = alwaysShow || (
        lastLoadedMillis != 0L &&
            nowMillis - lastLoadedMillis > STALE_AFTER_MILLIS &&
            !AgePolicy.isOlderThanTwoHours(storyTimeEpochSeconds, nowMillis)
        )

    fun failureFor(result: CommentThreadLoadResult.Failure): StoryLoadFailure =
        if (result.source == CommentThreadSource.ALGOLIA && !result.noInternet) {
            StoryLoadFailure.NOT_FOUND
        } else {
            StoryLoadFailure.GENERAL
        }

    fun nextPollLoadAction(
        active: Boolean,
        loadStarted: Boolean,
        lookupStarted: Boolean,
        story: Story?,
    ): PollLoadAction = when {
        !active || loadStarted || story == null || story.isComment -> PollLoadAction.NONE
        story.pollOptions != null -> PollLoadAction.LOAD_KNOWN_OPTIONS
        lookupStarted || story.id <= 0 || !StoryTitlePolicy.mayDescribePoll(story.title) ->
            PollLoadAction.NONE
        else -> PollLoadAction.LOOK_UP_OPTIONS
    }

    fun mergeOfficialStoryHeader(target: Story, source: Story) {
        target.title = source.title
        target.by = source.by
        target.score = source.score
        target.time = source.time
        target.url = source.url
        target.isLink = source.isLink
        target.isComment = source.isComment
        target.text = source.text
        target.kids = source.kids
        target.pollOptions = source.pollOptions
        target.descendants = source.descendants
        target.parentId = source.parentId
        target.loaded = true
    }
}
