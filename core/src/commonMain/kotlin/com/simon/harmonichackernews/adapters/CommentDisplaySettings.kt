package com.simon.harmonichackernews.adapters

import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.CommentPreferences
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.TextPreferences

data class CommentDisplaySettings(
    val collapseParent: Boolean,
    val showThumbnail: Boolean,
    val showHeaderPreviewImage: Boolean,
    val tintHeader: Boolean,
    val showUpButton: Boolean,
    val paletteTintMode: String,
    val preferredTextSize: Float,
    val commentDepthIndicatorMode: String,
    val showNavigationBar: Boolean,
    val font: String,
    val showInvert: Boolean,
    val showTopLevelDepthIndicator: Boolean,
    val theme: String?,
    val isTablet: Boolean,
    val faviconProvider: String,
    val swapLongPressTap: Boolean,
    val cardStyle: Boolean,
    val cardBorder: Boolean,
    val showDividers: Boolean,
    val highlightCommentMeta: Boolean,
    val collectReferenceLinks: Boolean,
    val hasAccountDetails: Boolean,
    val canProvideSummary: Boolean,
    val showAdditionalSummaryInfo: Boolean,
    val enableSummaryBoldFormatting: Boolean,
) {
    companion object {
        fun from(
            preferences: CommentPreferences,
            showInvert: Boolean,
            isTablet: Boolean,
            hasAccountDetails: Boolean,
            canProvideSummary: Boolean,
            showAdditionalSummaryInfo: Boolean = false,
            enableSummaryBoldFormatting: Boolean = true,
        ): CommentDisplaySettings = CommentDisplaySettings(
            collapseParent = preferences.collapseParent,
            showThumbnail = preferences.thumbnails,
            showHeaderPreviewImage = preferences.showHeaderPreviewImage,
            tintHeader = preferences.tintHeader,
            showUpButton = preferences.showUpButton,
            paletteTintMode = PaletteTintPreferences.normalizeConfigKey(
                preferences.paletteTintConfigKey,
            ),
            preferredTextSize = TextPreferences.clampCommentTextSize(preferences.textSize),
            commentDepthIndicatorMode = CommentDepthPreferences.sanitizeMode(
                preferences.depthIndicatorMode,
            ),
            showNavigationBar = preferences.showNavigationButtons,
            font = TextPreferences.sanitizeFont(preferences.font),
            showInvert = showInvert,
            showTopLevelDepthIndicator = preferences.showTopLevelDepthIndicator,
            theme = preferences.theme,
            isTablet = isTablet,
            faviconProvider = preferences.faviconProvider,
            swapLongPressTap = preferences.swapLongPressTap,
            cardStyle = preferences.cardStyle,
            cardBorder = preferences.cardBorder,
            showDividers = preferences.showDividers,
            highlightCommentMeta = preferences.highlightMetadata,
            collectReferenceLinks = preferences.collectReferenceLinks,
            hasAccountDetails = hasAccountDetails,
            canProvideSummary = canProvideSummary,
            showAdditionalSummaryInfo = showAdditionalSummaryInfo,
            enableSummaryBoldFormatting = enableSummaryBoldFormatting,
        )
    }
}
