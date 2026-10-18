package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.CommentsHeaderButton
import org.jetbrains.compose.resources.DrawableResource

internal val CommentsHeaderButton.icon: DrawableResource
    get() = when (this) {
        CommentsHeaderButton.USER -> Res.drawable.ic_account_circle
        CommentsHeaderButton.COMMENT -> Res.drawable.ic_comment
        CommentsHeaderButton.VOTE -> Res.drawable.ic_thumb_up
        CommentsHeaderButton.FAVORITE -> Res.drawable.ic_star
        CommentsHeaderButton.BOOKMARK -> Res.drawable.ic_bookmark
        CommentsHeaderButton.SUMMARIZE -> Res.drawable.ic_auto_awesome
        CommentsHeaderButton.SHARE -> Res.drawable.ic_share
        CommentsHeaderButton.REFRESH -> Res.drawable.ic_refresh
        CommentsHeaderButton.SIDE_BY_SIDE -> Res.drawable.ic_chrome_reader_mode
        CommentsHeaderButton.OPEN_PARENT -> Res.drawable.ic_reply
        CommentsHeaderButton.OPEN_TOP_LEVEL -> Res.drawable.ic_arrow_upward
        CommentsHeaderButton.SEARCH -> Res.drawable.ic_search
        CommentsHeaderButton.SORT -> Res.drawable.ic_filter_list
        CommentsHeaderButton.COMMENTS_BY_OP -> Res.drawable.ic_person
        CommentsHeaderButton.OPEN_BROWSER -> Res.drawable.ic_open_in_browser
        CommentsHeaderButton.DISABLE_AD_BLOCK -> Res.drawable.ic_block
        CommentsHeaderButton.ARCHIVE -> Res.drawable.ic_history
    }
