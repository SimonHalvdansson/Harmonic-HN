package com.simon.harmonichackernews.adapters;

import android.content.Context;
import androidx.annotation.NonNull;

import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class CommentDisplaySettings {
    public final boolean collapseParent;
    public final boolean showThumbnail;
    public final boolean showHeaderPreviewImage;
    public final boolean tintHeader;
    public final String paletteTintMode;
    public final float preferredTextSize;
    public final String commentDepthIndicatorMode;
    public final boolean showNavigationBar;
    public final String font;
    public final boolean showInvert;
    public final boolean showTopLevelDepthIndicator;
    public final String theme;
    public final boolean isTablet;
    public final String faviconProvider;
    public final boolean swapLongPressTap;
    public final boolean cardStyle;
    public final boolean cardBorder;
    public final boolean showDividers;
    public final boolean highlightCommentMeta;
    public final boolean collectReferenceLinks;
    public final boolean hasAccountDetails;
    public final boolean canProvideSummary;

    private CommentDisplaySettings(boolean collapseParent,
                                   boolean showThumbnail,
                                   boolean showHeaderPreviewImage,
                                   boolean tintHeader,
                                   String paletteTintMode,
                                   float preferredTextSize,
                                   String commentDepthIndicatorMode,
                                   boolean showNavigationBar,
                                   String font,
                                   boolean showInvert,
                                   boolean showTopLevelDepthIndicator,
                                   String theme,
                                   boolean isTablet,
                                   String faviconProvider,
                                   boolean swapLongPressTap,
                                   boolean cardStyle,
                                   boolean cardBorder,
                                   boolean showDividers,
                                   boolean highlightCommentMeta,
                                   boolean collectReferenceLinks,
                                   boolean hasAccountDetails,
                                   boolean canProvideSummary) {
        this.collapseParent = collapseParent;
        this.showThumbnail = showThumbnail;
        this.showHeaderPreviewImage = showHeaderPreviewImage;
        this.tintHeader = tintHeader;
        this.paletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        this.preferredTextSize = SettingsUtils.clampCommentTextSize(preferredTextSize);
        this.commentDepthIndicatorMode = CommentDepthIndicatorUtils.sanitizeMode(commentDepthIndicatorMode);
        this.showNavigationBar = showNavigationBar;
        this.font = font;
        this.showInvert = showInvert;
        this.showTopLevelDepthIndicator = showTopLevelDepthIndicator;
        this.theme = theme;
        this.isTablet = isTablet;
        this.faviconProvider = faviconProvider;
        this.swapLongPressTap = swapLongPressTap;
        this.cardStyle = cardStyle;
        this.cardBorder = cardBorder;
        this.showDividers = showDividers;
        this.highlightCommentMeta = highlightCommentMeta;
        this.collectReferenceLinks = collectReferenceLinks;
        this.hasAccountDetails = hasAccountDetails;
        this.canProvideSummary = canProvideSummary;
    }

    @NonNull
    public static CommentDisplaySettings from(@NonNull Context context,
                                              boolean showInvert,
                                              boolean isTablet,
                                              boolean hasAccountDetails,
                                              boolean canProvideSummary) {
        return new CommentDisplaySettings(
                SettingsUtils.shouldCollapseParent(context),
                SettingsUtils.shouldShowThumbnails(context),
                SettingsUtils.shouldShowCommentsHeaderPreviewImage(context),
                SettingsUtils.shouldTintCommentsHeader(context),
                SettingsUtils.getPreferredPaletteTintConfigKey(context),
                SettingsUtils.getPreferredCommentTextSize(context),
                SettingsUtils.getPreferredCommentDepthIndicatorMode(context),
                SettingsUtils.shouldShowNavigationButtons(context),
                SettingsUtils.getPreferredFont(context),
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
        );
    }

}
