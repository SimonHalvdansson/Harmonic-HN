package com.simon.harmonichackernews.adapters;

import android.content.Context;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;

import java.util.List;
import java.util.Objects;

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
                                   boolean hasAccountDetails) {
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
    }

    @NonNull
    public static CommentDisplaySettings from(@NonNull Context context,
                                              boolean showInvert,
                                              boolean isTablet,
                                              boolean hasAccountDetails) {
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
                hasAccountDetails
        );
    }

    @NonNull
    public CommentsRecyclerViewAdapter createAdapter(boolean integratedWebview,
                                                     LinearLayout bottomSheet,
                                                     FragmentManager fragmentManager,
                                                     List<Comment> comments,
                                                     Story story,
                                                     String username,
                                                     CommentsRecyclerViewAdapter.RequestSummaryCallback summaryCallback) {
        return new CommentsRecyclerViewAdapter(
                integratedWebview,
                bottomSheet,
                fragmentManager,
                comments,
                story,
                username,
                this,
                summaryCallback
        );
    }

    @NonNull
    public UpdateResult applyToAdapter(@NonNull CommentsRecyclerViewAdapter adapter) {
        boolean updateHeader = false;
        boolean updateComments = false;
        boolean themeChanged = false;

        if (adapter.collapseParent != collapseParent) {
            adapter.collapseParent = collapseParent;
            updateComments = true;
        }
        if (adapter.showThumbnail != showThumbnail) {
            adapter.showThumbnail = showThumbnail;
            updateHeader = true;
        }
        if (adapter.showHeaderPreviewImage != showHeaderPreviewImage) {
            adapter.showHeaderPreviewImage = showHeaderPreviewImage;
            updateHeader = true;
        }
        if (adapter.tintHeader != tintHeader) {
            adapter.tintHeader = tintHeader;
            updateHeader = true;
        }
        if (!Objects.equals(adapter.paletteTintMode, paletteTintMode)) {
            adapter.paletteTintMode = paletteTintMode;
            updateHeader = true;
        }
        if (Float.compare(adapter.preferredTextSize, preferredTextSize) != 0) {
            adapter.preferredTextSize = preferredTextSize;
            updateHeader = true;
            updateComments = true;
        }
        if (!Objects.equals(adapter.commentDepthIndicatorMode, commentDepthIndicatorMode)) {
            adapter.commentDepthIndicatorMode = commentDepthIndicatorMode;
            updateComments = true;
        }
        if (adapter.showNavigationBar != showNavigationBar) {
            adapter.showNavigationBar = showNavigationBar;
            updateHeader = true;
        }
        if (!Objects.equals(adapter.font, font)) {
            adapter.font = font;
            updateHeader = true;
            updateComments = true;
        }
        if (adapter.showInvert != showInvert) {
            adapter.showInvert = showInvert;
            updateHeader = true;
        }
        if (adapter.showTopLevelDepthIndicator != showTopLevelDepthIndicator) {
            adapter.showTopLevelDepthIndicator = showTopLevelDepthIndicator;
            updateComments = true;
        }
        if (!Objects.equals(adapter.theme, theme)) {
            adapter.theme = theme;
            updateHeader = true;
            updateComments = true;
            themeChanged = true;
        }
        if (!Objects.equals(adapter.faviconProvider, faviconProvider)) {
            adapter.faviconProvider = faviconProvider;
            updateHeader = true;
            updateComments = true;
        }
        if (adapter.swapLongPressTap != swapLongPressTap) {
            adapter.swapLongPressTap = swapLongPressTap;
        }
        if (adapter.cardStyle != cardStyle) {
            adapter.cardStyle = cardStyle;
            updateComments = true;
        }
        if (adapter.cardBorder != cardBorder) {
            adapter.cardBorder = cardBorder;
            updateComments = true;
        }
        if (adapter.showDividers != showDividers) {
            adapter.showDividers = showDividers;
            updateComments = true;
        }
        if (adapter.highlightCommentMeta != highlightCommentMeta) {
            adapter.highlightCommentMeta = highlightCommentMeta;
            updateComments = true;
        }
        if (adapter.collectReferenceLinks != collectReferenceLinks) {
            adapter.collectReferenceLinks = collectReferenceLinks;
            updateHeader = true;
            updateComments = true;
        }
        if (adapter.hasAccountDetails != hasAccountDetails) {
            adapter.hasAccountDetails = hasAccountDetails;
            updateHeader = true;
        }

        return new UpdateResult(updateHeader, updateComments, themeChanged);
    }

    public static class UpdateResult {
        public final boolean updateHeader;
        public final boolean updateComments;
        public final boolean themeChanged;

        private UpdateResult(boolean updateHeader, boolean updateComments, boolean themeChanged) {
            this.updateHeader = updateHeader;
            this.updateComments = updateComments;
            this.themeChanged = themeChanged;
        }
    }
}
