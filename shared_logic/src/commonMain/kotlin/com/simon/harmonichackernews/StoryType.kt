package com.simon.harmonichackernews

enum class StoryType(
    val label: String,
    val hackerNewsPath: String? = null,
    private val additionalFrontpage: Boolean = false,
    private val commentRows: Boolean = false,
    val isFrontpageLinkList: Boolean = false
) {
    TOP_STORIES("Top Stories"),
    LAST_24_HOURS("Last 24 hours"),
    LAST_48_HOURS("Last 48 hours"),
    LAST_WEEK("Last week"),
    NEW_STORIES("New Stories"),
    BEST_STORIES("Best Stories"),
    ASK_HN("Ask HN"),
    SHOW_HN("Show HN"),
    HN_JOBS("HN Jobs"),
    CLASSIC("Classic", "classic", true, false, false),
    BEST_COMMENTS("Best Comments", "bestcomments", true, true, false),
    HIGHLIGHTS("Highlights", "highlights", true, true, false),
    ACTIVE("Active", "active", true, false, false),
    FRONT("Front", "front", true, false, false),
    BOOKMARKS("Bookmarks"),
    FAVORITES("Favorites"),
    UPVOTED("Upvoted"),
    HISTORY("History"),
    UNKNOWN("");

    val isAlgolia: Boolean
        get() = this == LAST_24_HOURS || this == LAST_48_HOURS || this == LAST_WEEK

    val isActive: Boolean
        get() = this == ACTIVE

    val isFront: Boolean
        get() = this == FRONT

    val isBookmarks: Boolean
        get() = this == BOOKMARKS

    val isHistory: Boolean
        get() = this == HISTORY

    val isFavorites: Boolean
        get() = this == FAVORITES

    val isUpvoted: Boolean
        get() = this == UPVOTED

    val isUserItemList: Boolean
        get() = this.isFavorites || this.isUpvoted

    fun usesSavedItemFilter(): Boolean {
        return this.isBookmarks || this.isUserItemList
    }

    fun usesCommentRows(): Boolean {
        return this.isBookmarks || this.isUserItemList || commentRows
    }

    val isScrapedFrontpage: Boolean
        get() = additionalFrontpage && !this.isFrontpageLinkList

    val hackerNewsUrl: String?
        get() = when (this) {
            TOP_STORIES -> "https://hacker-news.firebaseio.com/v0/topstories.json"
            NEW_STORIES -> "https://hacker-news.firebaseio.com/v0/newstories.json"
            BEST_STORIES -> "https://hacker-news.firebaseio.com/v0/beststories.json"
            ASK_HN -> "https://hacker-news.firebaseio.com/v0/askstories.json"
            SHOW_HN -> "https://hacker-news.firebaseio.com/v0/showstories.json"
            HN_JOBS -> "https://hacker-news.firebaseio.com/v0/jobstories.json"
            else -> null
        }

    companion object {
        val additionalFrontpages = listOf(
            CLASSIC,
            BEST_COMMENTS,
            HIGHLIGHTS,
            ACTIVE,
            FRONT
        )

        fun fromLabel(label: CharSequence?): StoryType {
            return label?.let { value ->
                entries.firstOrNull { type -> type.label.contentEquals(value) }
            } ?: UNKNOWN
        }
    }
}
