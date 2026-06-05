package com.simon.harmonichackernews.settings;

import android.animation.ArgbEvaluator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceStoryContentPreviewBinding;
import com.simon.harmonichackernews.databinding.StoryListItemBinding;
import com.simon.harmonichackernews.databinding.StoryListItemCardBinding;
import com.simon.harmonichackernews.databinding.StoryListItemCardLeftBinding;
import com.simon.harmonichackernews.databinding.StoryListItemLeftBinding;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryMetaPreviewAnimator;
import com.simon.harmonichackernews.utils.Utils;

public class StoryContentPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long PREVIEW_ANIMATION_DURATION_MS = 180;
    private static final long PREVIEW_TEXT_FADE_DURATION_MS = 90;
    private static final int MIN_STABLE_PREVIEW_WIDTH_DP = 240;
    private static final int SMALL_PREVIEW_WIDTH_DP = 72;
    private static final int SMALL_PREVIEW_HEIGHT_DP = 52;
    private static final int SMALL_PREVIEW_START_MARGIN_DP = 8;
    private static final int SMALL_PREVIEW_END_MARGIN_DP = 2;
    private static final String PREVIEW_STORY_TITLE = "Algorithm breaks speed limit for solving linear equations";
    private static final String PREVIEW_STORY_COMMENTS = "18";

    private ViewGroup previewRoot;
    private ViewGroup previewItemContainer;
    private View metaContainer;
    private View storyLinkLayout;
    private View commentLayout;
    private ImageView favicon;
    private ImageView commentsIcon;
    private ImageView smallPreviewImage;
    private ImageView largePreviewImage;
    private MaterialCardView storyCard;
    private TextView storyTitle;
    private TextView storyIndex;
    private TextView storyMeta;
    private TextView comments;
    private View boundItemView;
    private boolean leftAligned;
    private boolean cardStyle;
    private boolean tintCardUsingPreview;
    private int commentsIconResId = R.drawable.ic_action_comment;
    private ValueAnimator previewImageModeAnimator;
    private ValueAnimator cardTintAnimator;
    private Integer currentCardBackgroundColor;
    private String previewImageModeOverride;
    private String displayStyleOverride;
    private final View.OnLayoutChangeListener previewContainerLayoutChangeListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft) {
                    syncPreviewContainerHeight(getCurrentPreviewImageMode());
                }
            };

    public StoryContentPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_story_content_preview);
        setSelectable(false);
    }

    public StoryContentPreviewPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);
        holder.setIsRecyclable(false);

        View itemView = holder.itemView;
        boundItemView = itemView;
        PreferenceStoryContentPreviewBinding binding =
                PreferenceStoryContentPreviewBinding.bind(itemView);
        previewRoot = binding.storyContentPreviewRoot;
        previewItemContainer = binding.storyContentPreviewItemContainer;
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
            previewItemContainer.addOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        leftAligned = SettingsUtils.shouldUseLeftAlign(getContext());
        cardStyle = SettingsUtils.shouldUseCardStoryDisplayStyle(getContext());
        tintCardUsingPreview = SettingsUtils.shouldTintCardUsingPreview(getContext());
        inflatePreviewItem(leftAligned);
        updatePreview(false);
        syncPreviewContainerHeight(getCurrentPreviewImageMode());
        if (previewItemContainer != null) {
            previewItemContainer.post(() -> syncPreviewContainerHeight(getCurrentPreviewImageMode()));
        }
        itemView.requestLayout();
    }

    public void updateThumbnails(boolean showThumbnails) {
        updatePreview(showThumbnails, null, null, null, null, null, null, null, true);
    }

    public void updatePoints(boolean showPoints) {
        updatePreview(null, showPoints, null, null, null, null, null, null, false);
    }

    public void updateCompactPoints(boolean compactPoints) {
        updatePointsText(
                SettingsUtils.shouldShowPoints(getContext()),
                compactPoints,
                SettingsUtils.shouldIncludeTopLevelDomain(getContext()),
                true);
        syncPreviewContainerHeight(getCurrentPreviewImageMode());
    }

    public void updateIncludeTopLevelDomain(boolean includeTopLevelDomain) {
        updatePointsText(
                SettingsUtils.shouldShowPoints(getContext()),
                SettingsUtils.shouldUseCompactPoints(getContext()),
                includeTopLevelDomain,
                false);
        syncPreviewContainerHeight(getCurrentPreviewImageMode());
    }

    public void updateCommentsCount(boolean showCommentsCount) {
        updatePreview(null, null, showCommentsCount, null, null, null, null, null, true);
    }

    public void updateShowIndex(boolean showIndex) {
        updatePreview(null, null, null, showIndex, null, null, null, null, true);
    }

    public void updateLeftAlign(boolean leftAlign) {
        updatePreview(null, null, null, null, leftAlign, null, null, null, true);
    }

    public void updateCompact(boolean compact) {
        updatePreview(null, null, null, null, null, compact, null, null, true);
    }

    public void updateHotness(String hotnessValue) {
        updatePreview(null, null, null, null, null, null, parseHotness(hotnessValue), null, true);
    }

    public void updatePreviewImageMode(String previewImageMode) {
        applyPreviewImageMode(previewImageMode, true);
    }

    public void updateDisplayStyle(String displayStyle) {
        applyDisplayStyle(displayStyle, true);
    }

    public void updateTextSize(String textSize) {
        applyTextSize(parseTextSize(textSize), true);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        SharedPreferences preferences = getSharedPreferences();
        if (preferences != null) {
            preferences.registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onDetached() {
        SharedPreferences preferences = getSharedPreferences();
        if (preferences != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        clearPreviewViews();
        super.onDetached();
    }

    private void clearPreviewViews() {
        cancelPreviewImageModeAnimator();
        if (comments != null) {
            comments.animate().cancel();
        }
        if (commentsIcon != null) {
            commentsIcon.animate().cancel();
        }
        cancelCardTintAnimator();
        previewRoot = null;
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        previewItemContainer = null;
        metaContainer = null;
        storyLinkLayout = null;
        commentLayout = null;
        favicon = null;
        commentsIcon = null;
        smallPreviewImage = null;
        largePreviewImage = null;
        storyCard = null;
        storyTitle = null;
        storyIndex = null;
        storyMeta = null;
        comments = null;
        boundItemView = null;
        tintCardUsingPreview = false;
        displayStyleOverride = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE.equals(key)) {
            String previewImageMode = sharedPreferences.getString(
                    key,
                    SettingsUtils.STORY_PREVIEW_IMAGE_OFF);
            if (previewImageMode != null && previewImageMode.equals(previewImageModeOverride)) {
                return;
            }
            applyPreviewImageMode(previewImageMode, true);
            return;
        }

        if (SettingsUtils.PREF_STORY_DISPLAY_STYLE.equals(key)) {
            String displayStyle = sharedPreferences.getString(
                    key,
                    SettingsUtils.STORY_DISPLAY_STYLE_STANDARD);
            if (displayStyle != null && displayStyle.equals(displayStyleOverride)) {
                return;
            }
            applyDisplayStyle(displayStyle, true);
            return;
        }

        if (SettingsUtils.PREF_STORY_TEXT_SIZE.equals(key)) {
            applyTextSize(SettingsUtils.getPreferredStoryTextSize(getContext()), true);
            return;
        }

        if (SettingsUtils.PREF_FONT.equals(key)) {
            FontUtils.init(getContext());
            applyTextSize(SettingsUtils.getPreferredStoryTextSize(getContext()), false);
            syncPreviewContainerHeight(getCurrentPreviewImageMode());
            return;
        }

        if (SettingsUtils.PREF_TINT_CARD_USING_PREVIEW.equals(key)) {
            applyTintCardUsingPreview(SettingsUtils.shouldTintCardUsingPreview(getContext()), true);
            return;
        }

        if ("pref_thumbnails".equals(key)
                || "pref_show_points".equals(key)
                || SettingsUtils.PREF_COMPACT_POINTS.equals(key)
                || "pref_show_comments_count".equals(key)
                || "pref_show_index".equals(key)
                || "pref_left_align".equals(key)
                || "pref_hotness".equals(key)
                || "pref_compact_view".equals(key)) {
            updatePreview(true);
        }
    }

    @SuppressLint("SetTextI18n")
    private void inflatePreviewItem(boolean leftAlign) {
        if (previewItemContainer == null) {
            return;
        }

        cancelPreviewImageModeAnimator();
        cancelCardTintAnimator();
        previewItemContainer.removeAllViews();
        PreviewStoryItemBinding binding =
                inflatePreviewStoryItemBinding(leftAlign, shouldUsePreviewCardShell());
        View itemView = binding.root;
        previewItemContainer.addView(itemView, createPreviewItemLayoutParams());

        storyLinkLayout = binding.storyLinkLayout;
        commentLayout = binding.commentLayout;
        metaContainer = binding.metaContainer;
        favicon = binding.favicon;
        commentsIcon = binding.commentsIcon;
        smallPreviewImage = binding.smallPreviewImage;
        largePreviewImage = binding.largePreviewImage;
        storyCard = binding.storyCard;
        storyTitle = binding.storyTitle;
        storyIndex = binding.storyIndex;
        storyMeta = binding.storyMeta;
        comments = binding.comments;
        currentCardBackgroundColor = storyCard != null
                ? storyCard.getCardBackgroundColor().getDefaultColor()
                : null;

        disablePreviewTextScrolling(storyTitle);
        disablePreviewTextScrolling(storyIndex);
        disablePreviewTextScrolling(storyMeta);
        disablePreviewTextScrolling(comments);
        configureStoryCardAppearance();
        bindStaticPreviewContent();
    }

    private boolean shouldUsePreviewCardShell() {
        return cardStyle || tintCardUsingPreview;
    }

    private void configureStoryCardAppearance() {
        if (storyCard == null) {
            return;
        }

        if (cardStyle) {
            int oneDp = Utils.pxFromDpInt(storyCard.getResources(), 1);
            storyCard.setStrokeWidth(oneDp);
            storyCard.setStrokeColor(MaterialColors.getColor(storyCard, R.attr.commentDividerColor, Color.TRANSPARENT));
            storyCard.setCardElevation(oneDp);
            return;
        }

        storyCard.setStrokeWidth(0);
        storyCard.setStrokeColor(Color.TRANSPARENT);
        storyCard.setCardElevation(0f);
    }

    private void disablePreviewTextScrolling(TextView textView) {
        if (textView == null) {
            return;
        }

        textView.setVerticalScrollBarEnabled(false);
    }

    @SuppressLint("SetTextI18n")
    private void bindStaticPreviewContent() {
        if (storyLinkLayout != null) {
            storyLinkLayout.setClickable(false);
            storyLinkLayout.setFocusable(false);
        }
        if (commentLayout != null) {
            commentLayout.setClickable(false);
            commentLayout.setFocusable(false);
            commentLayout.setContentDescription("Preview comment button");
        }
        if (favicon != null) {
            favicon.setImageResource(R.drawable.quanta);
        }
        if (commentsIcon != null) {
            commentsIconResId = R.drawable.ic_action_comment;
            commentsIcon.setImageResource(commentsIconResId);
            commentsIcon.setAlpha(1f);
            commentsIcon.setScaleX(1f);
            commentsIcon.setScaleY(1f);
        }
        if (smallPreviewImage != null) {
            smallPreviewImage.setImageResource(R.drawable.web_preview);
        }
        if (largePreviewImage != null) {
            largePreviewImage.setImageResource(R.drawable.web_preview);
        }
        if (storyTitle != null) {
            storyTitle.setText(PREVIEW_STORY_TITLE);
        }
        if (storyIndex != null) {
            storyIndex.setText("3.");
            storyIndex.setContentDescription("Story 3");
            storyIndex.setVisibility(View.GONE);
        }
        if (storyMeta != null) {
            StoryMetaPreviewAnimator.setPointsVisible(
                    storyMeta,
                    SettingsUtils.shouldShowPoints(getContext()),
                    SettingsUtils.shouldUseCompactPoints(getContext()),
                    SettingsUtils.shouldIncludeTopLevelDomain(getContext()),
                    false);
        }
        if (comments != null) {
            comments.setText(PREVIEW_STORY_COMMENTS);
            comments.setVisibility(View.VISIBLE);
        }
        applyTextSize(SettingsUtils.getPreferredStoryTextSize(getContext()), false);
    }

    private void updatePreview(boolean animate) {
        updatePreview(null, null, null, null, null, null, null, null, animate);
    }

    private void applyPreviewImageMode(String previewImageMode, boolean animate) {
        String displayedPreviewImageMode = getDisplayedPreviewImageMode();
        previewImageModeOverride = previewImageMode;
        if (previewImageMode.equals(displayedPreviewImageMode)) {
            cancelPreviewImageModeAnimator();
            updatePreview(null, null, null, null, null, null, null, previewImageMode, false);
            requestPreviewRemeasure();
            return;
        }
        if (animate
                && previewRoot != null
                && ViewCompat.isLaidOut(previewRoot)
                && canAnimateSmallPreviewImageMode(displayedPreviewImageMode, previewImageMode)) {
            animateSmallPreviewImageMode(displayedPreviewImageMode, previewImageMode);
            return;
        }
        cancelPreviewImageModeAnimator();
        updatePreview(null, null, null, null, null, null, null, previewImageMode, false);
        requestPreviewRemeasure();
    }

    private void applyDisplayStyle(String displayStyle, boolean animate) {
        displayStyleOverride = displayStyle;
        boolean useCardStyle = SettingsUtils.STORY_DISPLAY_STYLE_CARD.equals(displayStyle);
        if (useCardStyle == cardStyle) {
            return;
        }

        cancelPreviewImageModeAnimator();
        cardStyle = useCardStyle;
        inflatePreviewItem(leftAligned);
        updatePreview(false);
        syncPreviewContainerHeight(getCurrentPreviewImageMode());
        requestPreviewRemeasure();
    }

    private void applyTintCardUsingPreview(boolean useTintCardUsingPreview, boolean animate) {
        if (useTintCardUsingPreview == tintCardUsingPreview) {
            updateStoryCardBackground(getCurrentPreviewImageMode(), animate);
            return;
        }

        boolean wasUsingCardShell = shouldUsePreviewCardShell();
        tintCardUsingPreview = useTintCardUsingPreview;
        boolean cardShellChanged = wasUsingCardShell != shouldUsePreviewCardShell();
        if (cardShellChanged) {
            cancelPreviewImageModeAnimator();
            inflatePreviewItem(leftAligned);
            updatePreview(false);
            if (animate && tintCardUsingPreview && storyCard != null) {
                int defaultColor = getDefaultCardBackgroundColor(storyCard);
                MaterialCardView targetCard = storyCard;
                targetCard.setCardBackgroundColor(defaultColor);
                currentCardBackgroundColor = defaultColor;
                targetCard.post(() -> {
                    if (storyCard == targetCard) {
                        updateStoryCardBackground(getCurrentPreviewImageMode(), true);
                    }
                });
            }
            requestPreviewRemeasure();
            return;
        }

        configureStoryCardAppearance();
        updateStoryCardBackground(getCurrentPreviewImageMode(), animate);
    }

    private void applyTextSize(float textSize, boolean animate) {
        float clampedTextSize = SettingsUtils.clampStoryTextSize(textSize);
        applyStoryTypefacesAndTextSizes(storyTitle, storyMeta, storyIndex, comments, clampedTextSize);
        syncPreviewContainerHeight(getCurrentPreviewImageMode());
        requestPreviewRemeasure();
    }

    private void applyStoryTypefacesAndTextSizes(
            TextView title,
            TextView meta,
            TextView index,
            TextView commentCount,
            float storyTextSize) {
        ensureSelectedFontLoaded();

        if (title != null) {
            FontUtils.setStoryTitleTypeface(title, storyTextSize);
        }
        if (meta != null) {
            FontUtils.setStoryMetaTypeface(meta, storyTextSize);
        }
        if (index != null) {
            index.setTypeface(FontUtils.activeRegular);
        }
        if (commentCount != null) {
            FontUtils.setStoryCommentCountTypeface(commentCount, storyTextSize);
        }
    }

    private void ensureSelectedFontLoaded() {
        String selectedFont = SettingsUtils.getPreferredFont(getContext());
        if (FontUtils.activeRegular == null
                || FontUtils.activeBold == null
                || !selectedFont.equals(FontUtils.font)) {
            FontUtils.init(getContext());
        }
    }

    private String getCurrentPreviewImageMode() {
        return previewImageModeOverride != null
                ? previewImageModeOverride
                : SettingsUtils.getPreferredStoryPreviewImageMode(getContext());
    }

    private void updatePreview(
            Boolean showThumbnailsOverride,
            Boolean showPointsOverride,
            Boolean showCommentsCountOverride,
            Boolean showIndexOverride,
            Boolean leftAlignOverride,
            Boolean compactOverride,
            Integer hotnessOverride,
            String previewImageModeOverrideParam,
            boolean animate) {
        updatePreview(
                showThumbnailsOverride,
                showPointsOverride,
                showCommentsCountOverride,
                showIndexOverride,
                leftAlignOverride,
                compactOverride,
                hotnessOverride,
                previewImageModeOverrideParam,
                animate,
                true);
    }

    private void updatePreview(
            Boolean showThumbnailsOverride,
            Boolean showPointsOverride,
            Boolean showCommentsCountOverride,
            Boolean showIndexOverride,
            Boolean leftAlignOverride,
            Boolean compactOverride,
            Integer hotnessOverride,
            String previewImageModeOverrideParam,
            boolean animate,
            boolean syncHeight) {
        if (previewRoot == null) {
            return;
        }

        boolean compact = compactOverride != null
                ? compactOverride
                : SettingsUtils.shouldUseCompactView(getContext());
        boolean showThumbnails = showThumbnailsOverride != null
                ? showThumbnailsOverride
                : SettingsUtils.shouldShowThumbnails(getContext());
        boolean showPoints = showPointsOverride != null
                ? showPointsOverride
                : SettingsUtils.shouldShowPoints(getContext());
        boolean compactPoints = SettingsUtils.shouldUseCompactPoints(getContext());
        boolean includeTopLevelDomain = SettingsUtils.shouldIncludeTopLevelDomain(getContext());
        boolean showCommentsCount = showCommentsCountOverride != null
                ? showCommentsCountOverride
                : SettingsUtils.shouldShowCommentsCount(getContext());
        boolean showIndex = showIndexOverride != null
                ? showIndexOverride
                : SettingsUtils.shouldShowIndex(getContext());
        boolean shouldLeftAlign = leftAlignOverride != null
                ? leftAlignOverride
                : SettingsUtils.shouldUseLeftAlign(getContext());
        int hotness = hotnessOverride != null
                ? hotnessOverride
                : SettingsUtils.getPreferredHotness(getContext());
        String previewImageMode = previewImageModeOverrideParam != null
                ? previewImageModeOverrideParam
                : this.previewImageModeOverride != null
                ? this.previewImageModeOverride
                : SettingsUtils.getPreferredStoryPreviewImageMode(getContext());
        if (shouldLeftAlign != leftAligned) {
            cancelPreviewImageModeAnimator();
            leftAligned = shouldLeftAlign;
            inflatePreviewItem(shouldLeftAlign);
        }

        int targetMetaVisibility = compact ? View.GONE : View.VISIBLE;
        int targetCommentsVisibility = compact ? View.GONE : View.VISIBLE;
        boolean compactVisibilityChanged =
                metaContainer != null && metaContainer.getVisibility() != targetMetaVisibility
                        || comments != null && comments.getVisibility() != targetCommentsVisibility;

        if (metaContainer != null) {
            metaContainer.setVisibility(targetMetaVisibility);
        }
        if (favicon != null) {
            favicon.setVisibility(showThumbnails ? View.VISIBLE : View.GONE);
        }

        updateStoryIndex(showIndex);
        updatePreviewImage(previewImageMode);
        updateStoryCardBackground(previewImageMode, animate);
        updatePointsText(showPoints, compactPoints, includeTopLevelDomain, animate && !compactVisibilityChanged);
        updateCommentCount(showCommentsCount, compact, animate && !compactVisibilityChanged);
        updateHotnessIcon(hotness, animate);
        if (syncHeight) {
            syncPreviewContainerHeight(previewImageMode);
        }
    }

    private String getDisplayedPreviewImageMode() {
        if (largePreviewImage != null && largePreviewImage.getVisibility() == View.VISIBLE) {
            return SettingsUtils.STORY_PREVIEW_IMAGE_LARGE;
        }
        if (smallPreviewImage != null && smallPreviewImage.getVisibility() == View.VISIBLE) {
            return SettingsUtils.STORY_PREVIEW_IMAGE_SMALL;
        }
        return SettingsUtils.STORY_PREVIEW_IMAGE_OFF;
    }

    private boolean canAnimateSmallPreviewImageMode(String displayedPreviewImageMode, String targetPreviewImageMode) {
        return !SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(displayedPreviewImageMode)
                && (SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(targetPreviewImageMode)
                || SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(targetPreviewImageMode));
    }

    private void animateSmallPreviewImageMode(String displayedPreviewImageMode, String targetPreviewImageMode) {
        if (smallPreviewImage == null) {
            updatePreview(null, null, null, null, null, null, null, targetPreviewImageMode, false);
            requestPreviewRemeasure();
            return;
        }

        cancelPreviewImageModeAnimator();
        SmallPreviewLayoutState layoutState = getSmallPreviewLayoutState();
        boolean showingSmallPreview = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(targetPreviewImageMode);
        if (showingSmallPreview) {
            updatePreview(null, null, null, null, null, null, null, targetPreviewImageMode, false, false);
            syncPreviewContainerHeight(targetPreviewImageMode);
            setSmallPreviewImageProgress(layoutState, 0f, true);
            animateSmallPreviewImage(layoutState, 0f, 1f, () -> {
                setSmallPreviewImageProgress(layoutState, 1f, true);
                previewImageModeAnimator = null;
                requestPreviewRemeasure();
            });
            return;
        }

        if (!SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(displayedPreviewImageMode)) {
            updatePreview(null, null, null, null, null, null, null, targetPreviewImageMode, false);
            requestPreviewRemeasure();
            return;
        }

        syncPreviewContainerHeight(SettingsUtils.STORY_PREVIEW_IMAGE_SMALL);
        animateSmallPreviewImage(layoutState, 1f, 0f, () -> {
            updatePreview(null, null, null, null, null, null, null, targetPreviewImageMode, false, false);
            setSmallPreviewImageProgress(layoutState, 1f, false);
            syncPreviewContainerHeight(targetPreviewImageMode);
            previewImageModeAnimator = null;
            requestPreviewRemeasure();
        });
    }

    private void animateSmallPreviewImage(
            SmallPreviewLayoutState layoutState,
            float startProgress,
            float endProgress,
            Runnable endAction) {
        previewImageModeAnimator = ValueAnimator.ofFloat(startProgress, endProgress);
        previewImageModeAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        previewImageModeAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        previewImageModeAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setSmallPreviewImageProgress(layoutState, progress, true);
        });
        previewImageModeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (previewImageModeAnimator != animation) {
                    return;
                }
                endAction.run();
            }
        });
        previewImageModeAnimator.start();
    }

    private SmallPreviewLayoutState getSmallPreviewLayoutState() {
        int width = Utils.pxFromDpInt(smallPreviewImage.getResources(), SMALL_PREVIEW_WIDTH_DP);
        int height = Utils.pxFromDpInt(smallPreviewImage.getResources(), SMALL_PREVIEW_HEIGHT_DP);
        int startMargin = Utils.pxFromDpInt(smallPreviewImage.getResources(), SMALL_PREVIEW_START_MARGIN_DP);
        int endMargin = Utils.pxFromDpInt(smallPreviewImage.getResources(), SMALL_PREVIEW_END_MARGIN_DP);
        ViewGroup.LayoutParams layoutParams = smallPreviewImage.getLayoutParams();
        if (layoutParams != null) {
            if (layoutParams.width > 0) {
                width = layoutParams.width;
            }
            if (layoutParams.height > 0) {
                height = layoutParams.height;
            }
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
                int currentStartMargin = margins.getMarginStart();
                int currentEndMargin = margins.getMarginEnd();
                if (currentStartMargin > 0 || currentEndMargin > 0) {
                    startMargin = currentStartMargin;
                    endMargin = currentEndMargin;
                }
            }
        }
        return new SmallPreviewLayoutState(width, height, startMargin, endMargin);
    }

    private void setSmallPreviewImageProgress(SmallPreviewLayoutState layoutState, float progress, boolean visible) {
        if (smallPreviewImage == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = smallPreviewImage.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            smallPreviewImage.setAlpha(progress);
            smallPreviewImage.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }

        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
        margins.width = Math.max(0, Math.round(layoutState.width * progress));
        margins.height = layoutState.height;
        margins.setMarginStart(Math.max(0, Math.round(layoutState.startMargin * progress)));
        margins.setMarginEnd(Math.max(0, Math.round(layoutState.endMargin * progress)));
        smallPreviewImage.setLayoutParams(margins);
        smallPreviewImage.setAlpha(progress);
        smallPreviewImage.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void syncPreviewContainerHeight(String previewImageMode) {
        PreviewHeights heights = calculatePreviewHeights(previewImageMode);
        if (heights.isValid()) {
            applyPreviewHeights(heights);
        }
    }

    private PreviewHeights calculatePreviewHeights(String previewImageMode) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null) {
            return PreviewHeights.invalid();
        }

        int containerWidth = previewItemContainer.getWidth()
                - previewItemContainer.getPaddingLeft()
                - previewItemContainer.getPaddingRight();
        if (!isStablePreviewWidth(containerWidth)) {
            clearPreviewHeights();
            return PreviewHeights.invalid();
        }

        int targetContentHeight = measureCurrentPreviewItemHeight(containerWidth);
        int targetContainerHeight = targetContentHeight
                + previewItemContainer.getPaddingTop()
                + previewItemContainer.getPaddingBottom();
        int previewHeaderHeight = previewRoot.getChildCount() > 0
                ? PreviewPreferenceViewUtils.getOuterHeight(previewRoot.getChildAt(0))
                : 0;
        int targetRootHeight = targetContainerHeight
                + previewHeaderHeight
                + previewRoot.getPaddingTop()
                + previewRoot.getPaddingBottom();
        return new PreviewHeights(targetContentHeight, targetContainerHeight, targetRootHeight);
    }

    private boolean isStablePreviewWidth(int containerWidth) {
        return containerWidth >= Utils.pxFromDpInt(previewItemContainer.getResources(), MIN_STABLE_PREVIEW_WIDTH_DP);
    }

    private int measureCurrentPreviewItemHeight(int containerWidth) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0) {
            return 0;
        }
        PreviewStoryItemBinding binding =
                inflatePreviewStoryItemBinding(leftAligned, shouldUsePreviewCardShell());
        View itemView = binding.root;
        bindCurrentPreviewItemForMeasurement(binding);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        itemView.measure(widthSpec, heightSpec);
        return PreviewPreferenceViewUtils.getMeasuredOuterHeight(itemView);
    }

    private void bindCurrentPreviewItemForMeasurement(PreviewStoryItemBinding binding) {
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(storyTitle, binding.storyTitle);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(storyIndex, binding.storyIndex);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(storyMeta, binding.storyMeta);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(comments, binding.comments);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(metaContainer, binding.metaContainer);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(favicon, binding.favicon);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(smallPreviewImage, binding.smallPreviewImage);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(largePreviewImage, binding.largePreviewImage);
    }

    private void applyPreviewHeights(PreviewHeights heights) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null || !heights.isValid()) {
            return;
        }

        View previewItem = previewItemContainer.getChildAt(0);
        PreviewPreferenceViewUtils.setWrapContentHeight(previewItem);
        PreviewPreferenceViewUtils.setExactHeight(previewItemContainer, heights.containerHeight);
        previewRoot.setMinimumHeight(heights.rootHeight);
        if (boundItemView != null) {
            boundItemView.setMinimumHeight(heights.rootHeight);
        }
        requestPreviewRemeasure();
    }

    private void clearPreviewHeights() {
        if (previewItemContainer != null) {
            if (previewItemContainer.getChildCount() > 0) {
                PreviewPreferenceViewUtils.setWrapContentHeight(previewItemContainer.getChildAt(0));
            }
            PreviewPreferenceViewUtils.setWrapContentHeight(previewItemContainer);
        }
        if (previewRoot != null) {
            previewRoot.setMinimumHeight(0);
        }
        if (boundItemView != null) {
            boundItemView.setMinimumHeight(0);
        }
    }

    private FrameLayout.LayoutParams createPreviewItemLayoutParams() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        return layoutParams;
    }

    private PreviewStoryItemBinding inflatePreviewStoryItemBinding(boolean leftAlign, boolean useCardStyle) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (useCardStyle) {
            if (leftAlign) {
                StoryListItemCardLeftBinding binding =
                        StoryListItemCardLeftBinding.inflate(inflater, previewItemContainer, false);
                return new PreviewStoryItemBinding(
                        binding.getRoot(),
                        binding.storyContainer.storyTitle,
                        binding.storyContainer.storyMeta,
                        binding.storyContainer.storyMetaContainer,
                        binding.storyContainer.storyComments,
                        binding.storyContainer.storyLinkLayout,
                        binding.storyContainer.storyCommentLayout,
                        binding.storyContainer.storyCommentsIcon,
                        binding.storyContainer.storyMetaFavicon,
                        binding.storyContainer.storyPreviewImageSmall,
                        binding.storyContainer.storyPreviewImageLarge,
                        binding.storyContainer.storyIndex,
                        binding.storyCard);
            }

            StoryListItemCardBinding binding =
                    StoryListItemCardBinding.inflate(inflater, previewItemContainer, false);
            return new PreviewStoryItemBinding(
                    binding.getRoot(),
                    binding.storyContainer.storyTitle,
                    binding.storyContainer.storyMeta,
                    binding.storyContainer.storyMetaContainer,
                    binding.storyContainer.storyComments,
                    binding.storyContainer.storyLinkLayout,
                    binding.storyContainer.storyCommentLayout,
                    binding.storyContainer.storyCommentsIcon,
                    binding.storyContainer.storyMetaFavicon,
                    binding.storyContainer.storyPreviewImageSmall,
                    binding.storyContainer.storyPreviewImageLarge,
                    binding.storyContainer.storyIndex,
                    binding.storyCard);
        }

        if (leftAlign) {
            StoryListItemLeftBinding binding =
                    StoryListItemLeftBinding.inflate(inflater, previewItemContainer, false);
            return new PreviewStoryItemBinding(
                    binding.getRoot(),
                    binding.storyTitle,
                    binding.storyMeta,
                    binding.storyMetaContainer,
                    binding.storyComments,
                    binding.storyLinkLayout,
                    binding.storyCommentLayout,
                    binding.storyCommentsIcon,
                    binding.storyMetaFavicon,
                    binding.storyPreviewImageSmall,
                    binding.storyPreviewImageLarge,
                    binding.storyIndex,
                    null);
        }

        StoryListItemBinding binding =
                StoryListItemBinding.inflate(inflater, previewItemContainer, false);
        return new PreviewStoryItemBinding(
                binding.getRoot(),
                binding.storyTitle,
                binding.storyMeta,
                binding.storyMetaContainer,
                binding.storyComments,
                binding.storyLinkLayout,
                binding.storyCommentLayout,
                binding.storyCommentsIcon,
                binding.storyMetaFavicon,
                binding.storyPreviewImageSmall,
                binding.storyPreviewImageLarge,
                binding.storyIndex,
                null);
    }

    private static class PreviewStoryItemBinding {
        final View root;
        final TextView storyTitle;
        final TextView storyMeta;
        final View metaContainer;
        final TextView comments;
        final View storyLinkLayout;
        final View commentLayout;
        final ImageView commentsIcon;
        final ImageView favicon;
        final ImageView smallPreviewImage;
        final ImageView largePreviewImage;
        final TextView storyIndex;
        final MaterialCardView storyCard;

        PreviewStoryItemBinding(
                View root,
                TextView storyTitle,
                TextView storyMeta,
                View metaContainer,
                TextView comments,
                View storyLinkLayout,
                View commentLayout,
                ImageView commentsIcon,
                ImageView favicon,
                ImageView smallPreviewImage,
                ImageView largePreviewImage,
                TextView storyIndex,
                MaterialCardView storyCard) {
            this.root = root;
            this.storyTitle = storyTitle;
            this.storyMeta = storyMeta;
            this.metaContainer = metaContainer;
            this.comments = comments;
            this.storyLinkLayout = storyLinkLayout;
            this.commentLayout = commentLayout;
            this.commentsIcon = commentsIcon;
            this.favicon = favicon;
            this.smallPreviewImage = smallPreviewImage;
            this.largePreviewImage = largePreviewImage;
            this.storyIndex = storyIndex;
            this.storyCard = storyCard;
        }
    }

    private static class PreviewHeights {
        final int contentHeight;
        final int containerHeight;
        final int rootHeight;

        PreviewHeights(int contentHeight, int containerHeight, int rootHeight) {
            this.contentHeight = contentHeight;
            this.containerHeight = containerHeight;
            this.rootHeight = rootHeight;
        }

        static PreviewHeights invalid() {
            return new PreviewHeights(0, 0, 0);
        }

        boolean isValid() {
            return contentHeight > 0 && containerHeight > 0 && rootHeight > 0;
        }
    }

    private void requestPreviewRemeasure() {
        PreviewPreferenceViewUtils.requestPreviewRemeasure(previewItemContainer, previewRoot, boundItemView);
    }

    private void updatePointsText(
            boolean showPoints,
            boolean compactPoints,
            boolean includeTopLevelDomain,
            boolean animate) {
        StoryMetaPreviewAnimator.setPointsVisible(
                storyMeta,
                showPoints,
                compactPoints,
                includeTopLevelDomain,
                animate);
    }

    private void updateStoryIndex(boolean showIndex) {
        if (storyIndex == null) {
            return;
        }

        storyIndex.setVisibility(showIndex ? View.VISIBLE : View.GONE);
    }

    private void updatePreviewImage(String previewImageMode) {
        boolean showSmallPreview = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(previewImageMode);
        boolean showLargePreview = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode);
        if (smallPreviewImage != null) {
            smallPreviewImage.setVisibility(showSmallPreview ? View.VISIBLE : View.GONE);
        }
        if (largePreviewImage != null) {
            largePreviewImage.setVisibility(showLargePreview ? View.VISIBLE : View.GONE);
        }
    }

    private void updateStoryCardBackground(String previewImageMode, boolean animate) {
        if (storyCard == null) {
            return;
        }

        int targetColor = getDefaultCardBackgroundColor(storyCard);
        if (shouldTintPreviewCard()) {
            Drawable tintDrawable = ContextCompat.getDrawable(
                    getContext(),
                    getPreviewCardTintDrawableRes(previewImageMode));
            if (tintDrawable != null) {
                try {
                    targetColor = PreviewImageTintUtils.calculateCardTint(getContext(), tintDrawable);
                } catch (RuntimeException ignored) {
                    targetColor = getDefaultCardBackgroundColor(storyCard);
                }
            }
        }

        setStoryCardBackgroundColor(targetColor, animate);
    }

    private boolean shouldTintPreviewCard() {
        return tintCardUsingPreview;
    }

    private int getPreviewCardTintDrawableRes(String previewImageMode) {
        return SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                ? R.drawable.quanta
                : R.drawable.web_preview;
    }

    private void setStoryCardBackgroundColor(int targetColor, boolean animate) {
        MaterialCardView targetCard = storyCard;
        if (targetCard == null) {
            return;
        }

        cancelCardTintAnimator();

        int currentColor = currentCardBackgroundColor != null
                ? currentCardBackgroundColor
                : targetCard.getCardBackgroundColor().getDefaultColor();

        if (!animate || currentColor == targetColor) {
            targetCard.setCardBackgroundColor(targetColor);
            currentCardBackgroundColor = targetColor;
            return;
        }

        cardTintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, targetColor);
        cardTintAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        cardTintAnimator.addUpdateListener(animation -> {
            if (storyCard != targetCard) {
                animation.cancel();
                return;
            }
            int color = (int) animation.getAnimatedValue();
            targetCard.setCardBackgroundColor(color);
            currentCardBackgroundColor = color;
        });
        cardTintAnimator.start();
    }

    private void cancelPreviewImageModeAnimator() {
        if (previewImageModeAnimator == null) {
            return;
        }

        previewImageModeAnimator.removeAllUpdateListeners();
        previewImageModeAnimator.removeAllListeners();
        previewImageModeAnimator.cancel();
        previewImageModeAnimator = null;
    }

    private void cancelCardTintAnimator() {
        if (cardTintAnimator == null) {
            return;
        }

        cardTintAnimator.removeAllUpdateListeners();
        cardTintAnimator.cancel();
        cardTintAnimator = null;
    }

    private int getDefaultCardBackgroundColor(View view) {
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
    }

    private static class SmallPreviewLayoutState {
        final int width;
        final int height;
        final int startMargin;
        final int endMargin;

        SmallPreviewLayoutState(int width, int height, int startMargin, int endMargin) {
            this.width = width;
            this.height = height;
            this.startMargin = startMargin;
            this.endMargin = endMargin;
        }
    }

    private void updateCommentCount(boolean showCommentsCount, boolean compact, boolean animate) {
        TextView currentComments = comments;
        if (currentComments == null) {
            return;
        }

        String targetText = showCommentsCount ? "18" : "\u2022";
        int targetVisibility = compact ? View.GONE : View.VISIBLE;
        currentComments.animate().cancel();

        if (!animate) {
            currentComments.setText(targetText);
            currentComments.setVisibility(targetVisibility);
            currentComments.setAlpha(1f);
            return;
        }

        if (currentComments.getVisibility() != targetVisibility) {
            currentComments.setVisibility(targetVisibility);
            currentComments.setAlpha(1f);
        }

        CharSequence currentText = currentComments.getText();
        if (currentText != null && targetText.contentEquals(currentText)) {
            currentComments.setAlpha(1f);
            return;
        }

        if (targetVisibility != View.VISIBLE) {
            currentComments.setText(targetText);
            return;
        }

        currentComments.animate()
                .alpha(0f)
                .setDuration(PREVIEW_TEXT_FADE_DURATION_MS / 2)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    if (comments != currentComments) {
                        return;
                    }
                    currentComments.setText(targetText);
                    currentComments.animate()
                            .alpha(1f)
                            .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                            .start();
                })
                .start();
    }

    private void updateHotnessIcon(int hotness, boolean animate) {
        ImageView currentCommentsIcon = commentsIcon;
        if (currentCommentsIcon == null) {
            return;
        }

        int targetIconResId = hotness > 0 ? R.drawable.ic_action_whatshot : R.drawable.ic_action_comment;
        currentCommentsIcon.animate().cancel();

        if (targetIconResId == commentsIconResId) {
            currentCommentsIcon.setAlpha(1f);
            currentCommentsIcon.setScaleX(1f);
            currentCommentsIcon.setScaleY(1f);
            return;
        }

        if (!animate) {
            commentsIconResId = targetIconResId;
            currentCommentsIcon.setImageResource(commentsIconResId);
            currentCommentsIcon.setAlpha(1f);
            currentCommentsIcon.setScaleX(1f);
            currentCommentsIcon.setScaleY(1f);
            return;
        }

        currentCommentsIcon.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(PREVIEW_TEXT_FADE_DURATION_MS / 2)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    if (commentsIcon != currentCommentsIcon) {
                        return;
                    }
                    commentsIconResId = targetIconResId;
                    currentCommentsIcon.setImageResource(commentsIconResId);
                    currentCommentsIcon.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                            .start();
                })
                .start();
    }

    private int parseHotness(String hotnessValue) {
        try {
            return Integer.parseInt(hotnessValue);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float parseTextSize(String textSize) {
        try {
            return SettingsUtils.clampStoryTextSize(Float.parseFloat(textSize));
        } catch (NumberFormatException e) {
            return SettingsUtils.DEFAULT_STORY_TEXT_SIZE;
        }
    }
}
