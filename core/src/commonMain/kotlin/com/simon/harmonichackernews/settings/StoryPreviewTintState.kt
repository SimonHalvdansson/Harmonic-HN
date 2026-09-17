package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.Story

/** Platform-neutral mutation and validation for preview-image and favicon palette state. */
object StoryPreviewTintState {
    fun isPreviewCurrent(
        story: Story?,
        baseColor: Int,
        paletteTintMode: String?,
    ): Boolean = story != null &&
        story.previewImageTintColorLoaded &&
        story.previewImageTintBaseColor == baseColor &&
        isModeCurrent(story.previewImageTintMode, paletteTintMode) &&
        story.previewImageTintSourceUrl == story.previewImageUrl

    fun isFaviconCurrent(
        story: Story?,
        sourceUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
    ): Boolean = story != null &&
        story.faviconTintColorLoaded &&
        story.faviconTintBaseColor == baseColor &&
        isModeCurrent(story.faviconTintMode, paletteTintMode) &&
        story.faviconTintSourceUrl == sourceUrl

    fun applyFavicon(
        story: Story?,
        sourceUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        tintColor: Int,
    ): Boolean {
        if (story == null || sourceUrl.isNullOrEmpty()) return false
        story.faviconTintColor = tintColor
        story.faviconTintColorLoaded = true
        story.faviconTintSourceUrl = sourceUrl
        story.faviconTintBaseColor = baseColor
        story.faviconTintMode = storedMode(paletteTintMode)
        return true
    }

    fun isModeCurrent(storedModeValue: String?, paletteTintMode: String?): Boolean =
        storedModeValue == storedMode(paletteTintMode)

    fun storedMode(paletteTintMode: String?): String =
        PreviewTintPolicy.storedMode(paletteTintMode)
}
