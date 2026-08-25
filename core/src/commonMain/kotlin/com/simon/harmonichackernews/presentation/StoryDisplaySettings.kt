package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.StoryPreferences
import com.simon.harmonichackernews.settings.TextPreferences

data class StoryDisplaySettings(
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showCommentsCount: Boolean,
    val compactView: Boolean,
    val thumbnails: Boolean,
    val previewImageMode: String,
    val borderlessLargePreviewImage: Boolean,
    val showSummary: Boolean,
    val storyTextSize: Float,
    val showIndex: Boolean,
    val compactHeader: Boolean,
    val leftAlign: Boolean,
    val cardStyle: Boolean,
    val tintCardUsingPreview: Boolean,
    val paletteTintMode: String,
    val grayOutClicked: Boolean,
    val hotness: Int,
    val faviconProvider: String,
    val font: String,
    val commentTextSize: Float,
) {
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
            thumbnails = preferences.thumbnails,
            previewImageMode = preferences.previewImageMode,
            borderlessLargePreviewImage = preferences.borderlessLargePreviewImage,
            showSummary = preferences.showSummary,
            storyTextSize = TextPreferences.clampStoryTextSize(preferences.storyTextSize),
            showIndex = preferences.showIndex,
            compactHeader = preferences.compactHeader,
            leftAlign = preferences.leftAlign,
            cardStyle = preferences.cardStyle,
            tintCardUsingPreview = preferences.tintCardUsingPreview,
            paletteTintMode = preferences.paletteTintConfigKey,
            grayOutClicked = preferences.grayOutClicked,
            hotness = preferences.hotness,
            faviconProvider = preferences.faviconProvider,
            font = TextPreferences.sanitizeFont(preferences.font),
            commentTextSize = TextPreferences.clampCommentTextSize(preferences.commentTextSize),
        )

    }
}
