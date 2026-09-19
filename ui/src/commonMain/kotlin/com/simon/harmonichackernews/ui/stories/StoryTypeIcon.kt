package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Keep related time ranges recognizable without repeating icons across unrelated feeds. */
internal val StoryType.menuIcon: DrawableResource
    get() = when (this) {
        StoryType.TOP_STORIES -> Res.drawable.ic_local_fire_department
        StoryType.LAST_24_HOURS -> Res.drawable.ic_feed_24_hours
        StoryType.LAST_48_HOURS -> Res.drawable.ic_feed_48_hours
        StoryType.LAST_WEEK -> Res.drawable.ic_feed_week
        StoryType.NEW_STORIES -> Res.drawable.ic_bolt
        StoryType.BEST_STORIES -> Res.drawable.ic_trophy
        StoryType.ASK_HN -> Res.drawable.ic_live_help
        StoryType.SHOW_HN -> Res.drawable.ic_rocket_launch
        StoryType.HN_JOBS -> Res.drawable.ic_work
        StoryType.CLASSIC -> Res.drawable.ic_account_balance
        StoryType.BEST_COMMENTS -> Res.drawable.ic_reviews
        StoryType.HIGHLIGHTS -> Res.drawable.ic_format_quote
        StoryType.ACTIVE -> Res.drawable.ic_monitoring
        StoryType.FRONT -> Res.drawable.ic_newspaper
        StoryType.UNSLOP -> Res.drawable.ic_feed_unslop
        StoryType.BOOKMARKS -> Res.drawable.ic_bookmark
        StoryType.FAVORITES -> Res.drawable.ic_favorite
        StoryType.UPVOTED -> Res.drawable.ic_arrow_upward
        StoryType.HISTORY -> Res.drawable.ic_history
        StoryType.UNKNOWN -> Res.drawable.ic_subject
    }
