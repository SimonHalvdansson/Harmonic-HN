package com.simon.harmonichackernews.adapters

import com.simon.harmonichackernews.settings.CommentPreferences
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.SettingsUtils

class CommentDisplaySettings private constructor(
    val collapseParent: Boolean,
    val showThumbnail: Boolean,
    val showHeaderPreviewImage: Boolean,
    val tintHeader: Boolean,
    paletteTintMode: String?,
    preferredTextSize: Float,
    commentDepthIndicatorMode: String,
    val showNavigationBar: Boolean,
    val font: String,
    val showInvert: Boolean,
    val showTopLevelDepthIndicator: Boolean,
    val theme: String?,
    val isTablet: Boolean,
    val faviconProvider: String?,
    val swapLongPressTap: Boolean,
    val cardStyle: Boolean,
    val cardBorder: Boolean,
    val showDividers: Boolean,
    val highlightCommentMeta: Boolean,
    val collectReferenceLinks: Boolean,
    val hasAccountDetails: Boolean,
    val canProvideSummary: Boolean
) {
    val paletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
    val preferredTextSize = SettingsUtils.clampCommentTextSize(preferredTextSize)
    val commentDepthIndicatorMode = CommentDepthIndicatorUtils.sanitizeMode(
        commentDepthIndicatorMode
    )

    companion object {
        fun from(
            preferences: CommentPreferences,
            showInvert: Boolean,
            isTablet: Boolean,
            hasAccountDetails: Boolean,
            canProvideSummary: Boolean
        ): CommentDisplaySettings {
            return CommentDisplaySettings(
                preferences.collapseParent,
                preferences.thumbnails,
                preferences.showHeaderPreviewImage,
                preferences.tintHeader,
                preferences.paletteTintConfigKey,
                preferences.textSize,
                preferences.depthIndicatorMode,
                preferences.showNavigationButtons,
                preferences.font,
                showInvert,
                preferences.showTopLevelDepthIndicator,
                preferences.theme,
                isTablet,
                preferences.faviconProvider,
                preferences.swapLongPressTap,
                preferences.cardStyle,
                preferences.cardBorder,
                preferences.showDividers,
                preferences.highlightMetadata,
                preferences.collectReferenceLinks,
                hasAccountDetails,
                canProvideSummary
            )
        }
    }
}
