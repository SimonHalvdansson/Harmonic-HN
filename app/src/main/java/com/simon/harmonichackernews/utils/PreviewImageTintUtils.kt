package com.simon.harmonichackernews.utils

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.cachePreviewImageTintColor
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.clearCachedPreviewImageTintColors
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewTintState

/** Android cache/theme adapter around the shared palette extraction and tint-state policy. */
object PreviewImageTintUtils {
    fun getTintBaseColor(context: Context): Int = MaterialColors.getColor(
        context,
        R.attr.storyCardBackgroundColor,
        MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            Color.TRANSPARENT,
        ),
    )

    fun clearTintColorCaches(context: Context?) {
        clearCachedPreviewImageTintColors(context)
    }

    fun applyCachedStoryPreviewImageTintColor(
        story: Story?,
        imageUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        tintColor: Int,
    ): Boolean {
        if (story == null || imageUrl.isNullOrEmpty()) return false
        cachePreviewImageTintColor(story.id, imageUrl, baseColor, tintColor)
        return StoryPreviewTintState.applyPreview(
            story,
            imageUrl,
            baseColor,
            paletteTintMode,
            tintColor,
        )
    }

    fun isStoryPreviewImageTintColorCurrent(
        story: Story?,
        baseColor: Int,
        paletteTintMode: String? = PaletteTintPreferences.DEFAULT,
    ): Boolean = StoryPreviewTintState.isPreviewCurrent(story, baseColor, paletteTintMode)

    fun isTintModeCurrent(storedMode: String?, paletteTintMode: String?): Boolean =
        StoryPreviewTintState.isModeCurrent(storedMode, paletteTintMode)

    fun storedTintMode(paletteTintMode: String?): String =
        StoryPreviewTintState.storedMode(paletteTintMode)

    fun clearStoryPreviewImageTintColor(story: Story?) {
        StoryPreviewTintState.clearPreview(story)
    }
}
