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

    fun applyPreview(
        story: Story?,
        sourceUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        tintColor: Int,
    ): Boolean {
        if (story == null || sourceUrl.isNullOrEmpty() || story.previewImageUrl != sourceUrl) {
            return false
        }
        story.previewImageTintColor = tintColor
        story.previewImageTintColorLoaded = true
        story.previewImageTintSourceUrl = sourceUrl
        story.previewImageTintBaseColor = baseColor
        story.previewImageTintMode = storedMode(paletteTintMode)
        return true
    }

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

    fun clearPreview(story: Story?) {
        if (story == null) return
        story.previewImageTintColorLoaded = false
        story.previewImageTintSourceUrl = null
        story.previewImageTintBaseColor = 0
        story.previewImageTintMode = null
    }

    fun isModeCurrent(storedModeValue: String?, paletteTintMode: String?): Boolean =
        storedModeValue == storedMode(paletteTintMode)

    fun storedMode(paletteTintMode: String?): String =
        PreviewTintPolicy.storedMode(paletteTintMode)
}
