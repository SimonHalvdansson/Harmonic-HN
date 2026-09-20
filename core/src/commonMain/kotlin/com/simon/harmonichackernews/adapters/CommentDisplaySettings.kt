package com.simon.harmonichackernews.adapters

import com.simon.harmonichackernews.settings.UserAvatarOptions

import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.CommentIndicatorThickness
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
    val displayStyle: DisplayStyle,
    val showDividers: Boolean,
    val highlightCommentMeta: Boolean,
    val collectReferenceLinks: Boolean,
    val hasAccountDetails: Boolean,
    val canProvideSummary: Boolean,
    val showAdditionalSummaryInfo: Boolean,
    val enableSummaryBoldFormatting: Boolean,
    val indicatorThickness: CommentIndicatorThickness = CommentIndicatorThickness.STANDARD,
    val roundedDepthIndicators: Boolean = false,
    val continuousDepthIndicators: Boolean = false,
    val userAvatarsEnabled: Boolean = false,
    val userAvatarOptions: UserAvatarOptions = UserAvatarOptions(),
) {
    val cardStyle: Boolean get() = displayStyle == DisplayStyle.RAISED || displayStyle == DisplayStyle.OUTLINED
    val hasBackground: Boolean get() = displayStyle != DisplayStyle.FLAT

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
            indicatorThickness = preferences.indicatorThickness,
            roundedDepthIndicators = preferences.roundedDepthIndicators,
            continuousDepthIndicators = preferences.continuousDepthIndicators &&
                preferences.depthIndicatorMode != CommentDepthPreferences.AUTHOR,
            userAvatarsEnabled = preferences.userAvatarsEnabled,
            userAvatarOptions = preferences.userAvatarOptions,
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
            displayStyle = preferences.displayStyle,
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
