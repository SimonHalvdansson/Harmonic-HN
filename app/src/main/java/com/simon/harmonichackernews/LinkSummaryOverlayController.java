package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.BackEventCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.Transition;
import androidx.transition.AutoTransition;
import androidx.transition.ChangeBounds;
import androidx.transition.Fade;
import androidx.transition.TransitionListenerAdapter;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.transition.MaterialContainerTransform;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.ImageOnlyOverlayContentBinding;
import com.simon.harmonichackernews.databinding.LinkSummaryOverlayBinding;
import com.simon.harmonichackernews.databinding.LinkSummaryStoryPageBinding;
import com.simon.harmonichackernews.databinding.ReferenceLinkSummaryContentBinding;
import com.simon.harmonichackernews.databinding.StoryLinkSummaryContentBinding;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.LinkSummaryLoader;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.PreviewImageLayoutUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.TextSizeImageSpan;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.ImageViewTarget;
import coil.util.CoilUtils;

import java.util.ArrayList;
import java.util.List;

final class LinkSummaryOverlayController {
    static final class StorySharedElements {
        @Nullable final ImageView image;
        @Nullable final View title;
        @Nullable final View meta;

        StorySharedElements(
                @Nullable ImageView image,
                @Nullable View title,
                @Nullable View meta) {
            this.image = image;
            this.title = title;
            this.meta = meta;
        }
    }

    private static final class SoftwareImageDrawableSwap {
        final ImageView view;
        final Drawable original;
        final Bitmap softwareBitmap;

        SoftwareImageDrawableSwap(
                @NonNull ImageView view,
                @NonNull Drawable original,
                @NonNull Bitmap softwareBitmap) {
            this.view = view;
            this.original = original;
            this.softwareBitmap = softwareBitmap;
        }

        void restore() {
            view.setImageDrawable(original);
            softwareBitmap.recycle();
        }
    }

