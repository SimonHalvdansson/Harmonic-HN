package com.simon.harmonichackernews.adapters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.List;
import java.util.Objects;

public class StoryDisplaySettings {
    public final boolean showPoints;
    public final boolean compactPoints;
    public final boolean includeTopLevelDomain;
    public final boolean showCommentsCount;
    public final boolean compactView;
    public final boolean thumbnails;
    public final String previewImageMode;
    public final float storyTextSize;
    public final boolean showIndex;
    public final boolean compactHeader;
    public final boolean leftAlign;
    public final boolean cardStyle;
    public final boolean tintCardUsingPreview;
    public final String paletteTintMode;
    public final boolean grayOutClicked;
    public final int hotness;
    public final String faviconProvider;
    public final String font;
    public final float commentTextSize;

    private StoryDisplaySettings(boolean showPoints,
                                 boolean compactPoints,
                                 boolean includeTopLevelDomain,
                                 boolean showCommentsCount,
                                 boolean compactView,
                                 boolean thumbnails,
                                 String previewImageMode,
                                 float storyTextSize,
                                 boolean showIndex,
                                 boolean compactHeader,
                                 boolean leftAlign,
                                 boolean cardStyle,
                                 boolean tintCardUsingPreview,
                                 String paletteTintMode,
                                 boolean grayOutClicked,
                                 int hotness,
                                 String faviconProvider,
                                 String font,
                                 float commentTextSize) {
        this.showPoints = showPoints;
        this.compactPoints = compactPoints;
        this.includeTopLevelDomain = includeTopLevelDomain;
        this.showCommentsCount = showCommentsCount;
        this.compactView = compactView;
        this.thumbnails = thumbnails;
        this.previewImageMode = previewImageMode;
        this.storyTextSize = storyTextSize;
        this.showIndex = showIndex;
        this.compactHeader = compactHeader;
        this.leftAlign = leftAlign;
        this.cardStyle = cardStyle;
        this.tintCardUsingPreview = tintCardUsingPreview;
        this.paletteTintMode = paletteTintMode;
        this.grayOutClicked = grayOutClicked;
        this.hotness = hotness;
        this.faviconProvider = faviconProvider;
        this.font = font;
        this.commentTextSize = SettingsUtils.clampCommentTextSize(commentTextSize);
    }

    @NonNull
    public static StoryDisplaySettings from(@NonNull Context context) {
        return new StoryDisplaySettings(
                SettingsUtils.shouldShowPoints(context),
                SettingsUtils.shouldUseCompactPoints(context),
                SettingsUtils.shouldIncludeTopLevelDomain(context),
                SettingsUtils.shouldShowCommentsCount(context),
                SettingsUtils.shouldUseCompactView(context),
                SettingsUtils.shouldShowThumbnails(context),
                SettingsUtils.getPreferredStoryPreviewImageMode(context),
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
        );
    }

    @NonNull
    public StoryDisplaySettings withShowIndex(boolean showIndex) {
        return new StoryDisplaySettings(
                showPoints,
                compactPoints,
                includeTopLevelDomain,
                showCommentsCount,
                compactView,
                thumbnails,
                previewImageMode,
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
        );
    }

    @NonNull
    public StoryRecyclerViewAdapter createAdapter(@NonNull List<Story> stories,
                                                  @Nullable String submissionsUserName,
                                                  int wantedType) {
        return new StoryRecyclerViewAdapter(stories,
                showPoints,
                compactPoints,
                includeTopLevelDomain,
                showCommentsCount,
                compactView,
                thumbnails,
                previewImageMode,
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
                commentTextSize,
                submissionsUserName,
                wantedType
        );
    }

