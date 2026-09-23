package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreferences
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.TextPreferences

data class StoryDisplaySettings(
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showCommentsCount: Boolean,
    val compactView: Boolean,
    val showFavicons: Boolean,
    val previewImageMode: StoryPreviewMode,
    val borderlessLargePreviewImage: Boolean,
    val showPreviewText: Boolean,
    val storyTextSize: Float,
    val showIndex: Boolean,
    val compactHeader: Boolean,
    val commentsButtonOnLeft: Boolean,
    val displayStyle: DisplayStyle,
    val tintCardsFromImages: Boolean,
    val paletteTintMode: String,
    val dimReadStories: Boolean,
    val hotnessThreshold: Int,
    val faviconProvider: String,
    val font: String,
    val commentTextSize: Float,
    val listSelector: StoryListSelector = StoryListSelector.DROPDOWN,
) {
    val outline: Boolean get() = displayStyle == DisplayStyle.OUTLINED
    val cardStyle: Boolean get() = displayStyle == DisplayStyle.RAISED || displayStyle == DisplayStyle.OUTLINED
    val hasBackground: Boolean get() = displayStyle != DisplayStyle.FLAT

    fun withShowIndex(showIndex: Boolean): StoryDisplaySettings = copy(showIndex = showIndex)

    fun changesFrom(previous: StoryDisplaySettings): UpdateResult = UpdateResult(
        itemsChanged = this != previous,
        previewImageModeChanged = previewImageMode != previous.previewImageMode,
        fontChanged = font != previous.font,
        compactHeaderChanged = compactHeader != previous.compactHeader,
    )

    data class UpdateResult(
        val itemsChanged: Boolean,
        val previewImageModeChanged: Boolean,
        val fontChanged: Boolean,
        val compactHeaderChanged: Boolean,
    )

    companion object {
        fun from(preferences: StoryPreferences): StoryDisplaySettings = StoryDisplaySettings(
            showPoints = preferences.showPoints,
            compactPoints = preferences.compactPoints,
            includeTopLevelDomain = preferences.includeTopLevelDomain,
            showCommentsCount = preferences.showCommentsCount,
            compactView = preferences.compactView,
            showFavicons = preferences.showFavicons,
            previewImageMode = preferences.previewImageMode,
            borderlessLargePreviewImage = preferences.borderlessLargePreviewImage,
            showPreviewText = preferences.showPreviewText,
            storyTextSize = TextPreferences.clampStoryTextSize(preferences.storyTextSize),
            showIndex = preferences.showIndex,
            compactHeader = preferences.compactHeader,
            listSelector = preferences.listSelector,
            commentsButtonOnLeft = preferences.commentsButtonOnLeft,
            displayStyle = preferences.displayStyle,
            tintCardsFromImages = preferences.tintCardsFromImages,
            paletteTintMode = preferences.paletteTintConfigKey,
            dimReadStories = preferences.dimReadStories,
            hotnessThreshold = preferences.hotnessThreshold,
            faviconProvider = preferences.faviconProvider,
            font = TextPreferences.sanitizeFont(preferences.font),
            commentTextSize = TextPreferences.clampCommentTextSize(preferences.commentTextSize),
        )

    }
}
