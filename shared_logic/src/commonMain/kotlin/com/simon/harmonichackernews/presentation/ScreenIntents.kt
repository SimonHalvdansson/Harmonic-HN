package com.simon.harmonichackernews.presentation

/** Typed user intents shared by every stories screen implementation. */
enum class StorySearchOption { SORT, DATE, POINTS, COMMENTS }

enum class StoriesMenuAction {
    SETTINGS,
    ACCOUNT,
    PROFILE,
    CACHE,
    SUBMIT,
    CLEAR_HISTORY,
}

/** Typed user intents shared by every comments screen implementation. */
enum class CommentsHeaderAction {
    USER,
    REPLY,
    VOTE,
    FAVORITE,
    BOOKMARK,
    SUMMARIZE,
    REFRESH,
}

enum class CommentsShareAction {
    ARTICLE,
    ARTICLE_WITH_TITLE,
    HN,
    HN_WITH_TITLE,
    ARTICLE_AND_HN,
}

enum class CommentsMoreAction {
    REFRESH,
    OPEN_PARENT,
    OPEN_TOP_LEVEL,
    TOGGLE_BOOKMARK,
    SEARCH,
    COMMENTS_BY_OP,
    OPEN_BROWSER,
    DISABLE_AD_BLOCK,
    ARCHIVE_ORG,
    ARCHIVE_IS,
    ARCHIVE_TODAY,
}

enum class CommentsSheetAction {
    REFRESH,
    EXPAND,
    BROWSER,
    READER,
    INVERT,
}

enum class CommentMenuAction {
    USER,
    SHARE,
    COPY,
    BOOKMARK,
    FAVORITE,
    UPVOTE,
    UNVOTE,
    DOWNVOTE,
    REPLY,
}

enum class VoteDirection(val wireValue: String) {
    UP("up"),
    DOWN("down"),
    REMOVE("un"),
}
