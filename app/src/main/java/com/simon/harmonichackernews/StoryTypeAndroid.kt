package com.simon.harmonichackernews

import android.content.Context
import android.content.res.Resources
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.AndroidUserSettings
import kotlin.math.min

object StoryTypeAndroid {
    fun buildStoryTypeLabels(
        resources: Resources,
        context: Context?,
        showUserItemLists: Boolean,
    ): ArrayList<CharSequence> {
        val sortingOptions = resources.getStringArray(R.array.sorting_options)
        val labels = sortingOptions.mapTo(ArrayList<CharSequence>(sortingOptions.size)) { it }
        var insertionIndex = labels.indexOfFirst { it.contentEquals(StoryType.BOOKMARKS.label) }
        if (insertionIndex < 0) {
            insertionIndex = min(
                AndroidSettingsResources.indexOfLabel(resources, "HN Jobs", 2) + 1,
                labels.size,
            )
        }
        val enabledFrontpages = context?.let {
            AndroidUserSettings.get(it).story.additionalFrontpages
        }
        StoryType.additionalFrontpages.forEach { type ->
            val enabled = type.label in enabledFrontpages.orEmpty()
            if (enabled) labels.add(insertionIndex++, type.label)
        }
        if (showUserItemLists) {
            val bookmarksIndex = labels.indexOfFirst {
                it.contentEquals(StoryType.BOOKMARKS.label)
            }
            val favoritesIndex = if (bookmarksIndex >= 0) bookmarksIndex + 1 else min(
                AndroidSettingsResources.indexOfLabel(resources, "Bookmarks", 1) + 1,
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
        AndroidUserSettings.get(context).story.additionalFrontpages,
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
