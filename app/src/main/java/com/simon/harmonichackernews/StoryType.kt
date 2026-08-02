package com.simon.harmonichackernews

import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.Arrays
import kotlin.math.min

enum class StoryType @JvmOverloads constructor(
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
        get() = this == StoryType.LAST_24_HOURS || this == StoryType.LAST_48_HOURS || this == StoryType.LAST_WEEK

    val isActive: Boolean
        get() = this == StoryType.ACTIVE

    val isFront: Boolean
        get() = this == StoryType.FRONT

    val isBookmarks: Boolean
        get() = this == StoryType.BOOKMARKS

    val isHistory: Boolean
        get() = this == StoryType.HISTORY

    val isFavorites: Boolean
        get() = this == StoryType.FAVORITES

    val isUpvoted: Boolean
        get() = this == StoryType.UPVOTED

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
        get() {
            when (this) {
                StoryType.TOP_STORIES -> return Utils.URL_TOP
                StoryType.NEW_STORIES -> return Utils.URL_NEW
                StoryType.BEST_STORIES -> return Utils.URL_BEST
                StoryType.ASK_HN -> return Utils.URL_ASK
                StoryType.SHOW_HN -> return Utils.URL_SHOW
                StoryType.HN_JOBS -> return Utils.URL_JOBS
                else -> return null
            }
        }

    companion object {
        fun buildAdapterLabels(
            resources: Resources,
            ctx: Context?,
            showUserItemLists: Boolean
        ): ArrayList<CharSequence> {
            val sortingOptions = resources.getStringArray(R.array.sorting_options)
            val labels = ArrayList<CharSequence>(Arrays.asList(*sortingOptions))
            var additionalFrontpageIndex: Int = getLabelIndex(labels, StoryType.BOOKMARKS.label)
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
                val bookmarksIndex: Int = getLabelIndex(labels, StoryType.BOOKMARKS.label)
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
            val labels = ArrayList<CharSequence>(Arrays.asList(*startingPageOptions))
            for (type in additionalFrontpages) {
                if (enabledFrontpages != null && enabledFrontpages.contains(type.label)) {
                    labels.add(type.label)
                }
            }
            return labels
        }

        private val additionalFrontpages: Array<StoryType>
            get() = arrayOf<StoryType>(
                StoryType.CLASSIC,
                StoryType.BEST_COMMENTS,
                StoryType.HIGHLIGHTS,
                StoryType.ACTIVE,
                StoryType.FRONT
            )

        private fun getLabelIndex(labels: ArrayList<CharSequence>, label: String): Int {
            for (i in labels.indices) {
                if (TextUtils.equals(labels.get(i), label)) {
                    return i
                }
            }
            return -1
        }

        fun fromLabel(label: CharSequence?): StoryType {
            if (label == null) {
                return StoryType.UNKNOWN
            }

            for (type in StoryType.entries) {
                if (TextUtils.equals(type.label, label)) {
                    return type
                }
            }
            return StoryType.UNKNOWN
        }
    }
}
