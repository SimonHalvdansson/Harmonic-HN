package com.simon.harmonichackernews.ui.stories;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.adapters.StoryDisplaySettings;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.LinkSummaryLoader;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.utils.PreviewImageTintExtractor;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache;
import com.simon.harmonichackernews.utils.Utils;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import coil.Coil;
import coil.request.Disposable;
import coil.request.ImageRequest;
import coil.target.Target;

/**
 * Non-View state owned by {@code StoriesFragment} while Compose renders the story list.
 *
 * <p>This replaces the old unattached RecyclerView adapter. It deliberately contains only the
 * stateful responsibilities still needed by the Compose screen: pagination, display settings,
 * preview metadata/image prefetch, and card tint caching.</p>
 */
public final class StoryListState {
    public static final int PAGINATION_PAGE_SIZE = 30;
    private static final int LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176;
    private static final int FAVICON_TINT_SIZE_DP = 64;

    private final List<Story> stories;
    private final Map<Story, StoryPreviewImageLoader.PreviewImageRequest> previewRequests =
            new IdentityHashMap<>();
    private final Map<Story, Disposable> imagePrefetches = new IdentityHashMap<>();
    private final PreviewImageTintExtractor tintExtractor = new PreviewImageTintExtractor();
    @Nullable private Runnable changedListener;

    public boolean showPoints;
    public boolean compactPoints;
    public boolean includeTopLevelDomain;
    public boolean showCommentsCount;
    public boolean compactView;
    public boolean thumbnails;
    public String previewImageMode;
    public boolean borderlessLargePreviewImage;
    public boolean showSummary;
    public float storyTextSize;
    public boolean showIndex;
    public boolean compactHeader;
    public boolean leftAlign;
    public boolean cardStyle;
    public boolean tintCardUsingPreview;
    public String paletteTintMode;
    public String faviconProvider;
    public int hotness;
    public int type;
    public String font;
    public float commentTextSize;
    public boolean allowCommentRows;
    public boolean disableClickedEffects;
    public boolean grayOutClicked;

    public boolean paginationMode;
    public boolean showLoadMoreButton;
    public int visibleStoryCount = PAGINATION_PAGE_SIZE;
    private boolean loadMoreLoading;

    public StoryListState(
            @NonNull List<Story> stories,
            @NonNull StoryDisplaySettings settings,
            int wantedType) {
        this.stories = stories;
        applyInitialSettings(settings);
        type = wantedType;
        tintExtractor.attach();
    }

    public void setChangedListener(@Nullable Runnable listener) {
        changedListener = listener;
    }

    public int getItemCount() {
        return getVisibleStoryItemCount() + (hasLoadMoreButton() ? 1 : 0);
    }

    public int getVisibleStoryItemCount() {
        return paginationMode ? Math.min(visibleStoryCount, stories.size()) : stories.size();
    }

    public boolean hasLoadMoreButton() {
        return loadMoreLoading
                || showLoadMoreButton
                || (paginationMode && visibleStoryCount < stories.size());
    }

    public boolean isLoadMoreLoading() {
        return loadMoreLoading;
    }

    public void setLoadMoreLoading(boolean loading) {
        if (loadMoreLoading == loading) return;
        loadMoreLoading = loading;
        notifyChanged();
    }

    public void loadNextPage() {
        int oldVisibleCount = visibleStoryCount;
        visibleStoryCount = Math.min(visibleStoryCount + PAGINATION_PAGE_SIZE, stories.size());
        if (visibleStoryCount != oldVisibleCount) notifyChanged();
    }

    public void updateStoryClickedState(int position) {
        if (position >= 0 && position < stories.size()) notifyChanged();
    }

    public void updateStoryIndicesFromPosition(int position) {
        if (showIndex && position >= 0 && position < getVisibleStoryItemCount()) notifyChanged();
    }

    public void notifyDataSetChanged() {
        notifyChanged();
    }

    public void notifyItemChanged(int position) {
        notifyChanged();
    }

    public void notifyItemInserted(int position) {
        notifyChanged();
    }

    public void notifyItemRemoved(int position) {
        notifyChanged();
    }

    public void notifyItemRangeChanged(int positionStart, int itemCount) {
        notifyChanged();
    }

