package com.simon.harmonichackernews.adapters

import com.simon.harmonichackernews.settings.StoryPreferences
import com.simon.harmonichackernews.settings.TextPreferences

interface StoryDisplayState {
    var showPoints: Boolean
    var compactPoints: Boolean
    var includeTopLevelDomain: Boolean
    var showCommentsCount: Boolean
    var compactView: Boolean
    var thumbnails: Boolean
    var previewImageMode: String
    var borderlessLargePreviewImage: Boolean
    var showSummary: Boolean
    var storyTextSize: Float
    var showIndex: Boolean
    var compactHeader: Boolean
    var leftAlign: Boolean
    var cardStyle: Boolean
    var tintCardUsingPreview: Boolean
    var paletteTintMode: String
    var grayOutClicked: Boolean
    var hotness: Int
    var faviconProvider: String
    var font: String
    var commentTextSize: Float
    fun invalidateTypography()
}

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

    fun applyToState(state: StoryDisplayState): UpdateResult {
        var itemsChanged = false
        var requiresRebuild = false
        var previewImageModeChanged = false
        var fontChanged = false
        var compactHeaderChanged = false

        fun update(changed: Boolean, apply: () -> Unit) {
            if (changed) {
                apply()
                itemsChanged = true
            }
        }
        update(state.showPoints != showPoints) { state.showPoints = showPoints }
        update(state.compactPoints != compactPoints) { state.compactPoints = compactPoints }
        update(state.includeTopLevelDomain != includeTopLevelDomain) {
            state.includeTopLevelDomain = includeTopLevelDomain
        }
        update(state.showCommentsCount != showCommentsCount) {
            state.showCommentsCount = showCommentsCount
        }
        update(state.compactView != compactView) { state.compactView = compactView }
        update(state.thumbnails != thumbnails) { state.thumbnails = thumbnails }
        update(state.previewImageMode != previewImageMode) {
            state.previewImageMode = previewImageMode
            previewImageModeChanged = true
        }
        update(state.borderlessLargePreviewImage != borderlessLargePreviewImage) {
            state.borderlessLargePreviewImage = borderlessLargePreviewImage
        }
        update(state.showSummary != showSummary) { state.showSummary = showSummary }
        update(state.storyTextSize != storyTextSize) {
            state.storyTextSize = storyTextSize
            state.invalidateTypography()
        }
        update(state.commentTextSize != commentTextSize) {
            state.commentTextSize = commentTextSize
            state.invalidateTypography()
        }
        update(state.showIndex != showIndex) { state.showIndex = showIndex }
        if (state.leftAlign != leftAlign) {
            state.leftAlign = leftAlign
            requiresRebuild = true
        }
        if (state.cardStyle != cardStyle) {
            state.cardStyle = cardStyle
            requiresRebuild = true
        }
        if (state.tintCardUsingPreview != tintCardUsingPreview) {
            val shellChanged = !state.cardStyle
            state.tintCardUsingPreview = tintCardUsingPreview
            if (shellChanged) requiresRebuild = true else itemsChanged = true
        }
        update(state.paletteTintMode != paletteTintMode) { state.paletteTintMode = paletteTintMode }
        update(state.grayOutClicked != grayOutClicked) { state.grayOutClicked = grayOutClicked }
        if (state.font != font) {
            state.font = font
            state.invalidateTypography()
            fontChanged = true
            itemsChanged = true
        }
        if (state.compactHeader != compactHeader) {
            state.compactHeader = compactHeader
            compactHeaderChanged = true
        }
        update(state.hotness != hotness) { state.hotness = hotness }
        update(state.faviconProvider != faviconProvider) { state.faviconProvider = faviconProvider }

        return UpdateResult(
            itemsChanged,
            requiresRebuild,
            previewImageModeChanged,
            fontChanged,
            compactHeaderChanged,
        )
    }

    data class UpdateResult(
        val itemsChanged: Boolean,
        val requiresRebuild: Boolean,
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

        fun copyStateSettings(source: StoryDisplayState, target: StoryDisplayState) {
            target.showPoints = source.showPoints
            target.compactPoints = source.compactPoints
            target.includeTopLevelDomain = source.includeTopLevelDomain
            target.showCommentsCount = source.showCommentsCount
            target.compactView = source.compactView
            target.thumbnails = source.thumbnails
            target.previewImageMode = source.previewImageMode
            target.borderlessLargePreviewImage = source.borderlessLargePreviewImage
            target.showSummary = source.showSummary
            target.storyTextSize = source.storyTextSize
            target.showIndex = source.showIndex
            target.compactHeader = source.compactHeader
            target.leftAlign = source.leftAlign
            target.cardStyle = source.cardStyle
            target.tintCardUsingPreview = source.tintCardUsingPreview
            target.paletteTintMode = source.paletteTintMode
            target.grayOutClicked = source.grayOutClicked
            target.hotness = source.hotness
            target.faviconProvider = source.faviconProvider
            target.font = source.font
            target.commentTextSize = source.commentTextSize
            target.invalidateTypography()
        }
    }
}
