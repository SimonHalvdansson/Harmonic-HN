package com.simon.harmonichackernews.adapters

import android.content.Context
import androidx.annotation.NonNull
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils

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
    val paletteTintMode: String
    val preferredTextSize: Float
    val commentDepthIndicatorMode: String

    init {
        this.paletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
        this.preferredTextSize = SettingsUtils.clampCommentTextSize(preferredTextSize)
        this.commentDepthIndicatorMode =
            CommentDepthIndicatorUtils.sanitizeMode(commentDepthIndicatorMode)
    }

    companion object {
        fun from(
            context: Context,
            showInvert: Boolean,
            isTablet: Boolean,
            hasAccountDetails: Boolean,
            canProvideSummary: Boolean
        ): CommentDisplaySettings {
            return CommentDisplaySettings(
                SettingsUtils.shouldCollapseParent(context),
                SettingsUtils.shouldShowThumbnails(context),
                SettingsUtils.shouldShowCommentsHeaderPreviewImage(context),
                SettingsUtils.shouldTintCommentsHeader(context),
                SettingsUtils.getPreferredPaletteTintConfigKey(context),
                SettingsUtils.getPreferredCommentTextSize(context),
                SettingsUtils.getPreferredCommentDepthIndicatorMode(context).orEmpty(),
                SettingsUtils.shouldShowNavigationButtons(context),
                SettingsUtils.getPreferredFont(context).orEmpty(),
                showInvert,
                SettingsUtils.shouldShowTopLevelDepthIndicator(context),
                ThemeUtils.getPreferredTheme(context),
                isTablet,
                SettingsUtils.getPreferredFaviconProvider(context),
                SettingsUtils.shouldSwapCommentLongPressTap(context),
                SettingsUtils.shouldUseCardCommentDisplayStyle(context),
                SettingsUtils.shouldShowCommentCardBorder(context),
                SettingsUtils.shouldShowCommentDividers(context),
                SettingsUtils.shouldHighlightCommentMeta(context),
                SettingsUtils.shouldCollectLinksInComments(context),
                hasAccountDetails,
                canProvideSummary
            )
        }
    }
}