    @NonNull
    public UpdateResult applyToAdapter(@NonNull StoryRecyclerViewAdapter adapter) {
        boolean itemsChanged = false;
        boolean requiresRebuild = false;
        boolean previewImageModeChanged = false;
        boolean fontChanged = false;
        boolean compactHeaderChanged = false;

        if (adapter.showPoints != showPoints) {
            adapter.showPoints = showPoints;
            itemsChanged = true;
        }
        if (adapter.compactPoints != compactPoints) {
            adapter.compactPoints = compactPoints;
            itemsChanged = true;
        }
        if (adapter.includeTopLevelDomain != includeTopLevelDomain) {
            adapter.includeTopLevelDomain = includeTopLevelDomain;
            itemsChanged = true;
        }
        if (adapter.showCommentsCount != showCommentsCount) {
            adapter.showCommentsCount = showCommentsCount;
            itemsChanged = true;
        }
        if (adapter.compactView != compactView) {
            adapter.compactView = compactView;
            itemsChanged = true;
        }
        if (adapter.thumbnails != thumbnails) {
            adapter.thumbnails = thumbnails;
            itemsChanged = true;
        }
        if (!Objects.equals(adapter.previewImageMode, previewImageMode)) {
            adapter.previewImageMode = previewImageMode;
            previewImageModeChanged = true;
            itemsChanged = true;
        }
        if (Float.compare(adapter.storyTextSize, storyTextSize) != 0) {
            adapter.storyTextSize = storyTextSize;
            itemsChanged = true;
        }
        if (Float.compare(adapter.commentTextSize, commentTextSize) != 0) {
            adapter.commentTextSize = commentTextSize;
            itemsChanged = true;
        }
        if (adapter.showIndex != showIndex) {
            adapter.showIndex = showIndex;
            itemsChanged = true;
        }
        if (adapter.leftAlign != leftAlign) {
            adapter.leftAlign = leftAlign;
            requiresRebuild = true;
        }
        if (adapter.cardStyle != cardStyle) {
            adapter.cardStyle = cardStyle;
            requiresRebuild = true;
        }
        if (adapter.tintCardUsingPreview != tintCardUsingPreview) {
            boolean storyCardShellChanged = !adapter.cardStyle;
            adapter.tintCardUsingPreview = tintCardUsingPreview;
            if (storyCardShellChanged) {
                requiresRebuild = true;
            } else {
                itemsChanged = true;
            }
        }
        if (!Objects.equals(adapter.paletteTintMode, paletteTintMode)) {
            adapter.paletteTintMode = paletteTintMode;
            itemsChanged = true;
        }
        if (adapter.grayOutClicked != grayOutClicked) {
            adapter.grayOutClicked = grayOutClicked;
            itemsChanged = true;
        }
        if (!Objects.equals(adapter.font, font)) {
            adapter.font = font;
            fontChanged = true;
            itemsChanged = true;
        }
        if (adapter.compactHeader != compactHeader) {
            adapter.compactHeader = compactHeader;
            compactHeaderChanged = true;
        }
        if (adapter.hotness != hotness) {
            adapter.hotness = hotness;
            itemsChanged = true;
        }
        if (!Objects.equals(adapter.faviconProvider, faviconProvider)) {
            adapter.faviconProvider = faviconProvider;
            itemsChanged = true;
        }

        return new UpdateResult(
                itemsChanged,
                requiresRebuild,
                previewImageModeChanged,
                fontChanged,
                compactHeaderChanged
        );
    }

    public static void copyAdapterSettings(@NonNull StoryRecyclerViewAdapter sourceAdapter,
                                           @NonNull StoryRecyclerViewAdapter targetAdapter) {
        targetAdapter.showPoints = sourceAdapter.showPoints;
        targetAdapter.compactPoints = sourceAdapter.compactPoints;
        targetAdapter.includeTopLevelDomain = sourceAdapter.includeTopLevelDomain;
        targetAdapter.showCommentsCount = sourceAdapter.showCommentsCount;
        targetAdapter.compactView = sourceAdapter.compactView;
        targetAdapter.thumbnails = sourceAdapter.thumbnails;
        targetAdapter.previewImageMode = sourceAdapter.previewImageMode;
        targetAdapter.storyTextSize = sourceAdapter.storyTextSize;
        targetAdapter.showIndex = sourceAdapter.showIndex;
        targetAdapter.compactHeader = sourceAdapter.compactHeader;
        targetAdapter.leftAlign = sourceAdapter.leftAlign;
        targetAdapter.cardStyle = sourceAdapter.cardStyle;
        targetAdapter.tintCardUsingPreview = sourceAdapter.tintCardUsingPreview;
        targetAdapter.paletteTintMode = sourceAdapter.paletteTintMode;
        targetAdapter.grayOutClicked = sourceAdapter.grayOutClicked;
        targetAdapter.hotness = sourceAdapter.hotness;
        targetAdapter.faviconProvider = sourceAdapter.faviconProvider;
        targetAdapter.font = sourceAdapter.font;
        targetAdapter.commentTextSize = sourceAdapter.commentTextSize;
    }

    public static class UpdateResult {
        public final boolean itemsChanged;
        public final boolean requiresRebuild;
        public final boolean previewImageModeChanged;
        public final boolean fontChanged;
        public final boolean compactHeaderChanged;

        private UpdateResult(boolean itemsChanged,
                             boolean requiresRebuild,
                             boolean previewImageModeChanged,
                             boolean fontChanged,
                             boolean compactHeaderChanged) {
            this.itemsChanged = itemsChanged;
            this.requiresRebuild = requiresRebuild;
            this.previewImageModeChanged = previewImageModeChanged;
            this.fontChanged = fontChanged;
            this.compactHeaderChanged = compactHeaderChanged;
        }
    }
}