    private static final class LinkPositionSourceView extends View {
        LinkPositionSourceView(@NonNull Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    private static final class StorySharedElementSnapshot {
        final View start;
        final View end;
        final StorySharedElementSnapshotDrawable overlay;
        final Bitmap startBitmap;
        final Bitmap endBitmap;
        final float startAlpha;
        final float endAlpha;
        final float startX;
        final float startY;
        final float endX;
        final float endY;
        final int startWidth;
        final int startHeight;
        final int endWidth;
        final int endHeight;

        StorySharedElementSnapshot(
                @NonNull View start,
                @NonNull View end,
                @NonNull StorySharedElementSnapshotDrawable overlay,
                @NonNull Bitmap startBitmap,
                @NonNull Bitmap endBitmap,
                float startAlpha,
                float endAlpha,
                float startX,
                float startY,
                float endX,
                float endY) {
            this.start = start;
            this.end = end;
            this.overlay = overlay;
            this.startBitmap = startBitmap;
            this.endBitmap = endBitmap;
            this.startAlpha = startAlpha;
            this.endAlpha = endAlpha;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            startWidth = start.getWidth();
            startHeight = start.getHeight();
            endWidth = end.getWidth();
            endHeight = end.getHeight();
        }
    }

    private static final class StorySharedElementSnapshotDrawable extends Drawable {
        private final Bitmap start;
        private final Bitmap end;
        private final float startViewAlpha;
        private final float endViewAlpha;
        private final float startTopRadius;
        private final float startBottomRadius;
        private final float endTopRadius;
        private final float endBottomRadius;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path clipPath = new Path();
        private final RectF clipBounds = new RectF();
        private float progress;
        private int alpha = 255;

        StorySharedElementSnapshotDrawable(
                @NonNull Bitmap start,
                @NonNull Bitmap end,
                float startViewAlpha,
                float endViewAlpha,
                float startTopRadius,
                float startBottomRadius,
                float endTopRadius,
                float endBottomRadius) {
            this.start = start;
            this.end = end;
            this.startViewAlpha = startViewAlpha;
            this.endViewAlpha = endViewAlpha;
            this.startTopRadius = startTopRadius;
            this.startBottomRadius = startBottomRadius;
            this.endTopRadius = endTopRadius;
            this.endBottomRadius = endBottomRadius;
        }

        void setProgress(float progress) {
            this.progress = progress;
            invalidateSelf();
        }

        @Override public void draw(@NonNull Canvas canvas) {
            float topRadius = lerp(startTopRadius, endTopRadius, progress);
            float bottomRadius = lerp(startBottomRadius, endBottomRadius, progress);
            int saveCount = canvas.save();
            if (topRadius > 0f || bottomRadius > 0f) {
                clipBounds.set(getBounds());
                float[] radii = {
                        topRadius, topRadius,
                        topRadius, topRadius,
                        bottomRadius, bottomRadius,
                        bottomRadius, bottomRadius
                };
                clipPath.reset();
                clipPath.addRoundRect(clipBounds, radii, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }
            float viewAlpha = lerp(startViewAlpha, endViewAlpha, progress);
            paint.setAlpha(Math.round(alpha * viewAlpha * (1f - progress)));
            canvas.drawBitmap(start, null, getBounds(), paint);
            paint.setAlpha(Math.round(alpha * viewAlpha * progress));
            canvas.drawBitmap(end, null, getBounds(), paint);
            canvas.restoreToCount(saveCount);
        }

        private static float lerp(float start, float end, float progress) {
            return start + (end - start) * progress;
        }

        @Override public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class SplitCornerOutlineProvider extends ViewOutlineProvider {
        private final Path outlinePath = new Path();
        private final float[] cornerRadii = new float[8];
        private float topRadius;
        private float bottomRadius;

        SplitCornerOutlineProvider(float topRadius, float bottomRadius) {
            setCornerRadii(topRadius, bottomRadius);
        }

        float getTopRadius() {
            return topRadius;
        }

        float getBottomRadius() {
            return bottomRadius;
        }

        void setCornerRadii(float topRadius, float bottomRadius) {
            this.topRadius = topRadius;
            this.bottomRadius = bottomRadius;
            cornerRadii[0] = topRadius;
            cornerRadii[1] = topRadius;
            cornerRadii[2] = topRadius;
            cornerRadii[3] = topRadius;
            cornerRadii[4] = bottomRadius;
            cornerRadii[5] = bottomRadius;
            cornerRadii[6] = bottomRadius;
            cornerRadii[7] = bottomRadius;
        }

        @Override public void getOutline(View view, Outline outline) {
            if (view.getWidth() <= 0 || view.getHeight() <= 0) {
                outline.setEmpty();
                return;
            }
            outlinePath.reset();
            outlinePath.addRoundRect(
                    0, 0, view.getWidth(), view.getHeight(),
                    cornerRadii, Path.Direction.CW);
            outline.setConvexPath(outlinePath);
        }
    }

    interface Host {
        @Nullable Context getLinkSummaryContext();
        @NonNull Context requireLinkSummaryContext();
        @Nullable ViewGroup getLinkSummaryOverlayHost();
        @Nullable View findLinkSummarySourceView(int storyId);
        default @Nullable StorySharedElements findLinkSummaryStorySharedElements(int storyId) { return null; }
        default int getLinkSummaryStoryCount() { return 0; }
        default @Nullable Story getLinkSummaryStoryAt(int position) { return null; }
        default boolean isLinkSummaryStoryPageable(int position, @NonNull Story story) {
            return !story.isComment;
        }
        default int getLinkSummaryStoryPagingDistance(int firstStoryId, int secondStoryId) {
            return 0;
        }
        default void scrollLinkSummaryStoryListBy(int dy) { }
        default void setLinkSummaryStoryPagingAlphas(
                int firstStoryId, float firstAlpha,
                int secondStoryId, float secondAlpha) { }
        default void clearLinkSummaryStoryPagingAlphas(boolean animate) { }
        default @Nullable View findLinkSummaryImageSourceView() { return null; }
        default @Nullable Integer getLinkSummaryImageBackgroundColor() { return null; }
        default void setLinkSummaryImageSourceSuppressed(boolean suppressed) { }
        default int resolveStoryCardBackgroundColor(Story story) {
            return PreviewImageTintUtils.getTintBaseColor(requireLinkSummaryContext());
        }
        void stopLinkSummaryListScroll();
        void syncLinkSummaryBackState();

        default void openStoryLinkSummary(Story story, int position, boolean showWebsite) { }
        default void toggleStoryVote(Story story, int position, boolean currentlyUpvoted,
                                     Runnable completion) { completion.run(); }
        default void toggleStoryRead(Story story, int position) { }
        default void toggleStoryBookmark(Story story, int position, boolean currentlyBookmarked) { }
        default void toggleStoryFavorite(Story story, int position, boolean currentlyFavorited,
                                         Runnable completion) { completion.run(); }
    }

    private static final int NO_STORY_ID = -1;
    private static final String PDF_CONTENT_TYPE_ERROR =
            "This link contains application/pdf, not a web page";
    private static final int TRANSFORM_DURATION_MS = 280;
    private static final int STORY_CONTENT_TRANSITION_DURATION_MS = 220;
    private static final int REFERENCE_CONTENT_TRANSITION_DURATION_MS = 240;
    private static final int REFERENCE_IMAGE_TRANSITION_DURATION_MS = 360;
    private static final int REFERENCE_METADATA_FADE_OUT_DURATION_MS = 70;
    private static final int REFERENCE_METADATA_FADE_IN_DURATION_MS = 140;
    private static final int REFERENCE_IMAGE_COLLAPSED_SIZE_DP = 104;
    private static final int ACTION_SWAP_OUT_DURATION_MS = 90;
    private static final int ACTION_SWAP_IN_DURATION_MS = 150;
    private static final float ACTION_SWAP_MIN_SCALE = 0.72f;
    private static final int CARD_CORNER_RADIUS_DP = 28;
    private static final int STORY_PREVIEW_DEFAULT_HEIGHT_DP = 220;
    private static final int TEXT_STORY_SUMMARY_MAX_CHARS = 600;
    private static final int PREDICTIVE_BACK_TRANSLATION_X_DP = 56;
    private static final int PREDICTIVE_BACK_TRANSLATION_Y_DP = 18;
    private static final float PREDICTIVE_BACK_MIN_SCALE = 0.9f;
    private static final float PREDICTIVE_BACK_MIN_SCRIM_ALPHA = 0.45f;
    private static final float INLINE_LINK_RETURN_FADE_START = 0.45f;
    private static final float INLINE_LINK_RETURN_FADE_END = 0.75f;
    private static final float STORY_PAGE_FADE_START = 0.75f;

    private static final class StoryPageEntry {
        @NonNull final Story story;
        final int sourcePosition;

        StoryPageEntry(@NonNull Story story, int sourcePosition) {
            this.story = story;
            this.sourcePosition = sourcePosition;
        }
    }

    private final class StoryPageHolder extends RecyclerView.ViewHolder {
        final LinkSummaryStoryPageBinding pageBinding;
        final MaterialCardView pageCard;
        final NestedScrollView pageScroll;
        final StoryLinkSummaryContentBinding content;
        @Nullable Story story;
        int sourcePosition = RecyclerView.NO_POSITION;
        @Nullable String pageUrl;
        @Nullable String pageFallbackTitle;
        @Nullable LinkSummaryLoader.SummaryRequest pageSummaryRequest;
        boolean pendingPreviewHide;
        @Nullable Runnable pendingStateChange;

        StoryPageHolder(@NonNull LinkSummaryStoryPageBinding pageBinding) {
            super(pageBinding.getRoot());
            this.pageBinding = pageBinding;
            pageCard = pageBinding.linkSummaryStoryPageCard;
            pageScroll = pageBinding.linkSummaryStoryPageScroll;
            content = StoryLinkSummaryContentBinding.inflate(
                    LayoutInflater.from(pageBinding.getRoot().getContext()),
                    pageBinding.linkSummaryStoryPageBody,
                    true);
        }

        void cancelRequests() {
            if (pageSummaryRequest != null) {
                pageSummaryRequest.cancel();
                pageSummaryRequest = null;
            }
            CoilUtils.dispose(content.storyLinkPreview);
        }
    }

    private final class StoryPagerAdapter extends RecyclerView.Adapter<StoryPageHolder> {
        private final List<StoryPageEntry> entries;
        private final android.util.SparseArray<StoryPageHolder> boundHolders =
                new android.util.SparseArray<>();

        StoryPagerAdapter(@NonNull List<StoryPageEntry> entries) {
            this.entries = entries;
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public StoryPageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinkSummaryStoryPageBinding pageBinding = LinkSummaryStoryPageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            StoryPageHolder holder = new StoryPageHolder(pageBinding);
            holder.itemView.setOnClickListener(view -> dismiss(true));
            holder.pageCard.setOnTouchListener((view, event) -> true);
            configureNestedStoryScroll(holder);
            holder.itemView.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                        oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft
                        || bottom - top != oldBottom - oldTop) {
                    configureStoryPageWidth(holder);
                    resizeStoryPage(holder);
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull StoryPageHolder holder, int position) {
            removeBoundHolder(holder);
            boundHolders.put(position, holder);
            bindStoryPage(holder, entries.get(position));
        }

        @Override
        public void onViewRecycled(@NonNull StoryPageHolder holder) {
            removeBoundHolder(holder);
            holder.cancelRequests();
            super.onViewRecycled(holder);
        }

        @Override public int getItemCount() { return entries.size(); }
        @Override public long getItemId(int position) { return entries.get(position).story.id; }

        @Nullable StoryPageHolder getBoundHolder(int position) {
            return boundHolders.get(position);
        }

        @NonNull StoryPageEntry getEntry(int position) { return entries.get(position); }

        private void removeBoundHolder(@NonNull StoryPageHolder holder) {
            for (int index = boundHolders.size() - 1; index >= 0; index--) {
                if (boundHolders.valueAt(index) == holder) {
                    boundHolders.removeAt(index);
                }
            }
        }

        void cancelAllRequests() {
            for (int index = 0; index < boundHolders.size(); index++) {
                StoryPageHolder holder = boundHolders.valueAt(index);
                if (holder != null) holder.cancelRequests();
            }
        }
    }

    private final Host host;
    private FrameLayout overlay;
    private LinkSummaryOverlayBinding binding;
    private MaterialCardView card;
    private StoryLinkSummaryContentBinding storyBinding;
    private ViewPager2 storyPager;
    private StoryPagerAdapter storyPagerAdapter;
    private StoryPageHolder currentStoryPage;
    private float lastStoryPagerPosition = Float.NaN;
    private float pendingStoryListScrollPixels;
    private ReferenceLinkSummaryContentBinding referenceBinding;
    private ImageOnlyOverlayContentBinding imageBinding;
    private View sourceView;
    private LinkPositionSourceView linkPositionSourceView;
    private ImageView storyImageSourceView;
    private View storyTitleSourceView;
    private View storyMetaSourceView;
    private float storyImageSourceAlpha = 1f;
    private float storyTitleSourceAlpha = 1f;
    private float storyMetaSourceAlpha = 1f;
    private LinkSummaryLoader.SummaryRequest summaryRequest;
    private int visibleStoryId = NO_STORY_ID;
    private int visibleStoryPosition = -1;
    private String visibleUrl;
    private String fallbackTitle;
    private boolean dismissing;
    private boolean predictiveBackActive;
    private boolean enterTransitionStarted;
    private boolean enterTransitionComplete;
    private Runnable pendingReferenceStateChange;
    private final List<StorySharedElementSnapshot> storySharedElementSnapshots = new ArrayList<>();
    private ValueAnimator storySharedElementSnapshotAnimator;
    private ViewGroup storySharedElementSnapshotDrawing;
    private int storySharedElementSnapshotGeneration;
    private int storyVoteLoadingId = NO_STORY_ID;
    private int storyFavoriteLoadingId = NO_STORY_ID;
    private boolean referenceImageExpanded;
    private ValueAnimator referenceImageCornerAnimator;

    LinkSummaryOverlayController(Host host) {
        this.host = host;
    }

    boolean isShowing() { return overlay != null; }
    boolean isPredictiveBackActive() { return predictiveBackActive; }
    int getVisibleStoryId() { return visibleStoryId; }
    boolean isShowingReference() { return referenceBinding != null; }
    boolean isShowingImage() { return imageBinding != null; }
    @Nullable String getVisibleUrl() { return visibleUrl; }
    @Nullable String getFallbackTitle() { return fallbackTitle; }

    @SuppressLint("ClickableViewAccessibility")
    void showStory(
            Story story,
            int position,
            @Nullable View source,
            @Nullable StorySharedElements sharedElements) {
        Context context = host.getLinkSummaryContext();
        if (context == null || story == null
                || (story.isLink && TextUtils.isEmpty(story.url))
                || !prepareOverlay(source)) {
            return;
        }
        captureStorySharedElements(sharedElements, false);
        setupStoryPager(story, position);
    }

    private void setupStoryPager(@NonNull Story openedStory, int openedPosition) {
        if (binding == null) return;
        List<StoryPageEntry> entries = new ArrayList<>();
        int initialItem = RecyclerView.NO_POSITION;
        int storyCount = host.getLinkSummaryStoryCount();
        for (int sourcePosition = 0; sourcePosition < storyCount; sourcePosition++) {
            Story candidate = host.getLinkSummaryStoryAt(sourcePosition);
            if (candidate == null || !host.isLinkSummaryStoryPageable(sourcePosition, candidate)) {
                continue;
            }
            if (candidate == openedStory
                    || (candidate.id == openedStory.id && sourcePosition == openedPosition)) {
                initialItem = entries.size();
            }
            entries.add(new StoryPageEntry(candidate, sourcePosition));
        }
        if (initialItem == RecyclerView.NO_POSITION) {
            initialItem = entries.size();
            entries.add(new StoryPageEntry(openedStory, openedPosition));
        }

        binding.linkSummaryCard.setVisibility(View.GONE);
        storyPager = binding.linkSummaryStoryPager;
        storyPager.setVisibility(View.VISIBLE);
        storyPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        storyPager.setOffscreenPageLimit(1);
        // Pages draw outside their bounds for card elevation, so hide adjacent cards at rest.
        storyPager.setPageTransformer((page, pagePosition) -> {
            float distanceFromSelectedPage = Math.min(1f, Math.abs(pagePosition));
            float fadeProgress = Math.max(0f,
                    (distanceFromSelectedPage - STORY_PAGE_FADE_START)
                            / (1f - STORY_PAGE_FADE_START));
            page.setAlpha(1f - fadeProgress);
        });
        storyPager.setUserInputEnabled(false);
        storyPagerAdapter = new StoryPagerAdapter(entries);
        storyPager.setAdapter(storyPagerAdapter);
        final int startingItem = initialItem;
        lastStoryPagerPosition = startingItem;
        pendingStoryListScrollPixels = 0f;
        storyPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {
                updateStoryPagingProgress(position, positionOffset);
            }

            @Override
            public void onPageSelected(int position) {
                activateStoryPage(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE && storyPager != null) {
                    int position = storyPager.getCurrentItem();
                    lastStoryPagerPosition = position;
                    updateStoryPagingProgress(position, 0f);
                    activateStoryPage(position);
                }
            }
        });
        storyPager.setCurrentItem(startingItem, false);
        storyPager.post(() -> {
            if (storyPager == null || storyPagerAdapter == null) return;
            activateStoryPage(storyPager.getCurrentItem());
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void configureNestedStoryScroll(@NonNull StoryPageHolder holder) {
        final float[] downY = new float[1];
        holder.pageScroll.setOnTouchListener((view, event) -> {
            if (storyPager == null) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downY[0] = event.getY();
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float delta = downY[0] - event.getY();
                int direction = delta > 0f ? 1 : -1;
                boolean letPageScroll = Math.abs(delta) > 0f
                        && holder.pageScroll.canScrollVertically(direction);
                view.getParent().requestDisallowInterceptTouchEvent(letPageScroll);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private void updateStoryPagingProgress(int position, float positionOffset) {
        if (storyPagerAdapter == null || storyPagerAdapter.getItemCount() == 0) return;
        int lowerPosition = Math.max(0,
                Math.min(position, storyPagerAdapter.getItemCount() - 1));
        int upperPosition = Math.min(lowerPosition + 1, storyPagerAdapter.getItemCount() - 1);
        StoryPageEntry lower = storyPagerAdapter.getEntry(lowerPosition);
        StoryPageEntry upper = storyPagerAdapter.getEntry(upperPosition);
        float clampedOffset = Math.max(0f, Math.min(1f, positionOffset));
        host.setLinkSummaryStoryPagingAlphas(
                lower.story.id,
                upperPosition == lowerPosition ? 0f : clampedOffset,
                upperPosition == lowerPosition ? NO_STORY_ID : upper.story.id,
                upperPosition == lowerPosition ? 1f : 1f - clampedOffset);

        float pagerPosition = lowerPosition + clampedOffset;
        scrollStoryListForPagerDelta(lastStoryPagerPosition, pagerPosition);
        lastStoryPagerPosition = pagerPosition;
    }

    private void scrollStoryListForPagerDelta(float previousPosition, float currentPosition) {
        if (storyPagerAdapter == null || Float.isNaN(previousPosition)
                || previousPosition == currentPosition) {
            return;
        }
        float cursor = previousPosition;
        if (currentPosition > previousPosition) {
            while (cursor < currentPosition) {
                int segment = Math.min((int) Math.floor(cursor),
                        storyPagerAdapter.getItemCount() - 2);
                if (segment < 0) break;
                float end = Math.min(currentPosition, segment + 1f);
                addStoryListScrollForSegment(segment, end - cursor);
                cursor = end;
            }
        } else {
            while (cursor > currentPosition) {
                int segment = Math.min((int) Math.ceil(cursor) - 1,
                        storyPagerAdapter.getItemCount() - 2);
                if (segment < 0) break;
                float end = Math.max(currentPosition, segment);
                addStoryListScrollForSegment(segment, end - cursor);
                cursor = end;
            }
        }
    }

    private void addStoryListScrollForSegment(int segment, float pageDelta) {
        if (storyPagerAdapter == null || segment < 0
                || segment + 1 >= storyPagerAdapter.getItemCount()) {
            return;
        }
        StoryPageEntry first = storyPagerAdapter.getEntry(segment);
        StoryPageEntry second = storyPagerAdapter.getEntry(segment + 1);
        int distance = host.getLinkSummaryStoryPagingDistance(
                first.story.id, second.story.id);
        if (distance <= 0) return;
        pendingStoryListScrollPixels += pageDelta * distance;
        int wholePixels = pendingStoryListScrollPixels > 0f
                ? (int) Math.floor(pendingStoryListScrollPixels)
                : (int) Math.ceil(pendingStoryListScrollPixels);
        if (wholePixels != 0) {
            host.scrollLinkSummaryStoryListBy(wholePixels);
            pendingStoryListScrollPixels -= wholePixels;
        }
    }

    private void activateStoryPage(int position) {
        if (storyPager == null || storyPagerAdapter == null
                || position < 0 || position >= storyPagerAdapter.getItemCount()) {
            return;
        }
        StoryPageHolder holder = storyPagerAdapter.getBoundHolder(position);
        if (holder == null) {
            storyPager.post(() -> activateStoryPage(position));
            return;
        }
        currentStoryPage = holder;
        storyBinding = holder.content;
        card = holder.pageCard;
        visibleStoryId = holder.story == null ? NO_STORY_ID : holder.story.id;
        visibleStoryPosition = holder.sourcePosition;
        visibleUrl = holder.pageUrl;
        fallbackTitle = holder.pageFallbackTitle;

        restoreStorySharedElementAlphas();
        sourceView = visibleStoryId == NO_STORY_ID
                ? null : host.findLinkSummarySourceView(visibleStoryId);
        storyImageSourceView = null;
        storyTitleSourceView = null;
        storyMetaSourceView = null;
        storyImageSourceAlpha = 1f;
        storyTitleSourceAlpha = 1f;
        storyMetaSourceAlpha = 1f;
        if (visibleStoryId != NO_STORY_ID) {
            captureStorySharedElements(
                    host.findLinkSummaryStorySharedElements(visibleStoryId), false);
        }

        if (!enterTransitionStarted) {
            holder.pageCard.setVisibility(View.INVISIBLE);
            seedStoryPreviewFromSource(holder);
            startEnterTransition();
        }
    }

    private void seedStoryPreviewFromSource(@NonNull StoryPageHolder page) {
        if (storyImageSourceView == null) return;
        Drawable sourceDrawable = storyImageSourceView.getDrawable();
        if (sourceDrawable == null) return;

        ImageView preview = page.content.storyLinkPreview;
        Drawable previewDrawable = preview.getDrawable();
        if (previewDrawable == null) {
            previewDrawable = copyDrawable(sourceDrawable, page.pageCard.getResources());
            preview.setImageDrawable(previewDrawable);
        }
        preview.setVisibility(View.VISIBLE);
        page.content.storyLinkPreviewContainer.setVisibility(View.VISIBLE);
        stopPreviewShimmer(page.content.storyLinkPreviewShimmer);
        configureSeededStoryPreviewHeight(page, previewDrawable);
    }

    private void captureStorySharedElements(
            @Nullable StorySharedElements sharedElements,
            boolean onlyMissing) {
        if (sharedElements == null) return;
        if ((!onlyMissing || !isUsableTransitionView(storyImageSourceView))
                && isUsableStoryImageSource(sharedElements.image)) {
            storyImageSourceView = sharedElements.image;
            storyImageSourceAlpha = sharedElements.image.getAlpha();
        }
        if ((!onlyMissing || !isUsableTransitionView(storyTitleSourceView))
                && isUsableStorySharedSource(sharedElements.title)) {
            storyTitleSourceView = sharedElements.title;
            storyTitleSourceAlpha = sharedElements.title.getAlpha();
        }
        if ((!onlyMissing || !isUsableTransitionView(storyMetaSourceView))
                && isUsableStorySharedSource(sharedElements.meta)) {
            storyMetaSourceView = sharedElements.meta;
            storyMetaSourceAlpha = sharedElements.meta.getAlpha();
        }
    }

    private void resolveStorySharedElementsIfNeeded() {
        if (visibleStoryId == NO_STORY_ID
                || (isUsableTransitionView(storyImageSourceView)
                && isUsableTransitionView(storyTitleSourceView)
                && isUsableTransitionView(storyMetaSourceView))) {
            return;
        }
        captureStorySharedElements(
                host.findLinkSummaryStorySharedElements(visibleStoryId), true);
    }

    private void configureSeededStoryPreviewHeight(
            @NonNull StoryPageHolder page,
            @NonNull Drawable drawable) {
        ViewGroup.LayoutParams cardParams = page.pageCard.getLayoutParams();
        int width = cardParams == null ? 0 : cardParams.width;
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (width <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) return;

        int defaultHeight = Utils.pxFromDpInt(
                page.pageCard.getResources(), STORY_PREVIEW_DEFAULT_HEIGHT_DP);
        int targetHeight = Math.min(defaultHeight,
                Math.max(1, Math.round(width * intrinsicHeight / (float) intrinsicWidth)));
        ViewGroup.LayoutParams previewParams =
                page.content.storyLinkPreviewContainer.getLayoutParams();
        if (previewParams.height != targetHeight) {
            previewParams.height = targetHeight;
            page.content.storyLinkPreviewContainer.setLayoutParams(previewParams);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    void showReference(CollectedReferenceLinks.ReferenceLink link, @Nullable View source) {
        Context context = host.getLinkSummaryContext();
        if (context == null || link == null || TextUtils.isEmpty(link.getUrl()) || !prepareOverlay(source)) {
            return;
        }
        visibleUrl = link.getUrl();
        fallbackTitle = firstNonEmpty(link.getResolvedTitle(), link.getLabel(), link.getUrl());
        referenceBinding = ReferenceLinkSummaryContentBinding.inflate(
                LayoutInflater.from(context), binding.linkSummaryBody, true);
        applyReferenceTypography();
        referenceBinding.referenceLinkDomain.setText(safeDomain(link.getUrl()));
        FaviconLoader.loadFavicon(link.getUrl(), referenceBinding.referenceLinkFavicon, context,
                SettingsUtils.getPreferredFaviconProvider(context));
        referenceBinding.referenceLinkOpen.setOnClickListener(v -> Utils.openLinkMaybeHN(v.getContext(), visibleUrl));
        loadReferenceSummary();
        startEnterTransition();
    }

    void showReference(String url, String title, @Nullable View source) {
        showReference(url, title, source, null);
    }

    void showReference(
            String url,
            String title,
            @Nullable View source,
            @Nullable RectF sourceBounds) {
        Context context = host.getLinkSummaryContext();
        if (context == null || TextUtils.isEmpty(url)
                || !prepareOverlay(source, sourceBounds)) {
            return;
        }
        visibleUrl = url;
        fallbackTitle = firstNonEmpty(title, url);
        referenceBinding = ReferenceLinkSummaryContentBinding.inflate(
                LayoutInflater.from(context), binding.linkSummaryBody, true);
        applyReferenceTypography();
        referenceBinding.referenceLinkDomain.setText(safeDomain(url));
        FaviconLoader.loadFavicon(url, referenceBinding.referenceLinkFavicon, context,
                SettingsUtils.getPreferredFaviconProvider(context));
        referenceBinding.referenceLinkOpen.setOnClickListener(v -> Utils.openLinkMaybeHN(v.getContext(), visibleUrl));
        loadReferenceSummary();
        startEnterTransition();
    }

    private void applyStoryTypography(@NonNull StoryPageHolder page) {
        FontUtils.setLinkSummaryStoryTitleTypeface(page.content.storyLinkTitle);
        FontUtils.setLinkSummaryMetaTypeface(page.content.storyLinkMeta);
        FontUtils.setLinkSummaryBodyTypeface(page.content.storyLinkDescription);
        FontUtils.setLinkSummaryErrorTypeface(page.content.storyLinkError);
    }

    private void applyReferenceTypography() {
        FontUtils.setLinkSummaryMetaTypeface(referenceBinding.referenceLinkDomain);
        FontUtils.setLinkSummaryReferenceTitleTypeface(referenceBinding.referenceLinkTitle);
        FontUtils.setLinkSummaryBodyTypeface(referenceBinding.referenceLinkDescription);
        FontUtils.setLinkSummaryReferenceTitleTypeface(referenceBinding.referenceLinkErrorTitle);
        FontUtils.setLinkSummaryErrorTypeface(referenceBinding.referenceLinkError);
        FontUtils.setLinkSummaryButtonTypeface(referenceBinding.referenceLinkRetry);
        FontUtils.setLinkSummaryButtonTypeface(referenceBinding.referenceLinkOpen);
    }

    @SuppressLint("ClickableViewAccessibility")
    void showImage(String imageUrl, String description, @Nullable ImageView source) {
        Context context = host.getLinkSummaryContext();
        Drawable sourceDrawable = source == null ? null : source.getDrawable();
        if (context == null || (TextUtils.isEmpty(imageUrl) && sourceDrawable == null)
                || !prepareOverlay(source)) {
            return;
        }
        visibleUrl = imageUrl;
        fallbackTitle = description;
        imageBinding = ImageOnlyOverlayContentBinding.inflate(
                LayoutInflater.from(context), binding.linkSummaryBody, true);
        Integer imageBackgroundColor = host.getLinkSummaryImageBackgroundColor();
        if (imageBackgroundColor != null && card != null) {
            card.setCardBackgroundColor(imageBackgroundColor);
        }
        imageBinding.imageOnlyPreview.setContentDescription(description);
        binding.linkSummaryScroll.setVerticalScrollBarEnabled(false);
        binding.linkSummaryScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        if (sourceDrawable != null) {
            Drawable.ConstantState constantState = sourceDrawable.getConstantState();
            Drawable overlayDrawable = constantState == null
                    ? sourceDrawable
                    : constantState.newDrawable(context.getResources()).mutate();
            bindImageOnlyDrawable(overlayDrawable);
            return;
        }

        ImageView image = imageBinding.imageOnlyPreview;
        ImageRequest request = new ImageRequest.Builder(context)
                .data(imageUrl)
                .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .allowHardware(false)
                .target(new ImageViewTarget(image) {
                    @Override public void onSuccess(Drawable result) {
                        super.onSuccess(result);
                        if (imageBinding != null) {
                            bindImageOnlyDrawable(result);
                        }
                    }

                    @Override public void onError(Drawable error) {
                        super.onError(error);
                        removeNow();
                    }
                }).build();
        Coil.imageLoader(context).enqueue(request);
    }

    private void bindImageOnlyDrawable(Drawable drawable) {
        if (imageBinding == null || drawable == null) return;
        imageBinding.imageOnlyPreview.setImageDrawable(drawable);
        configureImageOnlySize(drawable);
        startEnterTransition();
    }

    private void configureImageOnlySize(Drawable drawable) {
        if (binding == null || card == null || imageBinding == null) return;
        binding.linkSummaryContent.post(() -> {
            if (binding == null || card == null || imageBinding == null) return;
            Context context = card.getContext();
            int horizontalPadding = binding.linkSummaryContent.getPaddingLeft()
                    + binding.linkSummaryContent.getPaddingRight();
            int verticalPadding = binding.linkSummaryContent.getPaddingTop()
                    + binding.linkSummaryContent.getPaddingBottom();
            int availableWidth = binding.linkSummaryContent.getWidth() - horizontalPadding;
            int availableHeight = binding.linkSummaryContent.getHeight() - verticalPadding;
            if (availableWidth <= 0) {
                availableWidth = context.getResources().getDisplayMetrics().widthPixels - horizontalPadding;
            }
            if (availableHeight <= 0) {
                availableHeight = context.getResources().getDisplayMetrics().heightPixels - verticalPadding;
            }
            int maxWidth = Utils.pxFromDpInt(context.getResources(),
                    Utils.isTablet(context.getResources()) ? 640 : 520);
            availableWidth = Math.max(1, Math.min(availableWidth, maxWidth));
            availableHeight = Math.max(1, availableHeight);

            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                intrinsicWidth = 16;
                intrinsicHeight = 9;
            }
            float scale = Math.min(
                    availableWidth / (float) intrinsicWidth,
                    availableHeight / (float) intrinsicHeight);
            int targetWidth = Math.max(1, Math.round(intrinsicWidth * scale));
            int targetHeight = Math.max(1, Math.round(intrinsicHeight * scale));

            FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
            cardParams.width = targetWidth;
            cardParams.height = targetHeight;
            card.setLayoutParams(cardParams);
            ViewGroup.LayoutParams scrollParams = binding.linkSummaryScroll.getLayoutParams();
            scrollParams.height = targetHeight;
            binding.linkSummaryScroll.setLayoutParams(scrollParams);
            ViewGroup.LayoutParams rootParams = imageBinding.getRoot().getLayoutParams();
            rootParams.height = targetHeight;
            imageBinding.getRoot().setLayoutParams(rootParams);
            ViewGroup.LayoutParams imageParams = imageBinding.imageOnlyPreview.getLayoutParams();
            imageParams.height = targetHeight;
            imageBinding.imageOnlyPreview.setLayoutParams(imageParams);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean prepareOverlay(@Nullable View source) {
        return prepareOverlay(source, null);
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean prepareOverlay(
            @Nullable View source,
            @Nullable RectF sourceBounds) {
        Context context = host.getLinkSummaryContext();
        ViewGroup overlayHost = host.getLinkSummaryOverlayHost();
        if (context == null || overlayHost == null) {
            return false;
        }
        removeNow();
        LinkPositionSourceView linkSource = createLinkPositionSource(
                overlayHost, source, sourceBounds);
        sourceView = linkSource == null ? source : linkSource;
        dismissing = false;
        enterTransitionStarted = false;
        enterTransitionComplete = false;
        pendingReferenceStateChange = null;
        binding = LinkSummaryOverlayBinding.inflate(LayoutInflater.from(context), overlayHost, false);
        overlay = binding.getRoot();
        overlayHost.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cancelCurrentListTouch(overlayHost);
        card = binding.linkSummaryCard;
        card.setCardBackgroundColor(resolveDialogBackground(context, source));
        card.setStrokeWidth(0);
        card.setStrokeColor(Color.TRANSPARENT);
        configureOverlayInsets(binding.linkSummaryContent);
        configureCardWidth(card);
        binding.linkSummaryContent.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                              oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft
                    || bottom - top != oldBottom - oldTop) {
                refreshForLayout();
            }
        });
        binding.linkSummaryScrim.setOnClickListener(v -> dismiss(true));
        binding.linkSummaryContent.setOnClickListener(v -> dismiss(true));
        card.setOnTouchListener((v, event) -> true);
        host.syncLinkSummaryBackState();
        return true;
    }

    @Nullable
    private LinkPositionSourceView createLinkPositionSource(
            @NonNull ViewGroup overlayHost,
            @Nullable View source,
            @Nullable RectF sourceBounds) {
        if (!isUsableTransitionView(source) || sourceBounds == null || sourceBounds.isEmpty()) {
            return null;
        }

        RectF clippedBounds = new RectF(sourceBounds);
        if (!clippedBounds.intersect(0, 0, source.getWidth(), source.getHeight())) {
            return null;
        }
        int[] hostLocation = new int[2];
        int[] sourceLocation = new int[2];
        overlayHost.getLocationOnScreen(hostLocation);
        source.getLocationOnScreen(sourceLocation);
        int anchorSize = Math.max(1, Utils.pxFromDpInt(source.getResources(), 8));
        LinkPositionSourceView linkSource = new LinkPositionSourceView(source.getContext());
        overlayHost.addView(linkSource, new ViewGroup.LayoutParams(anchorSize, anchorSize));
        linkSource.setX(sourceLocation[0] - hostLocation[0]
                + clippedBounds.centerX() - anchorSize / 2f);
        linkSource.setY(sourceLocation[1] - hostLocation[1]
                + clippedBounds.centerY() - anchorSize / 2f);
        linkPositionSourceView = linkSource;
        return linkSource;
    }

    private void bindStoryPage(
            @NonNull StoryPageHolder page,
            @NonNull StoryPageEntry entry) {
        Context context = page.itemView.getContext();
        page.cancelRequests();
        page.story = entry.story;
        page.sourcePosition = entry.sourcePosition;
        page.pageUrl = entry.story.url;
        page.pageFallbackTitle = entry.story.title;
        page.pendingPreviewHide = false;
        page.pendingStateChange = null;
        page.pageCard.setVisibility(View.VISIBLE);
        page.pageCard.setAlpha(1f);
        page.pageCard.setScaleX(1f);
        page.pageCard.setScaleY(1f);
        page.pageCard.setTranslationX(0f);
        page.pageCard.setTranslationY(0f);
        page.pageCard.setCardBackgroundColor(
                host.resolveStoryCardBackgroundColor(entry.story));
        page.pageCard.setStrokeWidth(0);
        page.pageCard.setStrokeColor(Color.TRANSPARENT);
        configureStoryPageWidth(page);
        applyStoryTypography(page);
        resetStoryPageContent(page);
        bindStoryKnownContent(page, context, entry.story);
        configureStoryActions(page, context, entry.story);
        if (!entry.story.isLink) {
            bindTextStorySummary(page, entry.story);
            resizeStoryPage(page);
            return;
        }
        LinkSummaryLoader.Result cached = StoryPreviewImageLoader.getCachedLinkSummary(
                context, entry.story.url);
        if (cached != null) {
            bindStoryResult(page, entry.story, cached);
        } else {
            startStoryShimmers(page);
            if (entry.story.previewImageUrlLoaded) {
                if (TextUtils.isEmpty(entry.story.previewImageUrl)) {
                    hideStoryPreview(page);
                } else {
                    loadStoryImage(page, entry.story, entry.story.previewImageUrl);
                }
            } else if (!TextUtils.isEmpty(entry.story.pdfTitle)
                    || !TextUtils.isEmpty(entry.story.videoTitle)) {
                hideStoryPreview(page);
            }
            loadStorySummary(page, entry.story);
        }
        resizeStoryPage(page);
    }

    private void resetStoryPageContent(@NonNull StoryPageHolder page) {
        StoryLinkSummaryContentBinding content = page.content;
        content.storyLinkPreview.setTag(null);
        content.storyLinkPreview.setImageDrawable(null);
        content.storyLinkPreview.setVisibility(View.INVISIBLE);
        content.storyLinkPreviewContainer.setVisibility(View.VISIBLE);
        content.storyLinkDescription.setText(null);
        content.storyLinkDescription.setVisibility(View.GONE);
        content.storyLinkError.setText(null);
        content.storyLinkError.setVisibility(View.GONE);
        content.storyLinkFavicon.setImageDrawable(null);
        content.storyLinkFavicon.setVisibility(View.VISIBLE);
        page.pageScroll.scrollTo(0, 0);
    }

    private void bindStoryKnownContent(
            @NonNull StoryPageHolder page,
            @NonNull Context context,
            @NonNull Story story) {
        StoryLinkSummaryContentBinding content = page.content;
        setStoryTitle(page, story, firstNonEmpty(story.title, story.url));
        StringBuilder meta = new StringBuilder()
                .append(story.score)
                .append(story.score == 1 ? " point" : " points");
        FaviconLoader.loadFavicon(story.url, content.storyLinkFavicon, context,
                SettingsUtils.getPreferredFaviconProvider(context));
        if (story.isLink) {
            meta.append(" • ").append(safeDomain(story.url));
        } else {
            if (!TextUtils.isEmpty(story.by)) {
                meta.append(" • ").append(story.by);
            }
        }
        meta.append(" • ").append(story.getTimeFormatted());
        content.storyLinkMeta.setText(meta);
        content.storyLinkComments.setText(R.string.link_summary_comments);
    }

    private void bindTextStorySummary(
            @NonNull StoryPageHolder page,
            @NonNull Story story) {
        StoryLinkSummaryContentBinding content = page.content;
        hideStoryPreview(page);
        stopDescriptionShimmer(content.storyLinkDescriptionShimmer);
        content.storyLinkError.setVisibility(View.GONE);
        String summary = extractTextStorySummary(story.text);
        if (TextUtils.isEmpty(summary)) {
            content.storyLinkDescription.setVisibility(View.GONE);
            return;
        }
        content.storyLinkDescription.setText(summary);
        content.storyLinkDescription.setVisibility(View.VISIBLE);
    }

    private static String extractTextStorySummary(@Nullable String storyHtml) {
        if (TextUtils.isEmpty(storyHtml)) {
            return "";
        }
        String summary = Html.fromHtml(storyHtml, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (summary.length() <= TEXT_STORY_SUMMARY_MAX_CHARS) {
            return summary;
        }
        int end = TEXT_STORY_SUMMARY_MAX_CHARS;
        int minimumBoundary = Math.round(TEXT_STORY_SUMMARY_MAX_CHARS * 0.75f);
        for (int index = TEXT_STORY_SUMMARY_MAX_CHARS - 1;
             index >= minimumBoundary;
             index--) {
            if (Character.isWhitespace(summary.charAt(index))) {
                end = index;
                break;
            }
        }
        return summary.substring(0, end).trim() + "…";
    }

    private void configureStoryActions(
            @NonNull StoryPageHolder page,
            @NonNull Context context,
            @NonNull Story story) {
        StoryLinkSummaryContentBinding content = page.content;
        boolean hasAccount = AccountUtils.hasAccountDetails(context);
        content.storyLinkComments.setText(hasAccount
                ? String.valueOf(story.descendants)
                : context.getString(R.string.link_summary_comments));
        content.storyLinkComments.setContentDescription(
                "Comments (" + story.descendants + ")");
        content.storyLinkVoteSlot.setVisibility(hasAccount ? View.VISIBLE : View.GONE);
        content.storyLinkFavoriteSlot.setVisibility(hasAccount ? View.VISIBLE : View.GONE);
        content.storyLinkBookmark.setVisibility(
                SettingsUtils.shouldUseBookmarks(context) ? View.VISIBLE : View.GONE);
        refreshStoryActionButtons(page, context, story);

        content.storyLinkVote.setOnClickListener(v -> {
            if (storyVoteLoadingId == story.id) return;
            boolean selected = Utils.isUpvoted(context, story.id, false);
            storyVoteLoadingId = story.id;
            ImageButton button = content.storyLinkVote;
            LoadingIndicator loadingIndicator = content.storyLinkVoteLoading;
            showStoryActionLoading(button, loadingIndicator,
                    selected ? "Removing upvote" : "Upvoting");
            host.toggleStoryVote(story, page.sourcePosition, selected,
                    () -> finishStoryActionLoading(
                            page, context, story, button, loadingIndicator, true));
        });
        content.storyLinkRead.setOnClickListener(v -> {
            host.toggleStoryRead(story, page.sourcePosition);
            refreshStoryActionButtons(page, context, story);
        });
        content.storyLinkBookmark.setOnClickListener(v -> {
            boolean selected = Utils.isBookmarked(context, story.id);
            host.toggleStoryBookmark(story, page.sourcePosition, selected);
            refreshStoryActionButtons(page, context, story);
        });
        content.storyLinkFavorite.setOnClickListener(v -> {
            if (storyFavoriteLoadingId == story.id) return;
            boolean selected = Utils.isFavorited(context, story.id);
            storyFavoriteLoadingId = story.id;
            ImageButton button = content.storyLinkFavorite;
            LoadingIndicator loadingIndicator = content.storyLinkFavoriteLoading;
            showStoryActionLoading(button, loadingIndicator,
                    selected ? "Removing favorite" : "Adding favorite");
            host.toggleStoryFavorite(story, page.sourcePosition, selected,
                    () -> finishStoryActionLoading(
                            page, context, story, button, loadingIndicator, false));
        });
        content.storyLinkHeader.setContentDescription(
                (story.isLink ? "Open link: " : "Open discussion: ")
                        + firstNonEmpty(story.title, story.url));
        content.storyLinkHeader.setOnClickListener(v -> {
            navigateToStory(story, page.sourcePosition, story.isLink);
        });
        content.storyLinkComments.setOnClickListener(v -> {
            navigateToStory(story, page.sourcePosition, false);
        });
    }

    private void navigateToStory(Story story, int position, boolean showWebsite) {
        if (Utils.isTablet(host.requireLinkSummaryContext().getResources())) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> host.openStoryLinkSummary(story, position, showWebsite),
                    TRANSFORM_DURATION_MS);
            dismiss(true);
            return;
        }
        FrameLayout overlayToRemove = overlay;
        host.openStoryLinkSummary(story, position, showWebsite);
        if (overlayToRemove != null) {
            overlayToRemove.postDelayed(() -> {
                if (overlay == overlayToRemove) {
                    removeNow();
                }
            }, TRANSFORM_DURATION_MS + 120L);
        }
    }

    private void refreshStoryActionButtons(
            @NonNull StoryPageHolder page,
            @NonNull Context context,
            @NonNull Story story) {
        StoryLinkSummaryContentBinding content = page.content;
        setActionState(content.storyLinkVote, Utils.isUpvoted(context, story.id, false),
                R.drawable.ic_thumb_up, R.drawable.ic_thumb_up_filled, "Upvote", "Remove upvote");
        setActionState(content.storyLinkRead, story.clicked,
                R.drawable.ic_visibility, R.drawable.ic_visibility_off, "Mark as read", "Mark as unread");
        setActionState(content.storyLinkBookmark, Utils.isBookmarked(context, story.id),
                R.drawable.ic_bookmark, R.drawable.ic_bookmark_filled, "Bookmark", "Remove bookmark");
        setActionState(content.storyLinkFavorite, Utils.isFavorited(context, story.id),
                R.drawable.ic_star, R.drawable.ic_star_filled, "Favorite", "Remove favorite");
    }

    private void setActionState(ImageButton button,
                                boolean selected, int normalIcon, int selectedIcon,
                                String normalDescription, String selectedDescription) {
        Runnable apply = () -> {
            String description = selected ? selectedDescription : normalDescription;
            button.setImageResource(selected ? selectedIcon : normalIcon);
            button.setContentDescription(description);
            TooltipCompat.setTooltipText(button, description);
            button.setTag(selected);
        };
        Object previousState = button.getTag();
        if (!ViewCompat.isLaidOut(button)
                || !(previousState instanceof Boolean)
                || (Boolean) previousState == selected) {
            apply.run();
            return;
        }
        button.animate().cancel();
        button.animate().alpha(0f).scaleX(.82f).scaleY(.82f).setDuration(90)
                .withEndAction(() -> {
                    apply.run();
                    button.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();
                }).start();
    }

    private void showStoryActionLoading(ImageButton button,
                                        LoadingIndicator loadingIndicator,
                                        String description) {
        button.setEnabled(false);
        loadingIndicator.setContentDescription(description);
        animateActionViewOut(button, () -> {
            button.setVisibility(View.GONE);
            resetActionView(button);
            loadingIndicator.setVisibility(View.VISIBLE);
            animateActionViewIn(loadingIndicator, null);
        });
    }

    private void finishStoryActionLoading(@NonNull StoryPageHolder page,
                                          @NonNull Context context,
                                          @NonNull Story story,
                                          @Nullable ImageButton button,
                                          @Nullable LoadingIndicator loadingIndicator,
                                          boolean vote) {
        if (vote) {
            if (storyVoteLoadingId == story.id) {
                storyVoteLoadingId = NO_STORY_ID;
            }
        } else {
            if (storyFavoriteLoadingId == story.id) {
                storyFavoriteLoadingId = NO_STORY_ID;
            }
        }
        if (button == null || loadingIndicator == null
                || page.story != story || page.content.getRoot().getParent() == null) {
            return;
        }
        animateActionViewOut(loadingIndicator, () -> {
            loadingIndicator.setVisibility(View.GONE);
            resetActionView(loadingIndicator);
            button.setTag(null);
            button.setVisibility(View.VISIBLE);
            refreshStoryActionButtons(page, context, story);
            button.setEnabled(false);
            animateActionViewIn(button, () -> button.setEnabled(true));
        });
    }

    private void resetActionView(View view) {
        view.animate().cancel();
        view.animate().setListener(null);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private void animateActionViewOut(View view, Runnable afterOut) {
        view.animate().cancel();
        view.animate().alpha(0f)
                .scaleX(ACTION_SWAP_MIN_SCALE).scaleY(ACTION_SWAP_MIN_SCALE)
                .setDuration(ACTION_SWAP_OUT_DURATION_MS)
                .withEndAction(afterOut).start();
    }

    private void animateActionViewIn(View view, @Nullable Runnable afterIn) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.setScaleX(ACTION_SWAP_MIN_SCALE);
        view.setScaleY(ACTION_SWAP_MIN_SCALE);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(ACTION_SWAP_IN_DURATION_MS)
                .withEndAction(afterIn).start();
    }

    private void startStoryShimmers(@NonNull StoryPageHolder page) {
        StoryLinkSummaryContentBinding content = page.content;
        content.storyLinkDescriptionShimmer.setVisibility(View.VISIBLE);
        content.storyLinkDescriptionShimmer.startShimmer();
        content.storyLinkPreviewShimmer.setVisibility(View.VISIBLE);
        content.storyLinkPreviewShimmer.startShimmer();
        content.storyLinkDescription.setVisibility(View.GONE);
        content.storyLinkError.setVisibility(View.GONE);
    }

    private void loadStorySummary(
            @NonNull StoryPageHolder page,
            @NonNull Story story) {
        String requestedUrl = page.pageUrl;
        page.pageSummaryRequest = LinkSummaryLoader.load(
                host.getLinkSummaryContext(), requestedUrl, page.pageFallbackTitle,
                new LinkSummaryLoader.Callback() {
                    @Override public void onSuccess(@NonNull LinkSummaryLoader.Result result) {
                        if (page.story != story
                                || !TextUtils.equals(requestedUrl, page.pageUrl)) return;
                        page.pageSummaryRequest = null;
                        applyOrDeferStoryStateChange(
                                page, () -> bindStoryResult(page, story, result));
                    }
                    @Override public void onFailure(@NonNull String message) {
                        if (page.story != story
                                || !TextUtils.equals(requestedUrl, page.pageUrl)) return;
                        page.pageSummaryRequest = null;
                        applyOrDeferStoryStateChange(page, () -> {
                            beginStoryContentTransition(
                                    page, STORY_CONTENT_TRANSITION_DURATION_MS);
                            stopDescriptionShimmer(page.content.storyLinkDescriptionShimmer);
                            if (page.content.storyLinkPreview.getVisibility() != View.VISIBLE) {
                                hideStoryPreview(page);
                            }
                            if (isPdfContentTypeError(message)) {
                                page.content.storyLinkError.setVisibility(View.GONE);
                            } else {
                                page.content.storyLinkError.setText(message);
                                page.content.storyLinkError.setVisibility(View.VISIBLE);
                            }
                            resizeStoryPage(page);
                        });
                    }
                });
    }

    private void bindStoryResult(
            @NonNull StoryPageHolder page,
            @NonNull Story story,
            @NonNull LinkSummaryLoader.Result result) {
        if (!TextUtils.isEmpty(result.finalUrl)) page.pageUrl = result.finalUrl;
        if (page == currentStoryPage) visibleUrl = page.pageUrl;
        beginStoryContentTransition(page, STORY_CONTENT_TRANSITION_DURATION_MS);
        setStoryTitle(page, story,
                firstNonEmpty(result.title, story.title, page.pageUrl));
        stopDescriptionShimmer(page.content.storyLinkDescriptionShimmer);
        if (!TextUtils.isEmpty(result.description)) {
            page.content.storyLinkDescription.setText(result.description);
            page.content.storyLinkDescription.setVisibility(View.VISIBLE);
        } else {
            page.content.storyLinkDescription.setVisibility(View.GONE);
        }
        String imageUrl = firstNonEmpty(result.imageUrl, story.previewImageUrl);
        if (TextUtils.isEmpty(imageUrl)) {
            hideStoryPreview(page);
        } else {
            story.previewImageUrl = imageUrl;
            story.previewImageUrlLoaded = true;
            if (!imageUrl.equals(page.content.storyLinkPreview.getTag())
                    || page.content.storyLinkPreview.getVisibility() != View.VISIBLE) {
                loadStoryImage(page, story, imageUrl);
            }
        }
        resizeStoryPage(page);
    }

    private void loadStoryImage(
            @NonNull StoryPageHolder page,
            @NonNull Story story,
            @NonNull String imageUrl) {
        ImageView imageView = page.content.storyLinkPreview;
        Drawable requestFallback = imageView.getDrawable();
        imageView.setTag(imageUrl);
        page.content.storyLinkPreviewContainer.setVisibility(View.VISIBLE);
        page.content.storyLinkPreviewShimmer.setVisibility(View.VISIBLE);
        page.content.storyLinkPreviewShimmer.startShimmer();
        ImageRequest request = new ImageRequest.Builder(imageView.getContext())
                .data(imageUrl).setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .allowHardware(false).crossfade(true)
                .target(new ImageViewTarget(imageView) {
                    @Override public void onStart(Drawable placeholder) {
                        if (requestFallback == null
                                && getStoryImageSourceDrawable(page) == null) {
                            super.onStart(placeholder);
                        }
                    }
                    @Override public void onSuccess(Drawable result) {
                        if (!isCurrentStoryImageTarget(page, story, imageView, imageUrl)) return;
                        super.onSuccess(result);
                        PreviewImageLayoutUtils.applyWideImageHeight(
                                imageView,
                                page.content.storyLinkPreviewContainer,
                                result,
                                STORY_PREVIEW_DEFAULT_HEIGHT_DP);
                        page.content.storyLinkPreviewShimmer.stopShimmer();
                        page.content.storyLinkPreviewShimmer.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                        int base = PreviewImageTintUtils.getTintBaseColor(imageView.getContext());
                        PreviewImageTintUtils.updateStoryPreviewImageTintColor(story, imageUrl, result, base,
                                SettingsUtils.getPreferredPaletteTintConfigKey(imageView.getContext()));
                        imageView.post(() -> resizeStoryPage(page));
                    }
                    @Override public void onError(Drawable error) {
                        if (!isCurrentStoryImageTarget(page, story, imageView, imageUrl)) return;
                        Drawable retainedDrawable = imageView.getDrawable();
                        if (retainedDrawable == null) {
                            retainedDrawable = requestFallback;
                        }
                        if (retainedDrawable == null) {
                            Drawable sourceDrawable = getStoryImageSourceDrawable(page);
                            if (sourceDrawable != null) {
                                retainedDrawable = copyDrawable(
                                        sourceDrawable, imageView.getResources());
                            }
                        }
                        if (retainedDrawable != null) {
                            imageView.setImageDrawable(retainedDrawable);
                            PreviewImageLayoutUtils.applyWideImageHeight(
                                    imageView,
                                    page.content.storyLinkPreviewContainer,
                                    retainedDrawable,
                                    STORY_PREVIEW_DEFAULT_HEIGHT_DP);
                            stopPreviewShimmer(page.content.storyLinkPreviewShimmer);
                            page.content.storyLinkPreviewContainer.setVisibility(View.VISIBLE);
                            imageView.setVisibility(View.VISIBLE);
                            resizeStoryPage(page);
                            return;
                        }

                        super.onError(null);
                        beginStoryContentTransition(
                                page, STORY_CONTENT_TRANSITION_DURATION_MS);
                        stopPreviewShimmer(page.content.storyLinkPreviewShimmer);
                        hideStoryPreview(page);
                        resizeStoryPage(page);
                    }
                }).build();
        Coil.imageLoader(imageView.getContext()).enqueue(request);
    }

    @Nullable
    private Drawable getStoryImageSourceDrawable(@NonNull StoryPageHolder page) {
        if (page == currentStoryPage && storyImageSourceView != null) {
            return storyImageSourceView.getDrawable();
        }
        if (page.story == null) return null;
        StorySharedElements elements =
                host.findLinkSummaryStorySharedElements(page.story.id);
        return elements == null || elements.image == null
                ? null : elements.image.getDrawable();
    }

    private static Drawable copyDrawable(Drawable source, Resources resources) {
        Drawable.ConstantState constantState = source.getConstantState();
        return constantState == null
                ? source
                : constantState.newDrawable(resources).mutate();
    }

    private boolean isCurrentStoryImageTarget(
            @NonNull StoryPageHolder page,
            @NonNull Story story,
            @NonNull ImageView imageView,
            @NonNull String imageUrl) {
        return page.story == story
                && page.content.storyLinkPreview == imageView
                && imageUrl.equals(imageView.getTag());
    }

    private void loadReferenceSummary() {
        loadReferenceSummary(false);
    }

    private void retryReferenceSummary() {
        if (referenceBinding == null || summaryRequest != null) return;
        Context context = referenceBinding.referenceLinkRetry.getContext();
        if (!Utils.isNetworkAvailable(context)) {
            referenceBinding.referenceLinkError.announceForAccessibility(
                    context.getString(R.string.link_summary_offline_message));
            return;
        }
        setReferenceRetryLoading(true);
        loadReferenceSummary(true);
    }

    private void loadReferenceSummary(boolean preserveErrorState) {
        String requestedUrl = visibleUrl;
        if (!preserveErrorState) {
            showReferenceLoadingState();
        }
        summaryRequest = LinkSummaryLoader.load(host.getLinkSummaryContext(), requestedUrl, fallbackTitle,
                new LinkSummaryLoader.Callback() {
                    @Override public void onSuccess(@NonNull LinkSummaryLoader.Result result) {
                        if (referenceBinding == null || !TextUtils.equals(requestedUrl, visibleUrl)) return;
                        summaryRequest = null;
                        setReferenceRetryLoading(false);
                        animateReferenceStateChange(() -> bindReferenceResult(result));
                    }
                    @Override public void onFailure(@NonNull String message) {
                        if (referenceBinding == null || !TextUtils.equals(requestedUrl, visibleUrl)) return;
                        summaryRequest = null;
                        setReferenceRetryLoading(false);
                        if (isPdfContentTypeError(message)) {
                            animateReferenceStateChange(() -> bindReferenceNoSummary());
                            return;
                        }
                        if (preserveErrorState) {
                            animateReferenceStateChange(() -> bindReferenceError(message));
                            return;
                        }
                        animateReferenceStateChange(() -> {
                            bindReferenceFallbackContent();
                            referenceBinding.referenceLinkErrorContainer.setAlpha(1f);
                            bindReferenceError(message);
                        });
                    }
                });
    }

    private void showReferenceLoadingState() {
        if (referenceBinding == null) return;
        referenceBinding.referenceLinkRetry.setOnClickListener(v -> retryReferenceSummary());
        referenceBinding.referenceLinkErrorContainer.animate().cancel();
        referenceBinding.referenceLinkErrorContainer.setAlpha(1f);
        referenceBinding.referenceLinkErrorContainer.setVisibility(View.GONE);
        referenceBinding.referenceLinkTitle.setVisibility(View.GONE);
        referenceBinding.referenceLinkDescription.setVisibility(View.GONE);
        referenceBinding.referenceLinkPreview.setVisibility(View.INVISIBLE);
        setReferencePreviewVisible(true);
        referenceBinding.referenceLinkPreviewShimmer.setVisibility(View.VISIBLE);
        referenceBinding.referenceLinkTitleShimmer.setVisibility(View.VISIBLE);
        referenceBinding.referenceLinkDescriptionShimmer.setVisibility(View.VISIBLE);
        startReferenceShimmers();
        resizeScroll();
    }

    private void setReferenceRetryLoading(boolean loading) {
        if (referenceBinding == null) return;
        referenceBinding.referenceLinkRetry.setEnabled(!loading);
        referenceBinding.referenceLinkRetry.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        referenceBinding.referenceLinkRetryLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void bindReferenceError(@NonNull String loaderMessage) {
        Context context = referenceBinding.referenceLinkError.getContext();
        boolean offline = !Utils.isNetworkAvailable(context);
        referenceBinding.referenceLinkErrorTitle.setText(offline
                ? R.string.link_summary_offline_title
                : R.string.link_summary_error_title);
        referenceBinding.referenceLinkError.setText(offline
                ? context.getText(R.string.link_summary_offline_message)
                : getReferenceErrorMessage(loaderMessage));
        referenceBinding.referenceLinkRetryContainer.setVisibility(
                offline || isReferenceErrorRetryable(loaderMessage)
                        ? View.VISIBLE
                        : View.GONE);
        referenceBinding.referenceLinkErrorContainer.setVisibility(View.VISIBLE);
    }

    private void bindReferenceNoSummary() {
        bindReferenceFallbackContent();
        referenceBinding.referenceLinkErrorContainer.animate().cancel();
        referenceBinding.referenceLinkErrorContainer.setAlpha(1f);
        referenceBinding.referenceLinkErrorContainer.setVisibility(View.GONE);
        referenceBinding.referenceLinkRetryContainer.setVisibility(View.GONE);
    }

    private void bindReferenceFallbackContent() {
        stopReferenceShimmers();
        referenceBinding.referenceLinkTitle.setAlpha(1f);
        referenceBinding.referenceLinkTitle.setText(firstNonEmpty(fallbackTitle, visibleUrl));
        referenceBinding.referenceLinkTitle.setVisibility(View.VISIBLE);
        referenceBinding.referenceLinkDescription.setVisibility(View.GONE);
        setReferencePreviewVisible(false);
    }

    private boolean isReferenceErrorRetryable(@NonNull String loaderMessage) {
        return !loaderMessage.startsWith("This link contains ");
    }

    private static boolean isPdfContentTypeError(@NonNull String loaderMessage) {
        return PDF_CONTENT_TYPE_ERROR.equalsIgnoreCase(loaderMessage.trim());
    }

    private CharSequence getReferenceErrorMessage(@NonNull String loaderMessage) {
        if (loaderMessage.startsWith("The page returned HTTP ")
                || loaderMessage.startsWith("This link contains ")
                || loaderMessage.startsWith("This link does not use ")
                || loaderMessage.startsWith("The page is too large ")) {
            return loaderMessage;
        }
        return referenceBinding.referenceLinkError.getContext()
                .getString(R.string.link_summary_error_message);
    }

    private void bindReferenceResult(LinkSummaryLoader.Result result) {
        if (!TextUtils.isEmpty(result.finalUrl)) visibleUrl = result.finalUrl;
        referenceBinding.referenceLinkErrorContainer.setVisibility(View.GONE);
        referenceBinding.referenceLinkTitleShimmer.stopShimmer();
        referenceBinding.referenceLinkTitleShimmer.setVisibility(View.GONE);
        referenceBinding.referenceLinkTitle.setAlpha(1f);
        referenceBinding.referenceLinkTitle.setText(firstNonEmpty(result.title, fallbackTitle, visibleUrl));
        referenceBinding.referenceLinkTitle.setVisibility(View.VISIBLE);
        stopDescriptionShimmer(referenceBinding.referenceLinkDescriptionShimmer);
        if (!TextUtils.isEmpty(result.description)) {
            referenceBinding.referenceLinkDescription.setAlpha(1f);
            referenceBinding.referenceLinkDescription.setText(result.description);
            referenceBinding.referenceLinkDescription.setVisibility(View.VISIBLE);
        } else {
            referenceBinding.referenceLinkDescription.setVisibility(View.GONE);
        }
        if (TextUtils.isEmpty(result.imageUrl)) {
            stopPreviewShimmer(referenceBinding.referenceLinkPreviewShimmer);
            setReferencePreviewVisible(false);
        } else {
            loadReferenceImage(result.imageUrl);
        }
        resizeScroll();
    }

    private void loadReferenceImage(String imageUrl) {
        ImageView image = referenceBinding.referenceLinkPreview;
        ImageRequest request = new ImageRequest.Builder(image.getContext())
                .data(imageUrl).setHeader("User-Agent", NetworkComponent.USER_AGENT).crossfade(true)
                .target(new ImageViewTarget(image) {
                    @Override public void onSuccess(Drawable result) {
                        super.onSuccess(result);
                        if (referenceBinding != null) {
                            stopPreviewShimmer(referenceBinding.referenceLinkPreviewShimmer);
                            setReferencePreviewVisible(true);
                            image.setVisibility(View.VISIBLE);
                            configureReferenceImageInteraction(image);
                        }
                    }
                    @Override public void onError(Drawable error) {
                        super.onError(null);
                        if (referenceBinding != null) {
                            stopPreviewShimmer(referenceBinding.referenceLinkPreviewShimmer);
                            setReferencePreviewVisible(false);
                            resizeScroll();
                        }
                    }
                }).build();
        Coil.imageLoader(image.getContext()).enqueue(request);
    }

    private void setReferencePreviewVisible(boolean visible) {
        if (referenceBinding == null) return;
        referenceBinding.referenceLinkPreviewContainer.setVisibility(
                visible ? View.VISIBLE : View.GONE);

        ViewGroup.LayoutParams rawParams =
                referenceBinding.referenceLinkMetadataContainer.getLayoutParams();
        if (!(rawParams instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) rawParams;
        int startMargin = visible ? 0 : Utils.pxFromDpInt(
                referenceBinding.getRoot().getResources(), 20);
        if (marginParams.getMarginStart() != startMargin) {
            marginParams.setMarginStart(startMargin);
            referenceBinding.referenceLinkMetadataContainer.setLayoutParams(marginParams);
        }
    }

    private void configureReferenceImageInteraction(@NonNull ImageView image) {
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        image.setContentDescription(image.getContext().getString(R.string.link_summary_expand_image));
        image.setOnClickListener(view -> setReferenceImageExpanded(!referenceImageExpanded));
    }

    private void setReferenceImageExpanded(boolean expanded) {
        if (referenceBinding == null || binding == null || card == null
                || expanded == referenceImageExpanded) {
            return;
        }
        ImageView image = referenceBinding.referenceLinkPreview;
        Drawable drawable = image.getDrawable();
        if (drawable == null || !ViewCompat.isLaidOut(referenceBinding.getRoot())) {
            return;
        }

        int width = referenceBinding.getRoot().getWidth();
        if (width <= 0) {
            width = card.getWidth();
        }
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (width <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return;
        }
        int targetWidth = width;

        referenceImageExpanded = expanded;
        image.setContentDescription(image.getContext().getString(expanded
                ? R.string.link_summary_collapse_image
                : R.string.link_summary_expand_image));
        LinearLayout metadata = referenceBinding.referenceLinkMetadataContainer;
        metadata.animate().cancel();
        metadata.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(REFERENCE_METADATA_FADE_OUT_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    if (referenceBinding == null || binding == null || card == null
                            || referenceImageExpanded != expanded) {
                        metadata.setAlpha(1f);
                        return;
                    }
                    startReferenceImageBoundsTransition(
                            image, expanded, targetWidth, intrinsicWidth, intrinsicHeight);
                })
                .start();
    }

    private void startReferenceImageBoundsTransition(
            @NonNull ImageView image,
            boolean expanded,
            int width,
            int intrinsicWidth,
            int intrinsicHeight) {
        AutoTransition transition = new AutoTransition();
        transition.setDuration(REFERENCE_IMAGE_TRANSITION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        TransitionManager.beginDelayedTransition(binding.linkSummaryContent, transition);

        int collapsedSize = Utils.pxFromDpInt(
                image.getResources(), REFERENCE_IMAGE_COLLAPSED_SIZE_DP);
        int expandedHeight = Math.max(1,
                Math.round(width * intrinsicHeight / (float) intrinsicWidth));
        int standardMargin = Utils.pxFromDpInt(image.getResources(), 20);
        int imageTextGap = Utils.pxFromDpInt(image.getResources(), expanded ? 18 : 16);

        LinearLayout header = referenceBinding.referenceLinkHeader;
        header.setOrientation(expanded ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                expanded ? ViewGroup.LayoutParams.MATCH_PARENT : collapsedSize,
                expanded ? expandedHeight : collapsedSize);
        imageParams.setMarginStart(expanded ? 0 : standardMargin);
        imageParams.topMargin = expanded ? 0 : standardMargin;
        imageParams.setMarginEnd(expanded ? 0 : imageTextGap);
        referenceBinding.referenceLinkPreviewContainer.setLayoutParams(imageParams);

        LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                expanded ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                expanded ? 0f : 1f);
        metadataParams.setMarginStart(expanded ? standardMargin : 0);
        metadataParams.topMargin = expanded ? imageTextGap : standardMargin;
        metadataParams.setMarginEnd(standardMargin);
        referenceBinding.referenceLinkMetadataContainer.setLayoutParams(metadataParams);

        ViewGroup.LayoutParams scrollParams = binding.linkSummaryScroll.getLayoutParams();
        scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.linkSummaryScroll.setLayoutParams(scrollParams);
        animateReferenceImageCorners(image, expanded);
        LinearLayout metadata = referenceBinding.referenceLinkMetadataContainer;
        metadata.animate().cancel();
        metadata.setAlpha(0f);
        metadata.animate()
                .alpha(1f)
                .setStartDelay(REFERENCE_IMAGE_TRANSITION_DURATION_MS
                        - REFERENCE_METADATA_FADE_IN_DURATION_MS)
                .setDuration(REFERENCE_METADATA_FADE_IN_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
        resizeScroll();
    }

    private void animateReferenceImageCorners(@NonNull ImageView image, boolean expanded) {
        float collapsedRadius = image.getResources().getDimension(
                R.dimen.story_preview_image_corner_radius);
        float expandedTopRadius = Utils.pxFromDpInt(
                image.getResources(), CARD_CORNER_RADIUS_DP);
        float startTopRadius = collapsedRadius;
        float startBottomRadius = collapsedRadius;
        if (image.getOutlineProvider() instanceof SplitCornerOutlineProvider) {
            SplitCornerOutlineProvider currentProvider =
                    (SplitCornerOutlineProvider) image.getOutlineProvider();
            startTopRadius = currentProvider.getTopRadius();
            startBottomRadius = currentProvider.getBottomRadius();
        }
        if (referenceImageCornerAnimator != null) {
            referenceImageCornerAnimator.cancel();
        }
        float targetTopRadius = expanded ? expandedTopRadius : collapsedRadius;
        float targetBottomRadius = expanded ? 0f : collapsedRadius;
        SplitCornerOutlineProvider outlineProvider = new SplitCornerOutlineProvider(
                startTopRadius, startBottomRadius);
        image.setBackground(null);
        image.setOutlineProvider(outlineProvider);
        image.setClipToOutline(true);
        referenceImageCornerAnimator = ValueAnimator.ofFloat(0f, 1f);
        referenceImageCornerAnimator.setDuration(REFERENCE_IMAGE_TRANSITION_DURATION_MS);
        referenceImageCornerAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        float initialTopRadius = startTopRadius;
        float initialBottomRadius = startBottomRadius;
        referenceImageCornerAnimator.addUpdateListener(animator -> {
            float progress = (Float) animator.getAnimatedValue();
            outlineProvider.setCornerRadii(
                    lerp(initialTopRadius, targetTopRadius, progress),
                    lerp(initialBottomRadius, targetBottomRadius, progress));
            image.invalidateOutline();
        });
        referenceImageCornerAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (referenceImageCornerAnimator == animation) {
                    referenceImageCornerAnimator = null;
                }
                if (!cancelled && referenceBinding != null && !expanded) {
                    image.setBackgroundResource(R.drawable.story_preview_image_background);
                    image.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                    image.setClipToOutline(true);
                }
            }
        });
        referenceImageCornerAnimator.start();
    }

    private void startReferenceShimmers() {
        referenceBinding.referenceLinkPreviewShimmer.startShimmer();
        referenceBinding.referenceLinkTitleShimmer.startShimmer();
        referenceBinding.referenceLinkDescriptionShimmer.startShimmer();
    }

    private void stopReferenceShimmers() {
        referenceBinding.referenceLinkPreviewShimmer.stopShimmer();
        referenceBinding.referenceLinkTitleShimmer.stopShimmer();
        referenceBinding.referenceLinkDescriptionShimmer.stopShimmer();
        referenceBinding.referenceLinkPreviewShimmer.setVisibility(View.GONE);
        referenceBinding.referenceLinkTitleShimmer.setVisibility(View.GONE);
        referenceBinding.referenceLinkDescriptionShimmer.setVisibility(View.GONE);
    }

    private void hideStoryPreview(@NonNull StoryPageHolder page) {
        stopPreviewShimmer(page.content.storyLinkPreviewShimmer);
        if (page == currentStoryPage && enterTransitionStarted && !enterTransitionComplete) {
            page.pendingPreviewHide = true;
            return;
        }
        page.pendingPreviewHide = false;
        if (enterTransitionComplete) {
            beginStoryContentTransition(page, STORY_CONTENT_TRANSITION_DURATION_MS);
        }
        page.content.storyLinkPreview.setVisibility(View.GONE);
        page.content.storyLinkPreviewContainer.setVisibility(View.GONE);
    }

    private void finishPendingStoryPreviewHides() {
        if (storyPagerAdapter == null) return;
        for (int position = 0; position < storyPagerAdapter.getItemCount(); position++) {
            StoryPageHolder page = storyPagerAdapter.getBoundHolder(position);
            if (page == null || !page.pendingPreviewHide) continue;
            hideStoryPreview(page);
            resizeStoryPage(page);
        }
    }

    private void applyOrDeferStoryStateChange(
            @NonNull StoryPageHolder page,
            @NonNull Runnable stateChange) {
        Story expectedStory = page.story;
        Runnable guardedStateChange = () -> {
            if (page.story != expectedStory || binding == null) return;
            stateChange.run();
        };
        if (page == currentStoryPage && !enterTransitionComplete) {
            page.pendingStateChange = guardedStateChange;
            return;
        }
        guardedStateChange.run();
    }

    private void finishPendingStoryStateChanges() {
        if (storyPagerAdapter == null) return;
        for (int position = 0; position < storyPagerAdapter.getItemCount(); position++) {
            StoryPageHolder page = storyPagerAdapter.getBoundHolder(position);
            if (page == null || page.pendingStateChange == null) continue;
            Runnable stateChange = page.pendingStateChange;
            page.pendingStateChange = null;
            page.content.getRoot().post(() -> {
                if (!enterTransitionComplete) {
                    page.pendingStateChange = stateChange;
                    return;
                }
                stateChange.run();
            });
        }
    }

    private void setStoryTitle(
            @NonNull StoryPageHolder page,
            @NonNull Story story,
            @NonNull String fallback) {
        Context context = page.content.storyLinkTitle.getContext();
        if (!TextUtils.isEmpty(story.pdfTitle)) {
            page.content.storyLinkTitle.setText(TextSizeImageSpan.createWithTrailingBadge(
                    context, story.pdfTitle, R.drawable.ic_action_pdf));
        } else if (!TextUtils.isEmpty(story.videoTitle)) {
            page.content.storyLinkTitle.setText(TextSizeImageSpan.createWithTrailingBadge(
                    context, story.videoTitle, R.drawable.ic_action_video));
        } else {
            page.content.storyLinkTitle.setText(fallback);
        }
    }

    private void beginStoryContentTransition(
            @NonNull StoryPageHolder page,
            int durationMs) {
        if (binding == null || !enterTransitionComplete) return;
        AutoTransition transition = new AutoTransition();
        transition.setDuration(durationMs);
        TransitionManager.beginDelayedTransition(page.pageBinding.getRoot(), transition);
    }

    private void animateReferenceStateChange(@NonNull Runnable stateChange) {
        if (referenceBinding == null) return;
        ReferenceLinkSummaryContentBinding expectedBinding = referenceBinding;
        Runnable guardedStateChange = () -> {
            if (referenceBinding != expectedBinding || binding == null) return;
            TransitionSet transition = new TransitionSet()
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .addTransition(new Fade())
                    .addTransition(new ChangeBounds());
            transition.setDuration(REFERENCE_CONTENT_TRANSITION_DURATION_MS);
            transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            TransitionManager.beginDelayedTransition(binding.linkSummaryContent, transition);
            stateChange.run();
            resizeScroll();
        };
        if (!enterTransitionComplete) {
            pendingReferenceStateChange = guardedStateChange;
            return;
        }
        guardedStateChange.run();
    }

    private void finishPendingReferenceStateChange() {
        Runnable stateChange = pendingReferenceStateChange;
        pendingReferenceStateChange = null;
        if (stateChange == null || referenceBinding == null) return;
        referenceBinding.getRoot().post(() -> {
            if (!enterTransitionComplete) {
                pendingReferenceStateChange = stateChange;
                return;
            }
            stateChange.run();
        });
    }

    private void stopPreviewShimmer(com.facebook.shimmer.ShimmerFrameLayout shimmer) {
        shimmer.stopShimmer();
        shimmer.setVisibility(View.GONE);
    }

    private void stopDescriptionShimmer(com.facebook.shimmer.ShimmerFrameLayout shimmer) {
        shimmer.stopShimmer();
        shimmer.setVisibility(View.GONE);
    }

    private void startEnterTransition() {
        if (enterTransitionStarted || overlay == null) return;
        enterTransitionStarted = true;
        ViewGroup overlayHost = host.getLinkSummaryOverlayHost();
        overlay.post(() -> {
            if (overlay == null || card == null || overlayHost == null) return;
            if (isUsableTransition(overlayHost, sourceView, card)) {
                MaterialContainerTransform cardTransform = createTransform(overlayHost, sourceView, card,
                        MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
                cardTransform.addTarget(card);
                Transition transition = cardTransform;
                addEnterTransitionCompletionListener(transition);
                prepareStorySharedElementSnapshotAnimation(
                        overlayHost,
                        transition,
                        MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
                TransitionManager.beginDelayedTransition(overlayHost, transition);
                binding.linkSummaryScrim.animate().alpha(1f).setDuration(TRANSFORM_DURATION_MS).start();
                if (imageBinding != null) host.setLinkSummaryImageSourceSuppressed(true);
                setSourceVisible(sourceView, false);
                setSourceVisible(storyImageSourceView, false);
                setSourceVisible(storyTitleSourceView, false);
                setSourceVisible(storyMetaSourceView, false);
                card.setVisibility(View.VISIBLE);
            } else {
                enterTransitionComplete = true;
                binding.linkSummaryScrim.setAlpha(1f);
                if (imageBinding != null) host.setLinkSummaryImageSourceSuppressed(true);
                setSourceVisible(sourceView, false);
                setSourceVisible(storyImageSourceView, false);
                setSourceVisible(storyTitleSourceView, false);
                setSourceVisible(storyMetaSourceView, false);
                card.setVisibility(View.VISIBLE);
                finishStoryPagerEnter();
                finishPendingReferenceStateChange();
            }
            if (currentStoryPage != null) {
                resizeStoryPage(currentStoryPage);
            } else if (imageBinding == null) {
                resizeScroll();
            }
        });
    }

    private void finishStoryPagerEnter() {
        if (storyPager == null) return;
        setSourceVisible(sourceView, true);
        setSourceVisible(storyImageSourceView, true);
        setSourceVisible(storyTitleSourceView, true);
        setSourceVisible(storyMetaSourceView, true);
        storyPager.setUserInputEnabled(true);
        finishPendingStoryPreviewHides();
        finishPendingStoryStateChanges();
    }

    private int resolveDialogBackground(Context context, @Nullable View source) {
        View current = source;
        while (current != null) {
            if (current instanceof MaterialCardView) {
                return ((MaterialCardView) current).getCardBackgroundColor().getDefaultColor();
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return PreviewImageTintUtils.getTintBaseColor(context);
    }

    private String safeDomain(String url) {
        try { return Utils.getDomainName(url); } catch (Exception ignored) { return url; }
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private void cancelCurrentListTouch(ViewGroup overlayHost) {
        long time = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(time, time, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        overlayHost.dispatchTouchEvent(cancel);
        cancel.recycle();
        host.stopLinkSummaryListScroll();
    }

    private void configureOverlayInsets(View content) {
        int left = content.getPaddingLeft(), top = content.getPaddingTop();
        int right = content.getPaddingRight(), bottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, new OnApplyWindowInsetsListener() {
            @NonNull @Override public WindowInsetsCompat onApplyWindowInsets(
                    @NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets system = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + system.left, top + system.top, right + system.right, bottom + system.bottom);
                return insets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(content);
    }

    private void configureCardWidth(MaterialCardView target) {
        Context context = host.requireLinkSummaryContext();
        int max = Utils.pxFromDpInt(context.getResources(), Utils.isTablet(context.getResources()) ? 640 : 520);
        int hostWidth = context.getResources().getDisplayMetrics().widthPixels;
        int horizontalPadding = Utils.pxFromDpInt(context.getResources(), 40);
        if (target.getParent() instanceof View) {
            View parent = (View) target.getParent();
            if (parent.getWidth() > 0) {
                hostWidth = parent.getWidth();
                horizontalPadding = parent.getPaddingLeft() + parent.getPaddingRight();
            }
        }
        int available = hostWidth - horizontalPadding;
        if (available <= 0) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) target.getLayoutParams();
        params.width = Math.min(max, available);
        target.setLayoutParams(params);
    }

    private void configureStoryPageWidth(@NonNull StoryPageHolder page) {
        Context context = page.pageCard.getContext();
        int max = Utils.pxFromDpInt(context.getResources(),
                Utils.isTablet(context.getResources()) ? 640 : 520);
        int available = page.itemView.getWidth();
        if (available <= 0) {
            available = context.getResources().getDisplayMetrics().widthPixels
                    - Utils.pxFromDpInt(context.getResources(), 40);
        }
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) page.pageCard.getLayoutParams();
        params.width = Math.min(max, available);
        page.pageCard.setLayoutParams(params);
    }

    private void refreshForLayout() {
        if (binding == null || card == null) return;
        if (imageBinding != null) {
            Drawable drawable = imageBinding.imageOnlyPreview.getDrawable();
            if (drawable != null) configureImageOnlySize(drawable);
            return;
        }

        configureCardWidth(card);
        ViewGroup.LayoutParams params = binding.linkSummaryScroll.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.linkSummaryScroll.setLayoutParams(params);
        resizeScroll();
    }

    private void resizeScroll() {
        if (binding == null) return;
        NestedScrollView scroll = binding.linkSummaryScroll;
        View content = binding.linkSummaryContent;
        scroll.post(() -> {
            if (binding == null || scroll.getChildCount() == 0) return;
            int available = content.getHeight() - content.getPaddingTop() - content.getPaddingBottom();
            if (available <= 0) return;
            int height = scroll.getChildAt(0).getHeight();
            ViewGroup.LayoutParams params = scroll.getLayoutParams();
            params.height = height > available ? available : ViewGroup.LayoutParams.WRAP_CONTENT;
            scroll.setLayoutParams(params);
            scroll.setVerticalFadingEdgeEnabled(height > available);
        });
    }

    private void resizeStoryPage(@NonNull StoryPageHolder page) {
        page.pageScroll.post(() -> {
            if (binding == null || page.story == null
                    || page.pageScroll.getChildCount() == 0) {
                return;
            }
            int available = page.itemView.getHeight();
            if (available <= 0) return;
            int height = page.pageScroll.getChildAt(0).getHeight();
            ViewGroup.LayoutParams params = page.pageScroll.getLayoutParams();
            params.height = height > available
                    ? available : ViewGroup.LayoutParams.WRAP_CONTENT;
            page.pageScroll.setLayoutParams(params);
            page.pageScroll.setVerticalFadingEdgeEnabled(height > available);
        });
    }

    private MaterialContainerTransform createTransform(ViewGroup drawing, View start, View end, int direction) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        boolean returningToInlineLink = direction == MaterialContainerTransform.TRANSITION_DIRECTION_RETURN
                && end instanceof LinkPositionSourceView;
        transform.setStartView(start);
        transform.setEndView(end);
        transform.setDuration(TRANSFORM_DURATION_MS);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setDrawingViewId(ensureDrawingViewId(drawing));
        transform.setTransitionDirection(direction);
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_IN);
        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
        transform.setStartShapeAppearanceModel(createShape(start));
        transform.setEndShapeAppearanceModel(createShape(end));
        MaterialContainerTransform.ProgressThresholds thresholds =
                new MaterialContainerTransform.ProgressThresholds(0f, 1f);
        transform.setScaleMaskProgressThresholds(thresholds);
        transform.setShapeMaskProgressThresholds(thresholds);
        if (returningToInlineLink) {
            transform.setFadeProgressThresholds(
                    new MaterialContainerTransform.ProgressThresholds(
                            INLINE_LINK_RETURN_FADE_START,
                            INLINE_LINK_RETURN_FADE_END));
        }
        transform.setElevationShadowEnabled(!returningToInlineLink);
        transform.setStartContainerColor(getContainerColor(start));
        transform.setEndContainerColor(getContainerColor(end));
        transform.setStartElevation(start.getElevation());
        transform.setEndElevation(end.getElevation());
        return transform;
    }

    private void prepareStorySharedElementSnapshotAnimation(
            @NonNull ViewGroup drawing,
            @NonNull Transition transition,
            int direction) {
        finishStorySharedElementSnapshotAnimation();
        resolveStorySharedElementsIfNeeded();
        // Container transforms draw on the content overlay. Use the decor overlay so these
        // independently moving snapshots remain above the transforming card.
        View root = drawing.getRootView();
        ViewGroup snapshotDrawing = root instanceof ViewGroup ? (ViewGroup) root : drawing;
        boolean entering = direction == MaterialContainerTransform.TRANSITION_DIRECTION_ENTER;
        ImageView dialogImage = getStoryPreviewView();
        View dialogTitle = getStoryTitleView();
        View dialogMeta = getStoryMetaView();

        ImageView imageStart = entering ? storyImageSourceView : dialogImage;
        ImageView imageEnd = entering ? dialogImage : storyImageSourceView;
        float listImageRadius = getStoryListImageRadius(storyImageSourceView);
        float dialogImageRadius = Utils.pxFromDpInt(
                snapshotDrawing.getResources(), CARD_CORNER_RADIUS_DP);
        addStorySharedElementSnapshot(
                snapshotDrawing,
                imageStart,
                imageEnd,
                entering ? listImageRadius : dialogImageRadius,
                entering ? listImageRadius : 0f,
                entering ? dialogImageRadius : listImageRadius,
                entering ? 0f : listImageRadius);

        addStorySharedElementSnapshot(
                snapshotDrawing,
                entering ? storyTitleSourceView : dialogTitle,
                entering ? dialogTitle : storyTitleSourceView,
                0f, 0f, 0f, 0f);
        addStorySharedElementSnapshot(
                snapshotDrawing,
                entering ? storyMetaSourceView : dialogMeta,
                entering ? dialogMeta : storyMetaSourceView,
                0f, 0f, 0f, 0f);
        if (storySharedElementSnapshots.isEmpty()) return;
        storySharedElementSnapshotDrawing = snapshotDrawing;

        for (StorySharedElementSnapshot snapshot : storySharedElementSnapshots) {
            snapshot.start.setAlpha(0f);
            snapshot.end.setAlpha(0f);
        }
        int generation = ++storySharedElementSnapshotGeneration;
        transition.addListener(new TransitionListenerAdapter() {
            private boolean finished;

            @Override public void onTransitionStart(@NonNull Transition transition) {
                if (generation == storySharedElementSnapshotGeneration) {
                    startStorySharedElementSnapshotAnimation(snapshotDrawing);
                }
            }

            @Override public void onTransitionEnd(@NonNull Transition transition) { finish(); }
            @Override public void onTransitionCancel(@NonNull Transition transition) { finish(); }

            private void finish() {
                if (!finished) {
                    finished = true;
                    transition.removeListener(this);
                    if (generation == storySharedElementSnapshotGeneration) {
                        finishStorySharedElementSnapshotAnimation();
                    }
                }
            }
        });
    }

    private void addStorySharedElementSnapshot(
            @NonNull ViewGroup drawing,
            @Nullable View start,
            @Nullable View end,
            float startTopRadius,
            float startBottomRadius,
            float endTopRadius,
            float endBottomRadius) {
        if (!isUsableTransition(drawing, start, end)) return;
        if ((start instanceof ImageView && ((ImageView) start).getDrawable() == null)
                || (end instanceof ImageView && ((ImageView) end).getDrawable() == null)) {
            return;
        }
        Bitmap startBitmap = captureViewBitmap(start);
        Bitmap endBitmap = captureViewBitmap(end);
        if (startBitmap == null || endBitmap == null) {
            if (startBitmap != null) startBitmap.recycle();
            if (endBitmap != null) endBitmap.recycle();
            return;
        }

        int[] drawingLocation = new int[2];
        int[] startLocation = new int[2];
        int[] endLocation = new int[2];
        drawing.getLocationOnScreen(drawingLocation);
        start.getLocationOnScreen(startLocation);
        end.getLocationOnScreen(endLocation);
        float startX = startLocation[0] - drawingLocation[0];
        float startY = startLocation[1] - drawingLocation[1];
        float endX = endLocation[0] - drawingLocation[0];
        float endY = endLocation[1] - drawingLocation[1];

        StorySharedElementSnapshotDrawable overlay = new StorySharedElementSnapshotDrawable(
                startBitmap,
                endBitmap,
                start.getAlpha(),
                end.getAlpha(),
                startTopRadius,
                startBottomRadius,
                endTopRadius,
                endBottomRadius);
        overlay.setBounds(
                Math.round(startX),
                Math.round(startY),
                Math.round(startX) + start.getWidth(),
                Math.round(startY) + start.getHeight());
        storySharedElementSnapshots.add(new StorySharedElementSnapshot(
                start,
                end,
                overlay,
                startBitmap,
                endBitmap,
                start.getAlpha(),
                end.getAlpha(),
                startX,
                startY,
                endX,
                endY));
    }

    @Nullable
    private Bitmap captureViewBitmap(@NonNull View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(
                    view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (RuntimeException exception) {
            return null;
        }
        float alpha = view.getAlpha();
        int visibility = view.getVisibility();
        List<SoftwareImageDrawableSwap> drawableSwaps = new ArrayList<>();
        try {
            replaceHardwareImageDrawables(view, drawableSwaps);
            view.setAlpha(1f);
            view.setVisibility(View.VISIBLE);
            view.draw(new Canvas(bitmap));
            return bitmap;
        } catch (RuntimeException exception) {
            bitmap.recycle();
            return null;
        } finally {
            for (int index = drawableSwaps.size() - 1; index >= 0; index--) {
                drawableSwaps.get(index).restore();
            }
            view.setVisibility(visibility);
            view.setAlpha(alpha);
        }
    }

    private void replaceHardwareImageDrawables(
            @NonNull View view,
            @NonNull List<SoftwareImageDrawableSwap> drawableSwaps) {
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                Bitmap drawableBitmap = ((BitmapDrawable) drawable).getBitmap();
                if (drawableBitmap != null
                        && drawableBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    Bitmap softwareBitmap = drawableBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    if (softwareBitmap != null) {
                        BitmapDrawable softwareDrawable = new BitmapDrawable(
                                view.getResources(), softwareBitmap);
                        softwareDrawable.setAlpha(drawable.getAlpha());
                        softwareDrawable.setColorFilter(drawable.getColorFilter());
                        imageView.setImageDrawable(softwareDrawable);
                        drawableSwaps.add(new SoftwareImageDrawableSwap(
                                imageView, drawable, softwareBitmap));
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                replaceHardwareImageDrawables(group.getChildAt(index), drawableSwaps);
            }
        }
    }

    private void startStorySharedElementSnapshotAnimation(@NonNull ViewGroup drawing) {
        if (storySharedElementSnapshots.isEmpty()) return;
        for (StorySharedElementSnapshot snapshot : storySharedElementSnapshots) {
            drawing.getOverlay().add(snapshot.overlay);
        }
        updateStorySharedElementSnapshots(drawing, 0f);
        storySharedElementSnapshotAnimator = ValueAnimator.ofFloat(0f, 1f);
        storySharedElementSnapshotAnimator.setDuration(TRANSFORM_DURATION_MS);
        storySharedElementSnapshotAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        storySharedElementSnapshotAnimator.addUpdateListener(animator -> updateStorySharedElementSnapshots(
                drawing, (Float) animator.getAnimatedValue()));
        storySharedElementSnapshotAnimator.start();
    }

    private void updateStorySharedElementSnapshots(@NonNull ViewGroup drawing, float progress) {
        for (StorySharedElementSnapshot snapshot : storySharedElementSnapshots) {
            float left = lerp(snapshot.startX, snapshot.endX, progress);
            float top = lerp(snapshot.startY, snapshot.endY, progress);
            int width = Math.round(lerp(snapshot.startWidth, snapshot.endWidth, progress));
            int height = Math.round(lerp(snapshot.startHeight, snapshot.endHeight, progress));
            snapshot.overlay.setBounds(
                    Math.round(left),
                    Math.round(top),
                    Math.round(left) + width,
                    Math.round(top) + height);
            snapshot.overlay.setProgress(progress);

            // Container transforms also draw in this overlay. Re-add the snapshots so they stay
            // above the card throughout the animation, regardless of transition listener order.
            drawing.getOverlay().remove(snapshot.overlay);
            drawing.getOverlay().add(snapshot.overlay);
        }
    }

    private void finishStorySharedElementSnapshotAnimation() {
        storySharedElementSnapshotGeneration++;
        if (storySharedElementSnapshotAnimator != null) {
            storySharedElementSnapshotAnimator.removeAllUpdateListeners();
            storySharedElementSnapshotAnimator.cancel();
            storySharedElementSnapshotAnimator = null;
        }
        for (StorySharedElementSnapshot snapshot : storySharedElementSnapshots) {
            if (storySharedElementSnapshotDrawing != null) {
                storySharedElementSnapshotDrawing.getOverlay().remove(snapshot.overlay);
            }
            snapshot.start.setAlpha(snapshot.startAlpha);
            snapshot.end.setAlpha(snapshot.endAlpha);
            snapshot.startBitmap.recycle();
            snapshot.endBitmap.recycle();
        }
        storySharedElementSnapshots.clear();
        storySharedElementSnapshotDrawing = null;
    }

    private float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private float getStoryListImageRadius(@Nullable ImageView image) {
        if (image == null) return 0f;
        return image.getClipToOutline()
                ? image.getResources().getDimension(R.dimen.story_preview_image_corner_radius)
                : 0f;
    }

    private ShapeAppearanceModel createShape(View view) {
        float radius = view instanceof LinkPositionSourceView ? 0
                : view == card ? CARD_CORNER_RADIUS_DP
                : view instanceof MaterialCardView ? 8
                : view instanceof ImageView ? 12 : 0;
        return ShapeAppearanceModel.builder().setAllCornerSizes(
                Utils.pxFromDpInt(view.getResources(), (int) radius)).build();
    }

    private int getContainerColor(View view) {
        return view instanceof LinkPositionSourceView
                ? Color.TRANSPARENT
                : view instanceof MaterialCardView
                ? ((MaterialCardView) view).getCardBackgroundColor().getDefaultColor()
                : PreviewImageTintUtils.getTintBaseColor(view.getContext());
    }

    void dismiss(boolean animate) {
        if (overlay == null || dismissing) return;
        if (binding == null || card == null) { removeNow(); return; }
        dismissing = true;
        predictiveBackActive = false;
        if (storyPager != null) {
            storyPager.setUserInputEnabled(false);
            host.clearLinkSummaryStoryPagingAlphas(animate);
        }
        cancelSummaryRequest();
        ViewGroup overlayHost = host.getLinkSummaryOverlayHost();
        View end = resolveSourceView();
        host.syncLinkSummaryBackState();
        if (animate && overlayHost != null && isUsableTransition(overlayHost, card, end)) {
            MaterialContainerTransform cardTransform = createTransform(overlayHost, card, end,
                    MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
            cardTransform.addTarget(end);
            Transition transition = cardTransform;
            prepareStorySharedElementSnapshotAnimation(
                    overlayHost,
                    transition,
                    MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
            transition.addListener(new TransitionListenerAdapter() {
                private boolean finished;
                @Override public void onTransitionEnd(@NonNull Transition transition) { finish(); }
                @Override public void onTransitionCancel(@NonNull Transition transition) { finish(); }
                private void finish() {
                    if (!finished) {
                        finished = true;
                        transition.removeListener(this);
                        restoreStorySharedElementAlphas();
                        removeNow();
                    }
                }
            });
            TransitionManager.beginDelayedTransition(overlayHost, transition);
            binding.linkSummaryScrim.animate().alpha(0f).setDuration(TRANSFORM_DURATION_MS).start();
            setSourceVisible(end, true);
            setSourceVisible(storyImageSourceView, true);
            setSourceVisible(storyTitleSourceView, true);
            setSourceVisible(storyMetaSourceView, true);
            card.setVisibility(View.INVISIBLE);
        } else if (!animate) {
            removeNow();
        } else {
            card.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(TRANSFORM_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) { removeNow(); }
                    });
            binding.linkSummaryScrim.animate().alpha(0f).setDuration(TRANSFORM_DURATION_MS).start();
        }
    }

    void removeNow() {
        cancelSummaryRequest();
        finishStorySharedElementSnapshotAnimation();
        host.clearLinkSummaryStoryPagingAlphas(false);
        if (referenceBinding != null) {
            referenceBinding.referenceLinkMetadataContainer.animate().cancel();
        }
        if (referenceImageCornerAnimator != null) {
            referenceImageCornerAnimator.removeAllUpdateListeners();
            referenceImageCornerAnimator.removeAllListeners();
            referenceImageCornerAnimator.cancel();
            referenceImageCornerAnimator = null;
        }
        if (overlay == null) {
            removeLinkPositionSource();
            return;
        }
        boolean wasShowingImage = imageBinding != null;
        if (imageBinding != null) CoilUtils.dispose(imageBinding.imageOnlyPreview);
        if (binding != null) ViewCompat.setOnApplyWindowInsetsListener(binding.linkSummaryContent, null);
        setSourceVisible(sourceView, true);
        setSourceVisible(storyImageSourceView, true);
        setSourceVisible(storyTitleSourceView, true);
        setSourceVisible(storyMetaSourceView, true);
        restoreStorySharedElementAlphas();
        if (overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
        removeLinkPositionSource();
        if (storyPager != null) {
            storyPager.setAdapter(null);
        }
        overlay = null; binding = null; card = null; storyBinding = null;
        storyPager = null; storyPagerAdapter = null; currentStoryPage = null;
        referenceBinding = null; imageBinding = null;
        sourceView = null; storyImageSourceView = null; storyTitleSourceView = null;
        storyMetaSourceView = null;
        storyImageSourceAlpha = 1f; storyTitleSourceAlpha = 1f;
        storyMetaSourceAlpha = 1f;
        visibleStoryId = NO_STORY_ID; visibleStoryPosition = -1;
        visibleUrl = null; fallbackTitle = null; dismissing = false;
        predictiveBackActive = false; enterTransitionStarted = false;
        enterTransitionComplete = false;
        pendingReferenceStateChange = null;
        lastStoryPagerPosition = Float.NaN;
        pendingStoryListScrollPixels = 0f;
        referenceImageExpanded = false;
        if (wasShowingImage) host.setLinkSummaryImageSourceSuppressed(false);
        host.syncLinkSummaryBackState();
    }

    private void removeLinkPositionSource() {
        if (linkPositionSourceView == null) return;
        if (linkPositionSourceView.getParent() instanceof ViewGroup) {
            ((ViewGroup) linkPositionSourceView.getParent()).removeView(linkPositionSourceView);
        }
        linkPositionSourceView = null;
    }

    private void cancelSummaryRequest() {
        if (summaryRequest != null) { summaryRequest.cancel(); summaryRequest = null; }
        if (storyPagerAdapter != null) storyPagerAdapter.cancelAllRequests();
    }

    void startPredictiveBack(@NonNull BackEventCompat event) {
        if (card == null || binding == null || dismissing) return;
        predictiveBackActive = true;
        card.animate().cancel(); binding.linkSummaryScrim.animate().cancel(); updatePredictiveBack(event);
    }

    void updatePredictiveBack(@NonNull BackEventCompat event) {
        if (card == null || binding == null || dismissing) return;
        predictiveBackActive = true;
        float progress = Math.max(0f, Math.min(1f, event.getProgress()));
        float eased = 1f - ((1f - progress) * (1f - progress));
        float scale = 1f - ((1f - PREDICTIVE_BACK_MIN_SCALE) * eased);
        float direction = event.getSwipeEdge() == BackEventCompat.EDGE_RIGHT ? -1f : 1f;
        card.setPivotX(direction > 0f ? 0f : card.getWidth());
        card.setPivotY(event.getTouchY() > 0f
                ? Math.max(0f, Math.min(card.getHeight(), event.getTouchY() - card.getTop()))
                : card.getHeight() / 2f);
        card.setScaleX(scale); card.setScaleY(scale);
        card.setTranslationX(direction * Utils.pxFromDpInt(card.getResources(),
                PREDICTIVE_BACK_TRANSLATION_X_DP) * eased);
        card.setTranslationY(Utils.pxFromDpInt(card.getResources(),
                PREDICTIVE_BACK_TRANSLATION_Y_DP) * eased);
        binding.linkSummaryScrim.setAlpha(1f - ((1f - PREDICTIVE_BACK_MIN_SCRIM_ALPHA) * eased));
    }

    void cancelPredictiveBack() {
        if (card == null || binding == null || !predictiveBackActive) return;
        predictiveBackActive = false;
        card.animate().translationX(0).translationY(0).scaleX(1).scaleY(1)
                .setDuration(TRANSFORM_DURATION_MS).setListener(null).start();
        binding.linkSummaryScrim.animate().alpha(1).setDuration(TRANSFORM_DURATION_MS).start();
    }

    void commitPredictiveBack() {
        if (overlay == null || card == null || binding == null || dismissing) return;
        predictiveBackActive = false; card.animate().cancel(); dismiss(true);
    }

    @Nullable private View resolveSourceView() {
        if (isUsableTransitionView(sourceView)) return sourceView;
        if (imageBinding != null) return host.findLinkSummaryImageSourceView();
        return visibleStoryId == NO_STORY_ID ? null : host.findLinkSummarySourceView(visibleStoryId);
    }

    @Nullable private ImageView getStoryPreviewView() {
        return storyBinding == null ? null : storyBinding.storyLinkPreview;
    }

    @Nullable private View getStoryTitleView() {
        return storyBinding == null ? null : storyBinding.storyLinkTitle;
    }

    @Nullable private View getStoryMetaView() {
        return storyBinding == null ? null : storyBinding.storyLinkMetaContainer;
    }

    private boolean isUsableStoryImageSource(@Nullable ImageView image) {
        return isUsableTransitionView(image)
                && image.getVisibility() == View.VISIBLE
                && image.getDrawable() != null;
    }

    private boolean isUsableStorySharedSource(@Nullable View view) {
        return isUsableTransitionView(view) && view.getVisibility() == View.VISIBLE;
    }

    private void addEnterTransitionCompletionListener(@NonNull Transition transition) {
        transition.addListener(new TransitionListenerAdapter() {
            private boolean restored;
            @Override public void onTransitionEnd(@NonNull Transition transition) { restore(); }
            @Override public void onTransitionCancel(@NonNull Transition transition) { restore(); }
            private void restore() {
                if (!restored) {
                    restored = true;
                    transition.removeListener(this);
                    enterTransitionComplete = true;
                    restoreStorySharedElementAlphas();
                    finishStoryPagerEnter();
                    finishPendingReferenceStateChange();
                }
            }
        });
    }

    private void restoreStorySharedElementAlphas() {
        if (storyImageSourceView != null) {
            storyImageSourceView.setAlpha(storyImageSourceAlpha);
        }
        if (storyTitleSourceView != null) {
            storyTitleSourceView.setAlpha(storyTitleSourceAlpha);
        }
        if (storyMetaSourceView != null) {
            storyMetaSourceView.setAlpha(storyMetaSourceAlpha);
        }
    }

    private void setSourceVisible(@Nullable View view, boolean visible) {
        if (isUsableTransitionView(view)) view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    private boolean isUsableTransitionView(@Nullable View view) {
        return view != null && ViewCompat.isAttachedToWindow(view) && view.getWidth() > 0 && view.getHeight() > 0;
    }

    private boolean isUsableTransition(@Nullable ViewGroup drawing, @Nullable View start, @Nullable View end) {
        return isUsableTransitionView(drawing) && isUsableTransitionView(start) && isUsableTransitionView(end)
                && isDescendantOf(start, drawing) && isDescendantOf(end, drawing);
    }

    private int ensureDrawingViewId(@NonNull ViewGroup drawing) {
        if (drawing.getId() == View.NO_ID) drawing.setId(View.generateViewId());
        return drawing.getId();
    }

    private boolean isDescendantOf(@Nullable View view, @NonNull ViewGroup ancestor) {
        View current = view;
        while (current != null) {
            if (current == ancestor) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
