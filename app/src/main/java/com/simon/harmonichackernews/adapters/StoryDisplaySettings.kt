package com.simon.harmonichackernews.adapters

import android.content.Context
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.ui.stories.StoryListState
import com.simon.harmonichackernews.utils.SettingsUtils

class StoryDisplaySettings private constructor(
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
    commentTextSize: Float
) {
    val commentTextSize = SettingsUtils.clampCommentTextSize(commentTextSize)

    fun withShowIndex(showIndex: Boolean): StoryDisplaySettings {
        return StoryDisplaySettings(
            showPoints,
            compactPoints,
            includeTopLevelDomain,
            showCommentsCount,
            compactView,
            thumbnails,
            previewImageMode,
            borderlessLargePreviewImage,
            showSummary,
            storyTextSize,
            showIndex,
            compactHeader,
            leftAlign,
            cardStyle,
            tintCardUsingPreview,
            paletteTintMode,
            grayOutClicked,
            hotness,
            faviconProvider,
            font,
            commentTextSize
        )
    }

    fun createListState(stories: MutableList<Story>, wantedType: Int): StoryListState {
        return StoryListState(stories, this, wantedType)
    }

    fun applyToState(adapter: StoryListState): UpdateResult {
        var itemsChanged = false
        var requiresRebuild = false
        var previewImageModeChanged = false
        var fontChanged = false
        var compactHeaderChanged = false

        if (adapter.showPoints != showPoints) {
            adapter.showPoints = showPoints
            itemsChanged = true
        }
        if (adapter.compactPoints != compactPoints) {
            adapter.compactPoints = compactPoints
            itemsChanged = true
        }
        if (adapter.includeTopLevelDomain != includeTopLevelDomain) {
            adapter.includeTopLevelDomain = includeTopLevelDomain
            itemsChanged = true
        }
        if (adapter.showCommentsCount != showCommentsCount) {
            adapter.showCommentsCount = showCommentsCount
            itemsChanged = true
        }
        if (adapter.compactView != compactView) {
            adapter.compactView = compactView
            itemsChanged = true
        }
        if (adapter.thumbnails != thumbnails) {
            adapter.thumbnails = thumbnails
            itemsChanged = true
        }
        if (adapter.previewImageMode != previewImageMode) {
            adapter.previewImageMode = previewImageMode
            previewImageModeChanged = true
            itemsChanged = true
        }
        if (adapter.borderlessLargePreviewImage != borderlessLargePreviewImage) {
            adapter.borderlessLargePreviewImage = borderlessLargePreviewImage
            itemsChanged = true
        }
        if (adapter.showSummary != showSummary) {
            adapter.showSummary = showSummary
            itemsChanged = true
        }
        if (adapter.storyTextSize.compareTo(storyTextSize) != 0) {
            adapter.storyTextSize = storyTextSize
            adapter.invalidateTypography()
            itemsChanged = true
        }
        if (adapter.commentTextSize.compareTo(commentTextSize) != 0) {
            adapter.commentTextSize = commentTextSize
            adapter.invalidateTypography()
            itemsChanged = true
        }
        if (adapter.showIndex != showIndex) {
            adapter.showIndex = showIndex
            itemsChanged = true
        }
        if (adapter.leftAlign != leftAlign) {
            adapter.leftAlign = leftAlign
            requiresRebuild = true
        }
        if (adapter.cardStyle != cardStyle) {
            adapter.cardStyle = cardStyle
            requiresRebuild = true
        }
        if (adapter.tintCardUsingPreview != tintCardUsingPreview) {
            val storyCardShellChanged = !adapter.cardStyle
            adapter.tintCardUsingPreview = tintCardUsingPreview
            if (storyCardShellChanged) {
                requiresRebuild = true
            } else {
                itemsChanged = true
            }
        }
        if (adapter.paletteTintMode != paletteTintMode) {
            adapter.paletteTintMode = paletteTintMode
            itemsChanged = true
        }
        if (adapter.grayOutClicked != grayOutClicked) {
            adapter.grayOutClicked = grayOutClicked
            itemsChanged = true
        }
        if (adapter.font != font) {
            adapter.font = font
            adapter.invalidateTypography()
            fontChanged = true
            itemsChanged = true
        }
        if (adapter.compactHeader != compactHeader) {
            adapter.compactHeader = compactHeader
            compactHeaderChanged = true
        }
        if (adapter.hotness != hotness) {
            adapter.hotness = hotness
            itemsChanged = true
        }
        if (adapter.faviconProvider != faviconProvider) {
            adapter.faviconProvider = faviconProvider
            itemsChanged = true
        }

        return UpdateResult(
            itemsChanged,
            requiresRebuild,
            previewImageModeChanged,
            fontChanged,
            compactHeaderChanged
        )
    }

    data class UpdateResult(
        val itemsChanged: Boolean,
        val requiresRebuild: Boolean,
        val previewImageModeChanged: Boolean,
        val fontChanged: Boolean,
        val compactHeaderChanged: Boolean
    )

    companion object {
        fun from(context: Context): StoryDisplaySettings {
            return StoryDisplaySettings(
                SettingsUtils.shouldShowPoints(context),
                SettingsUtils.shouldUseCompactPoints(context),
                SettingsUtils.shouldIncludeTopLevelDomain(context),
                SettingsUtils.shouldShowCommentsCount(context),
                SettingsUtils.shouldUseCompactView(context),
                SettingsUtils.shouldShowThumbnails(context),
                SettingsUtils.getPreferredStoryPreviewImageMode(context),
                SettingsUtils.shouldUseBorderlessLargeStoryPreviewImage(context),
                SettingsUtils.shouldShowStorySummary(context),
                SettingsUtils.getPreferredStoryTextSize(context),
                SettingsUtils.shouldShowIndex(context),
                SettingsUtils.shouldUseCompactHeader(context),
                SettingsUtils.shouldUseLeftAlign(context),
                SettingsUtils.shouldUseCardStoryDisplayStyle(context),
                SettingsUtils.shouldTintCardUsingPreview(context),
                SettingsUtils.getPreferredPaletteTintConfigKey(context),
                SettingsUtils.shouldGrayOutClicked(context),
                SettingsUtils.getPreferredHotness(context),
                SettingsUtils.getPreferredFaviconProvider(context),
                SettingsUtils.getPreferredFont(context),
                SettingsUtils.getPreferredCommentTextSize(context)
            )
        }

        fun copyStateSettings(
            sourceAdapter: StoryListState,
            targetAdapter: StoryListState
        ) {
            targetAdapter.showPoints = sourceAdapter.showPoints
            targetAdapter.compactPoints = sourceAdapter.compactPoints
            targetAdapter.includeTopLevelDomain = sourceAdapter.includeTopLevelDomain
            targetAdapter.showCommentsCount = sourceAdapter.showCommentsCount
            targetAdapter.compactView = sourceAdapter.compactView
            targetAdapter.thumbnails = sourceAdapter.thumbnails
            targetAdapter.previewImageMode = sourceAdapter.previewImageMode
            targetAdapter.borderlessLargePreviewImage = sourceAdapter.borderlessLargePreviewImage
            targetAdapter.showSummary = sourceAdapter.showSummary
            targetAdapter.storyTextSize = sourceAdapter.storyTextSize
            targetAdapter.showIndex = sourceAdapter.showIndex
            targetAdapter.compactHeader = sourceAdapter.compactHeader
            targetAdapter.leftAlign = sourceAdapter.leftAlign
            targetAdapter.cardStyle = sourceAdapter.cardStyle
            targetAdapter.tintCardUsingPreview = sourceAdapter.tintCardUsingPreview
            targetAdapter.paletteTintMode = sourceAdapter.paletteTintMode
            targetAdapter.grayOutClicked = sourceAdapter.grayOutClicked
            targetAdapter.hotness = sourceAdapter.hotness
            targetAdapter.faviconProvider = sourceAdapter.faviconProvider
            targetAdapter.font = sourceAdapter.font
            targetAdapter.commentTextSize = sourceAdapter.commentTextSize
            targetAdapter.invalidateTypography()
        }
    }
}
