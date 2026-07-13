package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.BackEventCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.transition.Transition;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionListenerAdapter;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.transition.MaterialContainerTransform;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.ImageOnlyOverlayContentBinding;
import com.simon.harmonichackernews.databinding.LinkSummaryOverlayBinding;
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

final class LinkSummaryOverlayController {
    interface Host {
        @Nullable Context getLinkSummaryContext();
        @NonNull Context requireLinkSummaryContext();
        @Nullable ViewGroup getLinkSummaryOverlayHost();
        @Nullable View findLinkSummarySourceView(int storyId);
        default @Nullable ImageView findLinkSummaryStoryImageSourceView(int storyId) { return null; }
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
    private static final int TRANSFORM_DURATION_MS = 280;
    private static final int STORY_CONTENT_TRANSITION_DURATION_MS = 220;
    private static final int REFERENCE_CONTENT_TRANSITION_DURATION_MS = 90;
    private static final int ACTION_SWAP_OUT_DURATION_MS = 90;
    private static final int ACTION_SWAP_IN_DURATION_MS = 150;
    private static final float ACTION_SWAP_MIN_SCALE = 0.72f;
    private static final int CARD_CORNER_RADIUS_DP = 28;
    private static final int STORY_PREVIEW_DEFAULT_HEIGHT_DP = 220;
    private static final int PREDICTIVE_BACK_TRANSLATION_X_DP = 56;
    private static final int PREDICTIVE_BACK_TRANSLATION_Y_DP = 18;
    private static final float PREDICTIVE_BACK_MIN_SCALE = 0.9f;
    private static final float PREDICTIVE_BACK_MIN_SCRIM_ALPHA = 0.45f;

    private final Host host;
    private FrameLayout overlay;
    private LinkSummaryOverlayBinding binding;
    private MaterialCardView card;
    private StoryLinkSummaryContentBinding storyBinding;
    private ReferenceLinkSummaryContentBinding referenceBinding;
    private ImageOnlyOverlayContentBinding imageBinding;
    private View sourceView;
    private ImageView storyImageSourceView;
    private float storyImageSourceAlpha = 1f;
    private LinkSummaryLoader.SummaryRequest summaryRequest;
    private int visibleStoryId = NO_STORY_ID;
    private int visibleStoryPosition = -1;
    private String visibleUrl;
    private String fallbackTitle;
    private boolean dismissing;
    private boolean predictiveBackActive;
    private boolean enterTransitionStarted;
    private int storyVoteLoadingId = NO_STORY_ID;
    private int storyFavoriteLoadingId = NO_STORY_ID;

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
    void showStory(Story story, int position, @Nullable View source, @Nullable ImageView imageSource) {
        Context context = host.getLinkSummaryContext();
        if (context == null || story == null || TextUtils.isEmpty(story.url) || !prepareOverlay(source)) {
            return;
        }
        if (isUsableStoryImageSource(imageSource)) {
            storyImageSourceView = imageSource;
            storyImageSourceAlpha = imageSource.getAlpha();
        }
        visibleStoryId = story.id;
        visibleStoryPosition = position;
        visibleUrl = story.url;
        fallbackTitle = story.title;
        if (card != null) {
            card.setCardBackgroundColor(host.resolveStoryCardBackgroundColor(story));
        }

        storyBinding = StoryLinkSummaryContentBinding.inflate(
                LayoutInflater.from(context), binding.linkSummaryBody, true);
        applyStoryTypography();
        bindStoryKnownContent(context, story);
        configureStoryActions(context, story);
        LinkSummaryLoader.Result cached = StoryPreviewImageLoader.getCachedLinkSummary(context, story.url);
        if (cached != null) {
            bindStoryResult(story, cached);
        } else {
            startStoryShimmers();
            if (story.previewImageUrlLoaded) {
                if (TextUtils.isEmpty(story.previewImageUrl)) {
                    hideStoryPreview();
                } else {
                    loadStoryImage(story, story.previewImageUrl);
                }
            } else if (!TextUtils.isEmpty(story.pdfTitle) || !TextUtils.isEmpty(story.videoTitle)) {
                hideStoryPreview();
            }
            loadStorySummary(story);
        }
        seedStoryPreviewFromSource();
        startEnterTransition();
    }