    public void notifyItemRangeInserted(int positionStart, int itemCount) {
        notifyChanged();
    }

    public void notifyItemRangeRemoved(int positionStart, int itemCount) {
        notifyChanged();
    }

    public void invalidateTypography() {
        // Compose typography is derived from StoryDisplaySettings on recomposition.
    }

    public int resolveStoryCardBackgroundColor(@Nullable Context context, @Nullable Story story) {
        int baseColor = context == null
                ? Color.TRANSPARENT
                : PreviewImageTintUtils.getTintBaseColor(context);
        if (!tintCardUsingPreview || story == null) return baseColor;
        if (!SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                && !story.previewImageLoadFailed
                && PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(
                story, baseColor, paletteTintMode)) {
            return story.previewImageTintColor;
        }
        String faviconUrl = getFaviconUrl(story);
        if (story.faviconTintColorLoaded
                && story.faviconTintBaseColor == baseColor
                && TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)
                && SettingsUtils.getPaletteTintConfigKey(paletteTintMode).equals(
                SettingsUtils.getPaletteTintConfigKey(story.faviconTintMode))) {
            return story.faviconTintColor;
        }
        return baseColor;
    }

    public void prefetchPreviewImage(@Nullable Context context, @Nullable Story story) {
        if (context == null || story == null || !story.loaded || story.loadingFailed
                || story.isComment || TextUtils.isEmpty(story.url)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        hydrateCachedPreviewState(appContext, story);
        if (tintCardUsingPreview) prefetchFaviconTint(appContext, story);

        boolean previewEnabled = !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode);
        if (!previewEnabled && !showSummary) return;
        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            if (previewEnabled) prefetchPreviewDrawable(appContext, story);
            return;
        }
        if (previewRequests.containsKey(story)
                || story.previewImageUrlLoading
                || story.linkSummaryLoading) {
            return;
        }

        story.previewImageUrlLoading = previewEnabled;
        story.linkSummaryLoading = showSummary;
        StoryPreviewImageLoader.PreviewImageRequest request =
                StoryPreviewImageLoader.loadPreviewContent(
                        appContext,
                        story.id,
                        story.url,
                        showSummary,
                        (imageUrl, summary) -> {
                            previewRequests.remove(story);
                            story.previewImageUrlLoading = false;
                            story.linkSummaryLoading = false;
                            story.previewImageUrlLoaded = true;
                            if (!TextUtils.isEmpty(imageUrl)) {
                                setPreviewImageUrl(story, imageUrl);
                                story.previewImageLoadFailed = false;
                            } else if (previewEnabled) {
                                story.previewImageLoadFailed = true;
                                PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
                            }
                            if (showSummary) {
                                story.linkSummaryLoaded = true;
                                story.linkSummaryDescription =
                                        summary == null ? null : summary.description;
                            }
                            cachePreviewState(appContext, story);
                            if (previewEnabled && !TextUtils.isEmpty(story.previewImageUrl)) {
                                prefetchPreviewDrawable(appContext, story);
                            }
                            notifyChanged();
                        });
        previewRequests.put(story, request);
    }

    public void dispose() {
        changedListener = null;
        for (Map.Entry<Story, StoryPreviewImageLoader.PreviewImageRequest> entry
                : previewRequests.entrySet()) {
            entry.getValue().cancel();
            entry.getKey().previewImageUrlLoading = false;
            entry.getKey().linkSummaryLoading = false;
        }
        previewRequests.clear();
        for (Map.Entry<Story, Disposable> entry : imagePrefetches.entrySet()) {
            entry.getValue().dispose();
            entry.getKey().previewImageLoading = false;
        }
        imagePrefetches.clear();
        tintExtractor.detach();
    }

    private void applyInitialSettings(StoryDisplaySettings settings) {
        showPoints = settings.showPoints;
        compactPoints = settings.compactPoints;
        includeTopLevelDomain = settings.includeTopLevelDomain;
        showCommentsCount = settings.showCommentsCount;
        compactView = settings.compactView;
        thumbnails = settings.thumbnails;
        previewImageMode = settings.previewImageMode;
        borderlessLargePreviewImage = settings.borderlessLargePreviewImage;
        showSummary = settings.showSummary;
        storyTextSize = settings.storyTextSize;
        showIndex = settings.showIndex;
        compactHeader = settings.compactHeader;
        leftAlign = settings.leftAlign;
        cardStyle = settings.cardStyle;
        tintCardUsingPreview = settings.tintCardUsingPreview;
        paletteTintMode = SettingsUtils.getPaletteTintConfigKey(settings.paletteTintMode);
        grayOutClicked = settings.grayOutClicked;
        hotness = settings.hotness;
        faviconProvider = settings.faviconProvider;
        font = settings.font;
        commentTextSize = settings.commentTextSize;
    }

    private void hydrateCachedPreviewState(Context context, Story story) {
        if (TextUtils.isEmpty(story.previewImageUrl) && !story.previewImageUrlLoaded) {
            boolean loaded = StoryPreviewImageLoader.isCachedPreviewImageUrlLoaded(
                    context, story.id, story.url);
            if (loaded) {
                setPreviewImageUrl(story, StoryPreviewImageLoader.getCachedPreviewImageUrl(
                        context, story.id, story.url));
                story.previewImageUrlLoaded = true;
                story.previewImageLoadFailed = TextUtils.isEmpty(story.previewImageUrl);
            }
        }
        if (showSummary && !story.linkSummaryLoaded) {
            LinkSummaryLoader.Result summary =
                    StoryPreviewImageLoader.getCachedLinkSummary(context, story.url);
            if (summary != null) {
                story.linkSummaryLoaded = true;
                story.linkSummaryDescription = summary.description;
                if (TextUtils.isEmpty(story.previewImageUrl)
                        && !TextUtils.isEmpty(summary.imageUrl)) {
                    setPreviewImageUrl(story, summary.imageUrl);
                    story.previewImageUrlLoaded = true;
                }
            }
        }
        if (tintCardUsingPreview && !TextUtils.isEmpty(story.previewImageUrl)) {
            int baseColor = PreviewImageTintUtils.getTintBaseColor(context);
            Integer tint = StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                    context, story.id, story.previewImageUrl, baseColor);
            if (tint != null) {
                PreviewImageTintUtils.applyCachedStoryPreviewImageTintColor(
                        story, story.previewImageUrl, baseColor, paletteTintMode, tint);
            }
        }
    }

    private void prefetchPreviewDrawable(Context context, Story story) {
        if (TextUtils.isEmpty(story.previewImageUrl)
                || story.previewImageLoaded
                || story.previewImageLoading
                || imagePrefetches.containsKey(story)) {
            return;
        }
        story.previewImageLoading = true;
        String imageUrl = story.previewImageUrl;
        int width = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? context.getResources().getDisplayMetrics().widthPixels
                : Utils.pxFromDpInt(context.getResources(), 72);
        int height = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? Utils.pxFromDpInt(context.getResources(), LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP)
                : Utils.pxFromDpInt(context.getResources(), 54);
        ImageRequest request = new ImageRequest.Builder(context)
                .data(imageUrl)
                .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .size(width, height)
                .allowHardware(!tintCardUsingPreview)
                .target(new Target() {
                    @Override
                    public void onError(@Nullable Drawable error) {
                        imagePrefetches.remove(story);
                        story.previewImageLoading = false;
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable result) {
                        imagePrefetches.remove(story);
                        story.previewImageLoading = false;
                        story.previewImageLoaded = true;
                        story.previewImageLoadFailed = false;
                        StoryPreviewImageMemoryCache.put(story.id, imageUrl, result);
                        requestTint(context, story, imageUrl, result, false);
                        cachePreviewState(context, story);
                        notifyChanged();
                    }
                })
                .build();
        Disposable disposable = Coil.imageLoader(context).enqueue(request);
        if (story.previewImageLoading) {
            imagePrefetches.put(story, disposable);
        }
    }

    private void prefetchFaviconTint(Context context, Story story) {
        if (!thumbnails || !TextUtils.isEmpty(story.previewImageUrl)) return;
        String faviconUrl = getFaviconUrl(story);
        if (TextUtils.isEmpty(faviconUrl)
                || story.faviconTintColorLoading
                || (story.faviconTintColorLoaded
                && TextUtils.equals(story.faviconTintSourceUrl, faviconUrl))) {
            return;
        }
        story.faviconTintSourceUrl = faviconUrl;
        story.faviconTintColorLoading = true;
        int baseColor = PreviewImageTintUtils.getTintBaseColor(context);
        Integer cachedTint = StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                context, story.id, faviconUrl, baseColor);
        if (cachedTint != null) {
            applyTint(context, story, faviconUrl, baseColor, cachedTint, true);
            return;
        }
        int size = Utils.pxFromDpInt(context.getResources(), FAVICON_TINT_SIZE_DP);
        ImageRequest request = new ImageRequest.Builder(context)
                .data(faviconUrl)
                .size(size, size)
                .allowHardware(false)
                .target(new Target() {
                    @Override
                    public void onError(@Nullable Drawable error) {
                        story.faviconTintColorLoading = false;
                        story.faviconTintColorLoadFailed = true;
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable result) {
                        requestTint(context, story, faviconUrl, result, true);
                    }
                })
                .build();
        Coil.imageLoader(context).enqueue(request);
    }

    private void requestTint(
            Context context,
            Story story,
            String sourceUrl,
            Drawable drawable,
            boolean favicon) {
        if (!tintCardUsingPreview) return;
        int baseColor = PreviewImageTintUtils.getTintBaseColor(context);
        Integer cached = StoryPreviewImageMemoryCache.getTintColor(story.id, sourceUrl, baseColor);
        if (cached != null) {
            applyTint(context, story, sourceUrl, baseColor, cached, favicon);
            return;
        }
        String mode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        tintExtractor.request(
                story,
                sourceUrl,
                baseColor,
                mode,
                favicon
                        ? PreviewImageTintExtractor.Source.FAVICON
                        : PreviewImageTintExtractor.Source.PREVIEW_IMAGE,
                drawable,
                new PreviewImageTintExtractor.Callback() {
                    @Override
                    public void onTintReady(int tintColor) {
                        applyTint(context, story, sourceUrl, baseColor, tintColor, favicon);
                    }

                    @Override
                    public void onTintFailed() {
                        if (favicon) {
                            story.faviconTintColorLoading = false;
                            story.faviconTintColorLoadFailed = true;
                        }
                    }

                    @Override
                    public void onTintCancelled() {
                        if (favicon) story.faviconTintColorLoading = false;
                    }
                });
    }

    private void applyTint(
            Context context,
            Story story,
            String sourceUrl,
            int baseColor,
            int tintColor,
            boolean favicon) {
        if (favicon) {
            if (!TextUtils.equals(story.faviconTintSourceUrl, sourceUrl)) return;
            story.faviconTintColor = tintColor;
            story.faviconTintColorLoaded = true;
            story.faviconTintColorLoading = false;
            story.faviconTintColorLoadFailed = false;
            story.faviconTintBaseColor = baseColor;
            story.faviconTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        } else if (!PreviewImageTintUtils.applyCachedStoryPreviewImageTintColor(
                story, sourceUrl, baseColor, paletteTintMode, tintColor)) {
            return;
        }
        cachePreviewState(context, story);
        notifyChanged();
    }

    private void cachePreviewState(Context context, Story story) {
        Utils.cacheStoryPreviewState(context, story);
        if (story.previewImageTintColorLoaded
                && !TextUtils.isEmpty(story.previewImageTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    context,
                    story.id,
                    story.previewImageTintSourceUrl,
                    story.previewImageTintBaseColor,
                    story.previewImageTintColor);
        }
        if (story.faviconTintColorLoaded && !TextUtils.isEmpty(story.faviconTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    context,
                    story.id,
                    story.faviconTintSourceUrl,
                    story.faviconTintBaseColor,
                    story.faviconTintColor);
        }
    }

    private void setPreviewImageUrl(Story story, @Nullable String imageUrl) {
        if (!TextUtils.equals(story.previewImageUrl, imageUrl)) {
            PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
            story.previewImageLoaded = false;
        }
        story.previewImageUrl = imageUrl;
    }

    @Nullable
    private String getFaviconUrl(Story story) {
        try {
            return FaviconLoader.getFaviconUrl(story.url, faviconProvider);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void notifyChanged() {
        if (changedListener != null) changedListener.run();
    }
}
