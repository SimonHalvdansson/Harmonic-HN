package com.simon.harmonichackernews

import android.content.Context
import android.content.res.Resources
import com.simon.harmonichackernews.utils.SettingsUtils
import kotlin.math.min

object StoryTypeAndroid {
    fun buildAdapterLabels(
        resources: Resources,
        context: Context?,
        showUserItemLists: Boolean,
    ): ArrayList<CharSequence> {
        val sortingOptions = resources.getStringArray(R.array.sorting_options)
        val labels = sortingOptions.mapTo(ArrayList<CharSequence>(sortingOptions.size)) { it }
        var insertionIndex = labels.indexOfFirst { it.contentEquals(StoryType.BOOKMARKS.label) }
        if (insertionIndex < 0) {
            insertionIndex = min(SettingsUtils.getJobsIndex(resources) + 1, labels.size)
        }
        StoryType.additionalFrontpages.forEach { type ->
            val enabled = context != null && SettingsUtils.isAdditionalFrontpageEnabled(
                context,
                type.label,
            )
            if (enabled) labels.add(insertionIndex++, type.label)
        }
        if (showUserItemLists) {
            val bookmarksIndex = labels.indexOfFirst {
                it.contentEquals(StoryType.BOOKMARKS.label)
            }
            val favoritesIndex = if (bookmarksIndex >= 0) bookmarksIndex + 1 else min(
                SettingsUtils.getBookmarksIndex(resources) + 1,
                labels.size,
            )
            labels.add(favoritesIndex, StoryType.FAVORITES.label)
            labels.add(StoryType.UPVOTED.label)
        }
        return labels
    }

    fun buildStartingPageLabels(
        resources: Resources,
        context: Context,
    ): ArrayList<CharSequence> = buildStartingPageLabels(
        resources,
        SettingsUtils.getEnabledAdditionalFrontpages(context),
    )

    fun buildStartingPageLabels(
        resources: Resources,
        enabledFrontpages: Set<String>?,
    ): ArrayList<CharSequence> {
        val options = resources.getStringArray(R.array.starting_page_options)
        val labels = options.mapTo(ArrayList<CharSequence>(options.size)) { it }
        StoryType.additionalFrontpages.forEach { type ->
            if (type.label in enabledFrontpages.orEmpty()) labels.add(type.label)
        }
        return labels
    }
}