    private void seedStoryPreviewFromSource() {
        if (storyBinding == null || card == null || storyImageSourceView == null) return;
        Drawable sourceDrawable = storyImageSourceView.getDrawable();
        if (sourceDrawable == null) return;

        ImageView preview = storyBinding.storyLinkPreview;
        Drawable previewDrawable = preview.getDrawable();
        if (previewDrawable == null) {
            Drawable.ConstantState constantState = sourceDrawable.getConstantState();
            previewDrawable = constantState == null
                    ? sourceDrawable
                    : constantState.newDrawable(card.getResources()).mutate();
            preview.setImageDrawable(previewDrawable);
        }
        preview.setVisibility(View.VISIBLE);
        storyBinding.storyLinkPreviewContainer.setVisibility(View.VISIBLE);
        stopPreviewShimmer(storyBinding.storyLinkPreviewShimmer);
        configureSeededStoryPreviewHeight(previewDrawable);
    }

    private void configureSeededStoryPreviewHeight(@NonNull Drawable drawable) {
        if (storyBinding == null || card == null) return;
        ViewGroup.LayoutParams cardParams = card.getLayoutParams();
        int width = cardParams == null ? 0 : cardParams.width;
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (width <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) return;

        int defaultHeight = Utils.pxFromDpInt(card.getResources(), STORY_PREVIEW_DEFAULT_HEIGHT_DP);
        int targetHeight = Math.min(defaultHeight,
                Math.max(1, Math.round(width * intrinsicHeight / (float) intrinsicWidth)));
        ViewGroup.LayoutParams previewParams = storyBinding.storyLinkPreviewContainer.getLayoutParams();
        if (previewParams.height != targetHeight) {
            previewParams.height = targetHeight;
            storyBinding.storyLinkPreviewContainer.setLayoutParams(previewParams);
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
        startReferenceShimmers();
        loadReferenceSummary();
        startEnterTransition();
    }

    void showReference(String url, String title, @Nullable View source) {
        Context context = host.getLinkSummaryContext();
        if (context == null || TextUtils.isEmpty(url) || !prepareOverlay(source)) {
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
        startReferenceShimmers();
        loadReferenceSummary();
        startEnterTransition();
    }

    private void applyStoryTypography() {
        FontUtils.setLinkSummaryStoryTitleTypeface(storyBinding.storyLinkTitle);
        FontUtils.setLinkSummaryMetaTypeface(storyBinding.storyLinkMeta);
        FontUtils.setLinkSummaryBodyTypeface(storyBinding.storyLinkDescription);
        FontUtils.setLinkSummaryErrorTypeface(storyBinding.storyLinkError);
    }

    private void applyReferenceTypography() {
        FontUtils.setLinkSummaryMetaTypeface(referenceBinding.referenceLinkDomain);
        FontUtils.setLinkSummaryReferenceTitleTypeface(referenceBinding.referenceLinkTitle);
        FontUtils.setLinkSummaryBodyTypeface(referenceBinding.referenceLinkDescription);
        FontUtils.setLinkSummaryErrorTypeface(referenceBinding.referenceLinkError);
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
        Context context = host.getLinkSummaryContext();
        ViewGroup overlayHost = host.getLinkSummaryOverlayHost();
        if (context == null || overlayHost == null) {
            return false;
        }
        removeNow();
        sourceView = source;
        dismissing = false;
        enterTransitionStarted = false;
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

    private void bindStoryKnownContent(Context context, Story story) {
        setStoryTitle(story, firstNonEmpty(story.title, story.url));
        storyBinding.storyLinkMeta.setText(story.score +
                (story.score == 1 ? " point" : " points") + " • " + safeDomain(story.url) +
                " • " + story.getTimeFormatted());
        storyBinding.storyLinkComments.setText(R.string.link_summary_comments);
        FaviconLoader.loadFavicon(story.url, storyBinding.storyLinkFavicon, context,
                SettingsUtils.getPreferredFaviconProvider(context));
    }

    private void configureStoryActions(Context context, Story story) {
        boolean hasAccount = AccountUtils.hasAccountDetails(context);
        storyBinding.storyLinkComments.setText(hasAccount
                ? String.valueOf(story.descendants)
                : context.getString(R.string.link_summary_comments));
        storyBinding.storyLinkComments.setContentDescription(
                "Comments (" + story.descendants + ")");
        storyBinding.storyLinkVoteSlot.setVisibility(hasAccount ? View.VISIBLE : View.GONE);
        storyBinding.storyLinkFavoriteSlot.setVisibility(hasAccount ? View.VISIBLE : View.GONE);
        storyBinding.storyLinkBookmark.setVisibility(
                SettingsUtils.shouldUseBookmarks(context) ? View.VISIBLE : View.GONE);
        refreshStoryActionButtons(context, story);

        storyBinding.storyLinkVote.setOnClickListener(v -> {
            if (storyVoteLoadingId == story.id) return;
            boolean selected = Utils.isUpvoted(context, story.id, false);
            storyVoteLoadingId = story.id;
            ImageButton button = storyBinding.storyLinkVote;
            LoadingIndicator loadingIndicator = storyBinding.storyLinkVoteLoading;
            showStoryActionLoading(button, loadingIndicator,
                    selected ? "Removing upvote" : "Upvoting");
            host.toggleStoryVote(story, visibleStoryPosition, selected,
                    () -> finishStoryActionLoading(context, story, button, loadingIndicator, true));
        });
        storyBinding.storyLinkRead.setOnClickListener(v -> {
            host.toggleStoryRead(story, visibleStoryPosition);
            refreshStoryActionButtons(context, story);
        });
        storyBinding.storyLinkBookmark.setOnClickListener(v -> {
            boolean selected = Utils.isBookmarked(context, story.id);
            host.toggleStoryBookmark(story, visibleStoryPosition, selected);
            refreshStoryActionButtons(context, story);
        });
        storyBinding.storyLinkFavorite.setOnClickListener(v -> {
            if (storyFavoriteLoadingId == story.id) return;
            boolean selected = Utils.isFavorited(context, story.id);
            storyFavoriteLoadingId = story.id;
            ImageButton button = storyBinding.storyLinkFavorite;
            LoadingIndicator loadingIndicator = storyBinding.storyLinkFavoriteLoading;
            showStoryActionLoading(button, loadingIndicator,
                    selected ? "Removing favorite" : "Adding favorite");
            host.toggleStoryFavorite(story, visibleStoryPosition, selected,
                    () -> finishStoryActionLoading(context, story, button, loadingIndicator, false));
        });
        storyBinding.storyLinkHeader.setContentDescription(
                "Open link: " + firstNonEmpty(story.title, story.url));
        storyBinding.storyLinkHeader.setOnClickListener(v -> {
            int position = visibleStoryPosition;
            navigateToStory(story, position, true);
        });
        storyBinding.storyLinkComments.setOnClickListener(v -> {
            int position = visibleStoryPosition;
            navigateToStory(story, position, false);
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

    private void refreshStoryActionButtons(Context context, Story story) {
        setActionState(storyBinding.storyLinkVote, Utils.isUpvoted(context, story.id, false),
                R.drawable.ic_thumb_up, R.drawable.ic_thumb_up_filled, "Upvote", "Remove upvote");
        setActionState(storyBinding.storyLinkRead, story.clicked,
                R.drawable.ic_visibility, R.drawable.ic_visibility_off, "Mark as read", "Mark as unread");
        setActionState(storyBinding.storyLinkBookmark, Utils.isBookmarked(context, story.id),
                R.drawable.ic_bookmark, R.drawable.ic_bookmark_filled, "Bookmark", "Remove bookmark");
        setActionState(storyBinding.storyLinkFavorite, Utils.isFavorited(context, story.id),
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

    private void finishStoryActionLoading(Context context,
                                          Story story,
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
                || storyBinding == null || visibleStoryId != story.id) {
            return;
        }
        animateActionViewOut(loadingIndicator, () -> {
            loadingIndicator.setVisibility(View.GONE);
            resetActionView(loadingIndicator);
            button.setTag(null);
            button.setVisibility(View.VISIBLE);
            refreshStoryActionButtons(context, story);
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

    private void startStoryShimmers() {
        storyBinding.storyLinkDescriptionShimmer.setVisibility(View.VISIBLE);
        storyBinding.storyLinkDescriptionShimmer.startShimmer();
        storyBinding.storyLinkPreviewShimmer.setVisibility(View.VISIBLE);
        storyBinding.storyLinkPreviewShimmer.startShimmer();
        storyBinding.storyLinkDescription.setVisibility(View.GONE);
        storyBinding.storyLinkError.setVisibility(View.GONE);
    }

    private void loadStorySummary(Story story) {
        String requestedUrl = visibleUrl;
        summaryRequest = LinkSummaryLoader.load(host.getLinkSummaryContext(), requestedUrl, fallbackTitle,
                new LinkSummaryLoader.Callback() {
                    @Override public void onSuccess(@NonNull LinkSummaryLoader.Result result) {
                        if (storyBinding == null || !TextUtils.equals(requestedUrl, visibleUrl)) return;
                        summaryRequest = null;
                        bindStoryResult(story, result);
                    }
                    @Override public void onFailure(@NonNull String message) {
                        if (storyBinding == null || !TextUtils.equals(requestedUrl, visibleUrl)) return;
                        summaryRequest = null;
                        beginContentTransition(STORY_CONTENT_TRANSITION_DURATION_MS);
                        stopDescriptionShimmer(storyBinding.storyLinkDescriptionShimmer);
                        if (storyBinding.storyLinkPreview.getVisibility() != View.VISIBLE) {
                            hideStoryPreview();
                        }
                        storyBinding.storyLinkError.setText(message);
                        storyBinding.storyLinkError.setVisibility(View.VISIBLE);
                        resizeScroll();
                    }
                });
    }

    private void bindStoryResult(Story story, LinkSummaryLoader.Result result) {
        if (!TextUtils.isEmpty(result.finalUrl)) visibleUrl = result.finalUrl;
        beginContentTransition(STORY_CONTENT_TRANSITION_DURATION_MS);
        setStoryTitle(story, firstNonEmpty(result.title, story.title, visibleUrl));
        stopDescriptionShimmer(storyBinding.storyLinkDescriptionShimmer);
        if (!TextUtils.isEmpty(result.description)) {
            storyBinding.storyLinkDescription.setText(result.description);
            storyBinding.storyLinkDescription.setVisibility(View.VISIBLE);
        } else {
            storyBinding.storyLinkDescription.setVisibility(View.GONE);
        }
        String imageUrl = firstNonEmpty(result.imageUrl, story.previewImageUrl);
        if (TextUtils.isEmpty(imageUrl)) {
            hideStoryPreview();
        } else {
            story.previewImageUrl = imageUrl;
            story.previewImageUrlLoaded = true;
            if (!imageUrl.equals(storyBinding.storyLinkPreview.getTag())
                    || storyBinding.storyLinkPreview.getVisibility() != View.VISIBLE) {
                loadStoryImage(story, imageUrl);
            }
        }
        resizeScroll();
    }

    private void loadStoryImage(Story story, String imageUrl) {
        ImageView imageView = storyBinding.storyLinkPreview;
        imageView.setTag(imageUrl);
        storyBinding.storyLinkPreviewContainer.setVisibility(View.VISIBLE);
        storyBinding.storyLinkPreviewShimmer.setVisibility(View.VISIBLE);
        storyBinding.storyLinkPreviewShimmer.startShimmer();
        ImageRequest request = new ImageRequest.Builder(imageView.getContext())
                .data(imageUrl).setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .allowHardware(false).crossfade(true)
                .target(new ImageViewTarget(imageView) {
                    @Override public void onSuccess(Drawable result) {
                        super.onSuccess(result);
                        if (storyBinding == null) return;
                        PreviewImageLayoutUtils.applyWideImageHeight(
                                imageView,
                                storyBinding.storyLinkPreviewContainer,
                                result,
                                STORY_PREVIEW_DEFAULT_HEIGHT_DP);
                        storyBinding.storyLinkPreviewShimmer.stopShimmer();
                        storyBinding.storyLinkPreviewShimmer.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                        int base = PreviewImageTintUtils.getTintBaseColor(imageView.getContext());
                        PreviewImageTintUtils.updateStoryPreviewImageTintColor(story, imageUrl, result, base,
                                SettingsUtils.getPreferredPaletteTintConfigKey(imageView.getContext()));
                        imageView.post(this::resizeStoryScroll);
                    }
                    private void resizeStoryScroll() {
                        if (storyBinding != null) {
                            resizeScroll();
                        }
                    }
                    @Override public void onError(Drawable error) {
                        super.onError(null);
                        if (storyBinding != null) {
                            beginContentTransition(STORY_CONTENT_TRANSITION_DURATION_MS);
                            stopPreviewShimmer(storyBinding.storyLinkPreviewShimmer);
                            hideStoryPreview();
                            resizeScroll();
                        }
                    }
                }).build();
        Coil.imageLoader(imageView.getContext()).enqueue(request);
    }

    private void loadReferenceSummary() {
        String requestedUrl = visibleUrl;
        summaryRequest = LinkSummaryLoader.load(host.getLinkSummaryContext(), requestedUrl, fallbackTitle,
                new LinkSummaryLoader.Callback() {
                    @Override public void onSuccess(@NonNull LinkSummaryLoader.Result result) {
                        if (referenceBinding == null || !TextUtils.equals(requestedUrl, visibleUrl)) return;
                        summaryRequest = null;
                        bindReferenceResult(result);
                    }
                    @Override public void onFailure(@NonNull String message) {
                        if (referenceBinding == null || !TextUtils.equals(requestedUrl, visibleUrl)) return;
                        summaryRequest = null;
                        stopReferenceShimmers();
                        referenceBinding.referenceLinkTitle.setAlpha(0f);
                        referenceBinding.referenceLinkTitle.setText(fallbackTitle);
                        referenceBinding.referenceLinkTitle.setVisibility(View.VISIBLE);
                        referenceBinding.referenceLinkError.setAlpha(0f);
                        referenceBinding.referenceLinkError.setText(message);
                        referenceBinding.referenceLinkError.setVisibility(View.VISIBLE);
                        referenceBinding.referenceLinkPreviewContainer.setVisibility(View.GONE);
                        fadeInReferenceView(referenceBinding.referenceLinkTitle);
                        fadeInReferenceView(referenceBinding.referenceLinkError);
                        resizeScroll();
                    }
                });
    }

    private void bindReferenceResult(LinkSummaryLoader.Result result) {
        if (!TextUtils.isEmpty(result.finalUrl)) visibleUrl = result.finalUrl;
        referenceBinding.referenceLinkTitleShimmer.stopShimmer();
        referenceBinding.referenceLinkTitleShimmer.setVisibility(View.GONE);
        referenceBinding.referenceLinkTitle.setAlpha(0f);
        referenceBinding.referenceLinkTitle.setText(firstNonEmpty(result.title, fallbackTitle, visibleUrl));
        referenceBinding.referenceLinkTitle.setVisibility(View.VISIBLE);
        stopDescriptionShimmer(referenceBinding.referenceLinkDescriptionShimmer);
        if (!TextUtils.isEmpty(result.description)) {
            referenceBinding.referenceLinkDescription.setAlpha(0f);
            referenceBinding.referenceLinkDescription.setText(result.description);
            referenceBinding.referenceLinkDescription.setVisibility(View.VISIBLE);
            fadeInReferenceView(referenceBinding.referenceLinkDescription);
        }
        fadeInReferenceView(referenceBinding.referenceLinkTitle);
        if (TextUtils.isEmpty(result.imageUrl)) {
            stopPreviewShimmer(referenceBinding.referenceLinkPreviewShimmer);
            referenceBinding.referenceLinkPreviewContainer.setVisibility(View.GONE);
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
                            image.setVisibility(View.VISIBLE);
                        }
                    }
                    @Override public void onError(Drawable error) {
                        super.onError(null);
                        if (referenceBinding != null) {
                            stopPreviewShimmer(referenceBinding.referenceLinkPreviewShimmer);
                            referenceBinding.referenceLinkPreviewContainer.setVisibility(View.GONE);
                            resizeScroll();
                        }
                    }
                }).build();
        Coil.imageLoader(image.getContext()).enqueue(request);
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

    private void hideStoryPreview() {
        if (storyBinding == null) return;
        stopPreviewShimmer(storyBinding.storyLinkPreviewShimmer);
        storyBinding.storyLinkPreview.setVisibility(View.GONE);
        storyBinding.storyLinkPreviewContainer.setVisibility(View.GONE);
    }

    private void setStoryTitle(Story story, String fallback) {
        Context context = storyBinding.storyLinkTitle.getContext();
        if (!TextUtils.isEmpty(story.pdfTitle)) {
            storyBinding.storyLinkTitle.setText(TextSizeImageSpan.createWithTrailingBadge(
                    context, story.pdfTitle, R.drawable.ic_action_pdf));
        } else if (!TextUtils.isEmpty(story.videoTitle)) {
            storyBinding.storyLinkTitle.setText(TextSizeImageSpan.createWithTrailingBadge(
                    context, story.videoTitle, R.drawable.ic_action_video));
        } else {
            storyBinding.storyLinkTitle.setText(fallback);
        }
    }

    private void beginContentTransition(int durationMs) {
        if (binding == null) return;
        AutoTransition transition = new AutoTransition();
        transition.setDuration(durationMs);
        TransitionManager.beginDelayedTransition(binding.linkSummaryContent, transition);
    }

    private void fadeInReferenceView(View view) {
        view.animate().cancel();
        view.animate()
                .alpha(1f)
                .setDuration(REFERENCE_CONTENT_TRANSITION_DURATION_MS)
                .setListener(null)
                .start();
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
                Transition transition = createStoryTransition(
                        overlayHost,
                        cardTransform,
                        resolveStoryImageSourceView(),
                        getStoryPreviewView(),
                        MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
                addStoryImageAlphaRestoreListener(transition);
                TransitionManager.beginDelayedTransition(overlayHost, transition);
                binding.linkSummaryScrim.animate().alpha(1f).setDuration(TRANSFORM_DURATION_MS).start();
                if (imageBinding != null) host.setLinkSummaryImageSourceSuppressed(true);
                setSourceVisible(sourceView, false);
                setSourceVisible(storyImageSourceView, false);
                card.setVisibility(View.VISIBLE);
            } else {
                binding.linkSummaryScrim.setAlpha(1f);
                if (imageBinding != null) host.setLinkSummaryImageSourceSuppressed(true);
                setSourceVisible(sourceView, false);
                setSourceVisible(storyImageSourceView, false);
                card.setVisibility(View.VISIBLE);
            }
            if (imageBinding == null) resizeScroll();
        });
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

    private MaterialContainerTransform createTransform(ViewGroup drawing, View start, View end, int direction) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
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
        transform.setElevationShadowEnabled(true);
        transform.setStartContainerColor(getContainerColor(start));
        transform.setEndContainerColor(getContainerColor(end));
        transform.setStartElevation(start.getElevation());
        transform.setEndElevation(end.getElevation());
        return transform;
    }

    private Transition createStoryTransition(
            @NonNull ViewGroup drawing,
            @NonNull MaterialContainerTransform cardTransform,
            @Nullable ImageView imageStart,
            @Nullable ImageView imageEnd,
            int direction) {
        if (!isUsableStoryImageTransition(drawing, imageStart, imageEnd)) {
            return cardTransform;
        }

        MaterialContainerTransform imageTransform = createStoryImageTransform(
                drawing, imageStart, imageEnd, direction);
        imageTransform.addTarget(imageEnd);
        return new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(cardTransform)
                .addTransition(imageTransform);
    }

    private MaterialContainerTransform createStoryImageTransform(
            @NonNull ViewGroup drawing,
            @NonNull ImageView start,
            @NonNull ImageView end,
            int direction) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        transform.setStartView(start);
        transform.setEndView(end);
        transform.setDuration(TRANSFORM_DURATION_MS);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setDrawingViewId(ensureDrawingViewId(drawing));
        transform.setTransitionDirection(direction);
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS);
        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
        transform.setElevationShadowEnabled(false);
        transform.setAllContainerColors(Color.TRANSPARENT);
        MaterialContainerTransform.ProgressThresholds thresholds =
                new MaterialContainerTransform.ProgressThresholds(0f, 1f);
        transform.setFadeProgressThresholds(thresholds);
        transform.setScaleMaskProgressThresholds(thresholds);
        transform.setShapeMaskProgressThresholds(thresholds);

        ShapeAppearanceModel listShape = createStoryListImageShape(
                direction == MaterialContainerTransform.TRANSITION_DIRECTION_ENTER ? start : end);
        ShapeAppearanceModel dialogShape = createStoryDialogImageShape(end.getResources());
        if (direction == MaterialContainerTransform.TRANSITION_DIRECTION_ENTER) {
            transform.setStartShapeAppearanceModel(listShape);
            transform.setEndShapeAppearanceModel(dialogShape);
        } else {
            transform.setStartShapeAppearanceModel(dialogShape);
            transform.setEndShapeAppearanceModel(listShape);
        }
        return transform;
    }

    private ShapeAppearanceModel createStoryListImageShape(@NonNull ImageView image) {
        float radius = image.getClipToOutline()
                ? image.getResources().getDimension(R.dimen.story_preview_image_corner_radius)
                : 0f;
        return ShapeAppearanceModel.builder().setAllCornerSizes(radius).build();
    }

    private ShapeAppearanceModel createStoryDialogImageShape(@NonNull android.content.res.Resources resources) {
        float radius = Utils.pxFromDpInt(resources, CARD_CORNER_RADIUS_DP);
        return ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .setBottomLeftCornerSize(0f)
                .setBottomRightCornerSize(0f)
                .build();
    }

    private ShapeAppearanceModel createShape(View view) {
        float radius = view == card ? CARD_CORNER_RADIUS_DP
                : view instanceof MaterialCardView ? 8
                : view instanceof ImageView ? 12 : 0;
        return ShapeAppearanceModel.builder().setAllCornerSizes(
                Utils.pxFromDpInt(view.getResources(), (int) radius)).build();
    }

    private int getContainerColor(View view) {
        return view instanceof MaterialCardView
                ? ((MaterialCardView) view).getCardBackgroundColor().getDefaultColor()
                : PreviewImageTintUtils.getTintBaseColor(view.getContext());
    }

    void dismiss(boolean animate) {
        if (overlay == null || dismissing) return;
        if (binding == null || card == null) { removeNow(); return; }
        dismissing = true;
        predictiveBackActive = false;
        cancelSummaryRequest();
        ViewGroup overlayHost = host.getLinkSummaryOverlayHost();
        View end = resolveSourceView();
        host.syncLinkSummaryBackState();
        if (animate && overlayHost != null && isUsableTransition(overlayHost, card, end)) {
            MaterialContainerTransform cardTransform = createTransform(overlayHost, card, end,
                    MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
            cardTransform.addTarget(end);
            Transition transition = createStoryTransition(
                    overlayHost,
                    cardTransform,
                    getStoryPreviewView(),
                    resolveStoryImageSourceView(),
                    MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
            transition.addListener(new TransitionListenerAdapter() {
                private boolean finished;
                @Override public void onTransitionEnd(@NonNull Transition transition) { finish(); }
                @Override public void onTransitionCancel(@NonNull Transition transition) { finish(); }
                private void finish() {
                    if (!finished) {
                        finished = true;
                        transition.removeListener(this);
                        restoreStoryImageSourceAlpha();
                        removeNow();
                    }
                }
            });
            TransitionManager.beginDelayedTransition(overlayHost, transition);
            binding.linkSummaryScrim.animate().alpha(0f).setDuration(TRANSFORM_DURATION_MS).start();
            setSourceVisible(end, true);
            setSourceVisible(resolveStoryImageSourceView(), true);
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
        if (overlay == null) return;
        boolean wasShowingImage = imageBinding != null;
        if (imageBinding != null) CoilUtils.dispose(imageBinding.imageOnlyPreview);
        if (binding != null) ViewCompat.setOnApplyWindowInsetsListener(binding.linkSummaryContent, null);
        setSourceVisible(sourceView, true);
        setSourceVisible(storyImageSourceView, true);
        restoreStoryImageSourceAlpha();
        if (overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
        overlay = null; binding = null; card = null; storyBinding = null;
        referenceBinding = null; imageBinding = null;
        sourceView = null; storyImageSourceView = null; storyImageSourceAlpha = 1f;
        visibleStoryId = NO_STORY_ID; visibleStoryPosition = -1;
        visibleUrl = null; fallbackTitle = null; dismissing = false;
        predictiveBackActive = false; enterTransitionStarted = false;
        if (wasShowingImage) host.setLinkSummaryImageSourceSuppressed(false);
        host.syncLinkSummaryBackState();
    }

    private void cancelSummaryRequest() {
        if (summaryRequest != null) { summaryRequest.cancel(); summaryRequest = null; }
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

    @Nullable private ImageView resolveStoryImageSourceView() {
        if (isUsableTransitionView(storyImageSourceView)) return storyImageSourceView;
        if (visibleStoryId == NO_STORY_ID) return null;
        ImageView resolved = host.findLinkSummaryStoryImageSourceView(visibleStoryId);
        if (isUsableStoryImageSource(resolved)) {
            storyImageSourceView = resolved;
            storyImageSourceAlpha = resolved.getAlpha();
            return resolved;
        }
        return null;
    }

    private boolean isUsableStoryImageSource(@Nullable ImageView image) {
        return isUsableTransitionView(image)
                && image.getVisibility() == View.VISIBLE
                && image.getDrawable() != null;
    }

    private boolean isUsableStoryImageTransition(
            @NonNull ViewGroup drawing,
            @Nullable ImageView start,
            @Nullable ImageView end) {
        return start != null
                && end != null
                && start.getDrawable() != null
                && end.getDrawable() != null
                && isUsableTransition(drawing, start, end);
    }

    private void addStoryImageAlphaRestoreListener(@NonNull Transition transition) {
        if (storyImageSourceView == null) return;
        transition.addListener(new TransitionListenerAdapter() {
            private boolean restored;
            @Override public void onTransitionEnd(@NonNull Transition transition) { restore(); }
            @Override public void onTransitionCancel(@NonNull Transition transition) { restore(); }
            private void restore() {
                if (!restored) {
                    restored = true;
                    transition.removeListener(this);
                    restoreStoryImageSourceAlpha();
                }
            }
        });
    }

    private void restoreStoryImageSourceAlpha() {
        if (storyImageSourceView != null) {
            storyImageSourceView.setAlpha(storyImageSourceAlpha);
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
