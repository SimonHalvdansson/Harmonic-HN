package com.simon.harmonichackernews.adapters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.List;

public class StoryDisplaySettings {
    public final boolean showPoints;
    public final boolean compactPoints;
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
    public final boolean grayOutClicked;
    public final int hotness;
    public final String faviconProvider;
    public final String font;

    private StoryDisplaySettings(boolean showPoints,
                                 boolean compactPoints,
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
                                 boolean grayOutClicked,
                                 int hotness,
                                 String faviconProvider,
                                 String font) {
        this.showPoints = showPoints;
        this.compactPoints = compactPoints;
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
        this.grayOutClicked = grayOutClicked;
        this.hotness = hotness;
        this.faviconProvider = faviconProvider;
        this.font = font;
    }

    @NonNull
    public static StoryDisplaySettings from(@NonNull Context context) {
        return new StoryDisplaySettings(
                SettingsUtils.shouldShowPoints(context),
                SettingsUtils.shouldUseCompactPoints(context),
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
                SettingsUtils.shouldGrayOutClicked(context),
                SettingsUtils.getPreferredHotness(context),
                SettingsUtils.getPreferredFaviconProvider(context),
                SettingsUtils.getPreferredFont(context)
        );
    }

    @NonNull
    public StoryRecyclerViewAdapter createAdapter(@NonNull List<Story> stories,
                                                  @Nullable String submissionsUserName,
                                                  int wantedType) {
        return new StoryRecyclerViewAdapter(stories,
                showPoints,
                compactPoints,
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
                grayOutClicked,
                hotness,
                faviconProvider,
                font,
                submissionsUserName,
                wantedType
        );
    }

    public void applyTo(@NonNull StoryRecyclerViewAdapter adapter) {
        adapter.showPoints = showPoints;
        adapter.compactPoints = compactPoints;
        adapter.showCommentsCount = showCommentsCount;
        adapter.compactView = compactView;
        adapter.thumbnails = thumbnails;
        adapter.previewImageMode = previewImageMode;
        adapter.storyTextSize = storyTextSize;
        adapter.showIndex = showIndex;
        adapter.compactHeader = compactHeader;
        adapter.leftAlign = leftAlign;
        adapter.cardStyle = cardStyle;
        adapter.tintCardUsingPreview = tintCardUsingPreview;
        adapter.grayOutClicked = grayOutClicked;
        adapter.hotness = hotness;
        adapter.faviconProvider = faviconProvider;
        adapter.font = font;
    }

    public static void copyAdapterSettings(@NonNull StoryRecyclerViewAdapter sourceAdapter,
                                           @NonNull StoryRecyclerViewAdapter targetAdapter) {
        targetAdapter.showPoints = sourceAdapter.showPoints;
        targetAdapter.compactPoints = sourceAdapter.compactPoints;
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
        targetAdapter.grayOutClicked = sourceAdapter.grayOutClicked;
        targetAdapter.hotness = sourceAdapter.hotness;
        targetAdapter.faviconProvider = sourceAdapter.faviconProvider;
        targetAdapter.font = sourceAdapter.font;
    }
}
