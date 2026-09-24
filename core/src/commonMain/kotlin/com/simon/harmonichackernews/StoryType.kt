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
    UNSLOP("unslop.news", additionalFrontpage = true),
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
        get() = additionalFrontpage && hackerNewsPath != null && !this.isFrontpageLinkList

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
            FRONT,
            UNSLOP,
        )

        fun fromLabel(label: CharSequence?): StoryType {
            return label?.let { value ->
                entries.firstOrNull { type -> type.label.contentEquals(value) }
            } ?: UNKNOWN
        }
    }
}

/** Portable ordering and availability policy for the stories source selector. */
object StoryTypeMenuPolicy {
    private val personalPages = listOf(
        StoryType.BOOKMARKS,
        StoryType.FAVORITES,
        StoryType.HISTORY,
        StoryType.UPVOTED,
    )

    val baseFrontpages = listOf(
        StoryType.TOP_STORIES,
        StoryType.LAST_24_HOURS,
        StoryType.LAST_48_HOURS,
        StoryType.LAST_WEEK,
        StoryType.NEW_STORIES,
        StoryType.BEST_STORIES,
        StoryType.ASK_HN,
        StoryType.SHOW_HN,
        StoryType.HN_JOBS,
    )

    /** Stable enum names are persisted independently of display labels and availability. */
    fun sanitizeOrder(order: List<String>): List<String> = order.distinct().filter { name ->
        (baseFrontpages + StoryType.additionalFrontpages + personalPages).any { it.name == name }
    }

    fun frontpages(
        enabledAdditionalFrontpages: Set<String>,
        order: List<String> = emptyList(),
    ): List<StoryType> {
        val available = baseFrontpages +
            StoryType.additionalFrontpages.filter { it.label in enabledAdditionalFrontpages }
        val ordered = order.distinct().mapNotNull { name -> available.find { it.name == name } }
        return ordered + available.filterNot { it in ordered }
    }

    fun availableTypes(
        enabledAdditionalFrontpages: Set<String>,
        hasAccount: Boolean,
        frontpageOrder: List<String> = emptyList(),
        bookmarksEnabled: Boolean = true,
    ): List<StoryType> {
        val pages = frontpages(enabledAdditionalFrontpages) + personalPages
        val ordered = sanitizeOrder(frontpageOrder).mapNotNull { name -> pages.find { it.name == name } }
        return (ordered + pages.filterNot { it in ordered }).filter {
            (!it.isUserItemList || hasAccount) && (!it.isBookmarks || bookmarksEnabled)
        }
    }

    /** Reorder visible slots without moving or forgetting temporarily hidden pages. */
    fun mergeVisibleOrder(
        enabledAdditionalFrontpages: Set<String>,
        storedOrder: List<String>,
        visibleOrder: List<StoryType>,
    ): List<String> {
        val current = availableTypes(enabledAdditionalFrontpages, hasAccount = true, storedOrder)
        val reordered = visibleOrder.distinct().filter { it != StoryType.UNKNOWN }
        val replacements = reordered.filter { it in current }.iterator()
        return (current.map { if (it in reordered) replacements.next() else it } +
            reordered.filterNot { it in current }).map { it.name }
    }

    fun preferred(label: CharSequence?, availableTypes: List<StoryType>): StoryType =
        StoryType.fromLabel(label)
            .takeIf { it != StoryType.UNKNOWN && it in availableTypes }
            ?: StoryType.TOP_STORIES
}

/** Shared settings catalogs; platforms localize labels later without recreating ordering policy. */
object StoryTypeSettingsPolicy {
    private val baseStartingPages = listOf(
        StoryType.TOP_STORIES,
        StoryType.LAST_24_HOURS,
        StoryType.LAST_48_HOURS,
        StoryType.LAST_WEEK,
        StoryType.NEW_STORIES,
        StoryType.BEST_STORIES,
        StoryType.ASK_HN,
        StoryType.SHOW_HN,
        StoryType.HN_JOBS,
    )

    fun startingPageLabels(enabledFrontpages: Set<String>?): List<String> = buildList {
        baseStartingPages.mapTo(this) { it.label }
        StoryType.additionalFrontpages
            .filter { it.label in enabledFrontpages.orEmpty() }
            .mapTo(this) { it.label }
    }

    val additionalFrontpageLabels: List<String>
        get() = StoryType.additionalFrontpages.map { it.label }
}
