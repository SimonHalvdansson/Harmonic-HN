package com.simon.harmonichackernews

import android.content.Context
import android.content.res.Resources
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlin.math.min

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
    CLASSIC(SettingsUtils.FRONT_PAGE_CLASSIC, "classic", true, false, false),
    BEST_COMMENTS(SettingsUtils.FRONT_PAGE_BEST_COMMENTS, "bestcomments", true, true, false),
    HIGHLIGHTS(SettingsUtils.FRONT_PAGE_HIGHLIGHTS, "highlights", true, true, false),
    ACTIVE(SettingsUtils.FRONT_PAGE_ACTIVE, "active", true, false, false),
    FRONT(SettingsUtils.FRONT_PAGE_FRONT, "front", true, false, false),
    BOOKMARKS("Bookmarks"),
    FAVORITES(SettingsUtils.FAVORITES_LABEL),
    UPVOTED(SettingsUtils.UPVOTED_LABEL),
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

    private fun isEnabledAdditionalFrontpage(ctx: Context?): Boolean {
        return !additionalFrontpage || (ctx != null && SettingsUtils.isAdditionalFrontpageEnabled(
            ctx,
            label
        ))
    }

    val hackerNewsUrl: String?
        get() = when (this) {
            TOP_STORIES -> Utils.URL_TOP
            NEW_STORIES -> Utils.URL_NEW
            BEST_STORIES -> Utils.URL_BEST
            ASK_HN -> Utils.URL_ASK
            SHOW_HN -> Utils.URL_SHOW
            HN_JOBS -> Utils.URL_JOBS
            else -> null
        }

    companion object {
        fun buildAdapterLabels(
            resources: Resources,
            ctx: Context?,
            showUserItemLists: Boolean
        ): ArrayList<CharSequence> {
            val sortingOptions = resources.getStringArray(R.array.sorting_options)
            val labels = sortingOptions.mapTo(ArrayList<CharSequence>(sortingOptions.size)) { it }
            var additionalFrontpageIndex = getLabelIndex(labels, BOOKMARKS.label)
            if (additionalFrontpageIndex < 0) {
                additionalFrontpageIndex =
                    min(SettingsUtils.getJobsIndex(resources) + 1, labels.size)
            }
            for (type in additionalFrontpages) {
                if (type.isEnabledAdditionalFrontpage(ctx)) {
                    labels.add(additionalFrontpageIndex, type.label)
                    additionalFrontpageIndex++
                }
            }
            if (showUserItemLists) {
                val bookmarksIndex = getLabelIndex(labels, BOOKMARKS.label)
                val favoritesIndex = if (bookmarksIndex >= 0) bookmarksIndex + 1 else min(
                    SettingsUtils.getBookmarksIndex(resources) + 1, labels.size
                )
                labels.add(favoritesIndex, SettingsUtils.FAVORITES_LABEL)
                labels.add(SettingsUtils.UPVOTED_LABEL)
            }
            return labels
        }

        fun buildStartingPageLabels(resources: Resources, ctx: Context): ArrayList<CharSequence> {
            return buildStartingPageLabels(
                resources,
                SettingsUtils.getEnabledAdditionalFrontpages(ctx)
            )
        }

        fun buildStartingPageLabels(
            resources: Resources,
            enabledFrontpages: Set<String>?
        ): ArrayList<CharSequence> {
            val startingPageOptions = resources.getStringArray(R.array.starting_page_options)
            val labels = startingPageOptions.mapTo(
                ArrayList<CharSequence>(startingPageOptions.size)
            ) { it }
            for (type in additionalFrontpages) {
                if (type.label in enabledFrontpages.orEmpty()) {
                    labels.add(type.label)
                }
            }
            return labels
        }

        private val additionalFrontpages = listOf(
            CLASSIC,
            BEST_COMMENTS,
            HIGHLIGHTS,
            ACTIVE,
            FRONT
        )

        private fun getLabelIndex(labels: List<CharSequence>, label: String): Int =
            labels.indexOfFirst { it.contentEquals(label) }

        fun fromLabel(label: CharSequence?): StoryType {
            return label?.let { value ->
                entries.firstOrNull { type -> type.label.contentEquals(value) }
            } ?: UNKNOWN
        }
    }
}
