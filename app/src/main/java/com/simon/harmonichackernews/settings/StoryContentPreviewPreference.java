package com.simon.harmonichackernews.settings;

import android.animation.ArgbEvaluator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
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
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceStoryContentPreviewBinding;
import com.simon.harmonichackernews.databinding.StoryListItemCardBinding;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryMetaPreviewAnimator;
import com.simon.harmonichackernews.utils.Utils;

public class StoryContentPreviewPreference extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {

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
    private boolean leftAligned;
    private boolean cardStyle;
    private boolean tintCardUsingPreview;
    private int commentsIconResId = R.drawable.ic_action_comment;
    private ValueAnimator previewImageModeAnimator;
    private ValueAnimator cardTintAnimator;
    private ValueAnimator cardAppearanceAnimator;
    private ValueAnimator previewHeightAnimator;
    private ValueAnimator metaContainerAnimator;
    private Integer currentCardBackgroundColor;
    private String previewImageModeOverride;
    private String displayStyleOverride;
    private final View.OnLayoutChangeListener previewContainerLayoutChangeListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft) {
                    String previewImageMode = getCurrentPreviewImageMode();
                    view.post(() -> {
                        if (previewItemContainer == view) {
                            syncPreviewContainerHeight(previewImageMode);
                        }
                    });
                }
            };

    public StoryContentPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        View view = LayoutInflater.from(context).inflate(
                R.layout.preference_story_content_preview,
                this,
                false);
        addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        bindPreviewLayout(view);
    }

    public StoryContentPreviewPreference(Context context) {
        this(context, null);
    }

    private void bindPreviewLayout(View itemView) {
        itemView.setClickable(false);
        itemView.setFocusable(false);
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
        updatePreview(null, showPoints, null, null, null, null, null, null, true);
    }

    public void updateCompactPoints(boolean compactPoints) {
        updatePointsText(
                SettingsUtils.shouldShowPoints(getContext()),
                compactPoints,
                SettingsUtils.shouldIncludeTopLevelDomain(getContext()),
                true);
        syncPreviewContainerHeight(getCurrentPreviewImageMode(), true);
    }

    public void updateIncludeTopLevelDomain(boolean includeTopLevelDomain) {
        updatePointsText(
                SettingsUtils.shouldShowPoints(getContext()),
                SettingsUtils.shouldUseCompactPoints(getContext()),
                includeTopLevelDomain,
                true);
        syncPreviewContainerHeight(getCurrentPreviewImageMode(), true);
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        clearPreviewViews();
        super.onDetachedFromWindow();
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
        cancelCardAppearanceAnimator();
        cancelPreviewHeightAnimator();
        cancelMetaContainerAnimator();
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
                inflatePreviewStoryItemBinding(leftAlign);
        View itemView = binding.root;
        previewItemContainer.addView(itemView, createPreviewItemLayoutParams());
        bindPreviewItem(binding);
    }

    private void bindPreviewItem(PreviewStoryItemBinding binding) {
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
        configureStoryCardAppearance(false);
        bindStaticPreviewContent();
    }

    private void configureStoryCardAppearance(boolean animate) {
        if (storyCard == null) {
            return;
        }

        int targetStrokeWidth = cardStyle ? Utils.pxFromDpInt(storyCard.getResources(), 1) : 0;
        float targetElevation = cardStyle ? Utils.pxFromDpInt(storyCard.getResources(), 1) : 0f;
        int targetStrokeColor = cardStyle
                ? MaterialColors.getColor(storyCard, R.attr.commentDividerColor, Color.TRANSPARENT)
                : Color.TRANSPARENT;
        setStoryCardChrome(targetStrokeWidth, targetStrokeColor, targetElevation, animate);
    }

    private void setStoryCardChrome(
            int targetStrokeWidth,
            int targetStrokeColor,
            float targetElevation,
            boolean animate) {
        MaterialCardView targetCard = storyCard;
        if (targetCard == null) {
            return;
        }

        cancelCardAppearanceAnimator();
        int currentStrokeWidth = targetCard.getStrokeWidth();
        float currentElevation = targetCard.getCardElevation();
        if (!animate || !ViewCompat.isLaidOut(targetCard)) {
            targetCard.setStrokeWidth(targetStrokeWidth);
            targetCard.setStrokeColor(targetStrokeColor);
            targetCard.setCardElevation(targetElevation);
            return;
        }

        cardAppearanceAnimator = ValueAnimator.ofFloat(0f, 1f);
        cardAppearanceAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        cardAppearanceAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        cardAppearanceAnimator.addUpdateListener(animation -> {
            if (storyCard != targetCard) {
                animation.cancel();
                return;
            }

            float progress = (float) animation.getAnimatedValue();
            targetCard.setStrokeWidth(lerp(currentStrokeWidth, targetStrokeWidth, progress));
            targetCard.setStrokeColor(targetStrokeColor);
            targetCard.setCardElevation(currentElevation + (targetElevation - currentElevation) * progress);
        });
        cardAppearanceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cardAppearanceAnimator != animation) {
                    return;
                }
                targetCard.setStrokeWidth(targetStrokeWidth);
                targetCard.setStrokeColor(targetStrokeColor);
                targetCard.setCardElevation(targetElevation);
                cardAppearanceAnimator = null;
            }
        });
        cardAppearanceAnimator.start();
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
            if (storyLinkLayout instanceof ViewGroup) {
                setPreviewLayoutTransition((ViewGroup) storyLinkLayout);
            }
        }
        if (metaContainer instanceof ViewGroup) {
            setPreviewLayoutTransition((ViewGroup) metaContainer);
        }
        if (commentLayout != null) {
            commentLayout.setClickable(false);
            commentLayout.setFocusable(false);
            commentLayout.setContentDescription("Preview comment button");
            if (commentLayout instanceof ViewGroup) {
                setPreviewLayoutTransition((ViewGroup) commentLayout);
            }
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
        applyCommentButtonAlignment(leftAligned, false);
        applyTextSize(SettingsUtils.getPreferredStoryTextSize(getContext()), false);
    }

    private void setPreviewLayoutTransition(ViewGroup viewGroup) {
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        transition.enableTransitionType(LayoutTransition.CHANGING);
        viewGroup.setLayoutTransition(transition);
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
        updatePreview(null, null, null, null, null, null, null, previewImageMode, animate);
    }

    private void applyDisplayStyle(String displayStyle, boolean animate) {
        displayStyleOverride = displayStyle;
        boolean useCardStyle = SettingsUtils.STORY_DISPLAY_STYLE_CARD.equals(displayStyle);
        if (useCardStyle == cardStyle) {
            return;
        }

        cancelPreviewImageModeAnimator();
        cardStyle = useCardStyle;
        configureStoryCardAppearance(animate);
        updateStoryCardBackground(getCurrentPreviewImageMode(), animate);
        syncPreviewContainerHeight(getCurrentPreviewImageMode(), animate);
    }

    private void applyTintCardUsingPreview(boolean useTintCardUsingPreview, boolean animate) {
        if (useTintCardUsingPreview == tintCardUsingPreview) {
            updateStoryCardBackground(getCurrentPreviewImageMode(), animate);
            return;
        }

        tintCardUsingPreview = useTintCardUsingPreview;
        configureStoryCardAppearance(animate);
        updateStoryCardBackground(getCurrentPreviewImageMode(), animate);
    }

    private void applyTextSize(float textSize, boolean animate) {
        float clampedTextSize = SettingsUtils.clampStoryTextSize(textSize);
        applyStoryTypefacesAndTextSizes(storyTitle, storyMeta, storyIndex, comments, clampedTextSize);
        syncPreviewContainerHeight(getCurrentPreviewImageMode(), animate);
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
            applyCommentButtonAlignment(shouldLeftAlign, animate);
        }

        int targetMetaVisibility = compact ? View.GONE : View.VISIBLE;
        int targetCommentsVisibility = compact ? View.GONE : View.VISIBLE;
        boolean compactVisibilityChanged =
                metaContainer != null && metaContainer.getVisibility() != targetMetaVisibility
                        || comments != null && comments.getVisibility() != targetCommentsVisibility;

        updateMetaContainer(targetMetaVisibility == View.VISIBLE, animate);
        updateFavicon(showThumbnails, animate);

        updateStoryIndex(showIndex, animate);
        updatePreviewImage(previewImageMode);
        updateStoryCardBackground(previewImageMode, animate);
        updatePointsText(showPoints, compactPoints, includeTopLevelDomain, animate && !compactVisibilityChanged);
        updateCommentCount(showCommentsCount, compact, animate);
        updateHotnessIcon(hotness, animate);
        if (syncHeight) {
            syncPreviewContainerHeight(previewImageMode, animate);
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

    private void applyCommentButtonAlignment(boolean leftAlign, boolean animate) {
        applyCommentButtonAlignment(storyLinkLayout, commentLayout, leftAlign, animate);
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
            float startProgress = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(displayedPreviewImageMode)
                    ? getSmallPreviewImageProgress(layoutState)
                    : 0f;
            updatePreview(null, null, null, null, null, null, null, targetPreviewImageMode, true, false);
            syncPreviewContainerHeight(targetPreviewImageMode);
            setSmallPreviewImageProgress(layoutState, startProgress, true);
            animateSmallPreviewImage(layoutState, startProgress, 1f, () -> {
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
        updateStoryCardBackground(targetPreviewImageMode, true);
        animateSmallPreviewImage(layoutState, getSmallPreviewImageProgress(layoutState), 0f, () -> {
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
        return new SmallPreviewLayoutState(width, height, startMargin, endMargin);
    }

    private float getSmallPreviewImageProgress(SmallPreviewLayoutState layoutState) {
        if (smallPreviewImage == null || layoutState.width <= 0) {
            return 0f;
        }

        ViewGroup.LayoutParams layoutParams = smallPreviewImage.getLayoutParams();
        if (layoutParams == null || layoutParams.width <= 0) {
            return smallPreviewImage.getVisibility() == View.VISIBLE ? 1f : 0f;
        }
        return Math.max(0f, Math.min(1f, layoutParams.width / (float) layoutState.width));
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
        syncPreviewContainerHeight(previewImageMode, false);
    }

    private void syncPreviewContainerHeight(String previewImageMode, boolean animate) {
        PreviewHeights heights = calculatePreviewHeights(previewImageMode);
        if (heights.isValid()) {
            applyPreviewHeights(heights, animate);
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

        int targetContentHeight = measureReservedPreviewItemHeight(containerWidth, previewImageMode);
        int targetContainerHeight = targetContentHeight
                + previewItemContainer.getPaddingTop()
                + previewItemContainer.getPaddingBottom();
        int targetRootHeight = targetContainerHeight
                + previewRoot.getPaddingTop()
                + previewRoot.getPaddingBottom();
        return new PreviewHeights(targetContentHeight, targetContainerHeight, targetRootHeight);
    }

    private boolean isStablePreviewWidth(int containerWidth) {
        return containerWidth >= Utils.pxFromDpInt(previewItemContainer.getResources(), MIN_STABLE_PREVIEW_WIDTH_DP);
    }

    private int measureReservedPreviewItemHeight(int containerWidth, String previewImageMode) {
        if (SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)) {
            return measureCurrentPreviewItemHeight(containerWidth, previewImageMode);
        }

        int offHeight = measureCurrentPreviewItemHeight(
                containerWidth,
                SettingsUtils.STORY_PREVIEW_IMAGE_OFF);
        int smallHeight = measureCurrentPreviewItemHeight(
                containerWidth,
                SettingsUtils.STORY_PREVIEW_IMAGE_SMALL);
        return Math.max(offHeight, smallHeight);
    }

    private int measureCurrentPreviewItemHeight(int containerWidth, String previewImageMode) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0) {
            return 0;
        }
        PreviewStoryItemBinding binding =
                inflatePreviewStoryItemBinding(leftAligned);
        View itemView = binding.root;
        bindCurrentPreviewItemForMeasurement(binding, previewImageMode);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        itemView.measure(widthSpec, heightSpec);
        return PreviewPreferenceViewUtils.getMeasuredOuterHeight(itemView);
    }

    private void bindCurrentPreviewItemForMeasurement(PreviewStoryItemBinding binding, String previewImageMode) {
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(storyTitle, binding.storyTitle);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(storyIndex, binding.storyIndex);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(storyMeta, binding.storyMeta);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(comments, binding.comments);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(metaContainer, binding.metaContainer);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(favicon, binding.favicon);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(smallPreviewImage, binding.smallPreviewImage);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(largePreviewImage, binding.largePreviewImage);
        applyPreviewImageVisibilityForMeasurement(binding, previewImageMode);
    }

    private void applyPreviewImageVisibilityForMeasurement(
            PreviewStoryItemBinding binding,
            String previewImageMode) {
        if (binding.smallPreviewImage != null) {
            binding.smallPreviewImage.setVisibility(
                    SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(previewImageMode)
                            ? View.VISIBLE
                            : View.GONE);
        }
        if (binding.largePreviewImage != null) {
            binding.largePreviewImage.setVisibility(
                    SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                            ? View.VISIBLE
                            : View.GONE);
        }
    }

    private void applyPreviewHeights(PreviewHeights heights) {
        applyPreviewHeights(heights, false);
    }

    private void applyPreviewHeights(PreviewHeights heights, boolean animate) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null || !heights.isValid()) {
            return;
        }

        if (animate && ViewCompat.isLaidOut(previewItemContainer)) {
            animatePreviewHeights(heights);
            return;
        }

        cancelPreviewHeightAnimator();
        setPreviewHeights(heights);
    }

    private void setPreviewHeights(PreviewHeights heights) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null || !heights.isValid()) {
            return;
        }

        View previewItem = previewItemContainer.getChildAt(0);
        PreviewPreferenceViewUtils.setWrapContentHeight(previewItem);
        PreviewPreferenceViewUtils.setExactHeight(previewItemContainer, heights.containerHeight);
        previewRoot.setMinimumHeight(heights.rootHeight);
        setMinimumHeight(heights.rootHeight);
        requestPreviewRemeasure();
    }

    private void animatePreviewHeights(PreviewHeights targetHeights) {
        if (previewItemContainer == null || previewRoot == null || !targetHeights.isValid()) {
            return;
        }

        int startContainerHeight = getCurrentHeightForAnimation(previewItemContainer, targetHeights.containerHeight);
        int startRootHeight = Math.max(previewRoot.getHeight(), previewRoot.getMinimumHeight());
        if (startRootHeight <= 0) {
            startRootHeight = targetHeights.rootHeight;
        }
        int startSelfHeight = Math.max(getHeight(), getMinimumHeight());
        if (startSelfHeight <= 0) {
            startSelfHeight = targetHeights.rootHeight;
        }

        if (startContainerHeight == targetHeights.containerHeight
                && startRootHeight == targetHeights.rootHeight
                && startSelfHeight == targetHeights.rootHeight) {
            setPreviewHeights(targetHeights);
            return;
        }

        cancelPreviewHeightAnimator();
        int finalStartRootHeight = startRootHeight;
        int finalStartSelfHeight = startSelfHeight;
        previewHeightAnimator = ValueAnimator.ofFloat(0f, 1f);
        previewHeightAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        previewHeightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        previewHeightAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            PreviewHeights frameHeights = new PreviewHeights(
                    targetHeights.contentHeight,
                    lerp(startContainerHeight, targetHeights.containerHeight, progress),
                    lerp(finalStartRootHeight, targetHeights.rootHeight, progress));
            setPreviewHeights(frameHeights);
            setMinimumHeight(lerp(finalStartSelfHeight, targetHeights.rootHeight, progress));
        });
        previewHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (previewHeightAnimator != animation) {
                    return;
                }
                previewHeightAnimator = null;
                setPreviewHeights(targetHeights);
            }
        });
        previewHeightAnimator.start();
    }

    private int getCurrentHeightForAnimation(View view, int fallbackHeight) {
        int currentHeight = view.getHeight();
        if (currentHeight > 0) {
            return currentHeight;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height > 0) {
            return layoutParams.height;
        }
        return fallbackHeight;
    }

    private int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    private void clearPreviewHeights() {
        cancelPreviewHeightAnimator();
        if (previewItemContainer != null) {
            if (previewItemContainer.getChildCount() > 0) {
                PreviewPreferenceViewUtils.setWrapContentHeight(previewItemContainer.getChildAt(0));
            }
            PreviewPreferenceViewUtils.setWrapContentHeight(previewItemContainer);
        }
        if (previewRoot != null) {
            previewRoot.setMinimumHeight(0);
        }
        setMinimumHeight(0);
    }

    private FrameLayout.LayoutParams createPreviewItemLayoutParams() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        return layoutParams;
    }

    private PreviewStoryItemBinding inflatePreviewStoryItemBinding(boolean leftAlign) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        StoryListItemCardBinding binding =
                StoryListItemCardBinding.inflate(inflater, previewItemContainer, false);
        PreviewStoryItemBinding itemBinding = new PreviewStoryItemBinding(
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
        applyCommentButtonAlignment(itemBinding.storyLinkLayout, itemBinding.commentLayout, leftAlign, false);
        return itemBinding;
    }

    private void applyCommentButtonAlignment(
            View targetStoryLinkLayout,
            View targetCommentLayout,
            boolean leftAlign,
            boolean animate) {
        if (targetStoryLinkLayout == null
                || targetCommentLayout == null
                || !(targetCommentLayout.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup row = (ViewGroup) targetCommentLayout.getParent();
        int commentIndex = row.indexOfChild(targetCommentLayout);
        int storyIndex = row.indexOfChild(targetStoryLinkLayout);
        if (commentIndex < 0 || storyIndex < 0) {
            return;
        }

        boolean currentlyLeftAligned = commentIndex < storyIndex;
        if (currentlyLeftAligned == leftAlign) {
            return;
        }

        targetCommentLayout.animate().cancel();
        targetStoryLinkLayout.animate().cancel();
        int oldCommentLeft = targetCommentLayout.getLeft();
        int oldStoryLeft = targetStoryLinkLayout.getLeft();

        row.removeView(targetCommentLayout);
        row.addView(targetCommentLayout, leftAlign ? 0 : row.getChildCount());
        row.requestLayout();

        if (!animate || !ViewCompat.isLaidOut(row)) {
            targetCommentLayout.setTranslationX(0f);
            targetStoryLinkLayout.setTranslationX(0f);
            return;
        }

        row.post(() -> {
            if (targetCommentLayout.getParent() != row) {
                return;
            }

            int newCommentLeft = targetCommentLayout.getLeft();
            int newStoryLeft = targetStoryLinkLayout.getLeft();
            targetCommentLayout.setTranslationX(oldCommentLeft - newCommentLeft);
            targetStoryLinkLayout.setTranslationX(oldStoryLeft - newStoryLeft);
            targetCommentLayout.animate()
                    .translationX(0f)
                    .setDuration(PREVIEW_ANIMATION_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
            targetStoryLinkLayout.animate()
                    .translationX(0f)
                    .setDuration(PREVIEW_ANIMATION_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        });
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
        PreviewPreferenceViewUtils.requestPreviewRemeasure(previewItemContainer, previewRoot, this);
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

    private void updateMetaContainer(boolean showMeta, boolean animate) {
        if (metaContainer == null) {
            return;
        }

        cancelMetaContainerAnimator();
        metaContainerAnimator = animateVerticalVisibility(metaContainer, showMeta, animate);
    }

    private void updateFavicon(boolean showThumbnails, boolean animate) {
        if (favicon == null) {
            return;
        }

        updateVisibilityWithLayoutTransition(favicon, showThumbnails, animate);
    }

    private void updateStoryIndex(boolean showIndex, boolean animate) {
        if (storyIndex == null) {
            return;
        }

        updateVisibilityWithLayoutTransition(storyIndex, showIndex, animate);
    }

    private void updateVisibilityWithLayoutTransition(View view, boolean visible, boolean animate) {
        if (view == null) {
            return;
        }

        if (!animate) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }

        view.animate().cancel();
        view.setAlpha(1f);
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        requestPreviewRemeasure();
    }

    private ValueAnimator animateVerticalVisibility(View view, boolean visible, boolean animate) {
        if (view == null) {
            return null;
        }

        view.animate().cancel();
        int originalHeight = getLayoutHeightSpec(view);
        int targetHeight = measureCurrentViewHeight(view);
        if (targetHeight <= 0) {
            targetHeight = view.getHeight();
        }
        if (targetHeight <= 0) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            restoreLayoutHeight(view, originalHeight);
            return null;
        }

        if (!animate || !isPreviewLaidOut()) {
            restoreLayoutHeight(view, originalHeight);
            view.setAlpha(1f);
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            return null;
        }

        if (visible && view.getVisibility() == View.VISIBLE) {
            restoreLayoutHeight(view, originalHeight);
            view.setAlpha(1f);
            return null;
        }
        if (!visible && view.getVisibility() == View.GONE) {
            restoreLayoutHeight(view, originalHeight);
            view.setAlpha(1f);
            return null;
        }

        int finalTargetHeight = targetHeight;
        float startProgress = getVerticalVisibilityProgress(view, finalTargetHeight);
        if (visible) {
            view.setVisibility(View.VISIBLE);
            if (view.getHeight() <= 0) {
                setLayoutHeight(view, 0);
            }
            view.setAlpha(startProgress);
        }

        ValueAnimator animator = ValueAnimator.ofFloat(startProgress, visible ? 1f : 0f);
        animator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setLayoutHeight(view, Math.max(1, Math.round(finalTargetHeight * progress)));
            view.setAlpha(progress);
            requestPreviewRemeasure();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                restoreLayoutHeight(view, originalHeight);
                view.setAlpha(1f);
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
                requestPreviewRemeasure();
            }
        });
        animator.start();
        return animator;
    }

    private float getVerticalVisibilityProgress(View view, int fullHeight) {
        if (view == null || fullHeight <= 0) {
            return 0f;
        }

        int currentHeight = view.getHeight();
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height > 0) {
            currentHeight = layoutParams.height;
        }
        if (currentHeight <= 0) {
            return view.getVisibility() == View.VISIBLE ? 1f : 0f;
        }
        return Math.max(0f, Math.min(1f, currentHeight / (float) fullHeight));
    }

    private int measureCurrentViewHeight(View view) {
        if (view == null) {
            return 0;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        int originalVisibility = view.getVisibility();
        int originalHeight = layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (layoutParams != null) {
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(layoutParams);
        }
        view.setVisibility(View.VISIBLE);

        int width = view.getWidth();
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        if (width <= 0 && parent != null) {
            width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        }
        int widthSpec = View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        int measuredHeight = view.getMeasuredHeight();

        if (layoutParams != null) {
            layoutParams.height = originalHeight;
            view.setLayoutParams(layoutParams);
        }
        view.setVisibility(originalVisibility);
        return measuredHeight;
    }

    private int getLayoutHeightSpec(View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        return layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private void setLayoutHeight(View view, int height) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null) {
            return;
        }
        if (layoutParams.height != height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
    }

    private void restoreLayoutHeight(View view, int height) {
        setLayoutHeight(view, height);
    }

    private boolean isPreviewLaidOut() {
        return previewRoot != null && ViewCompat.isLaidOut(previewRoot);
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

        int targetColor = getUntintedCardBackgroundColor(storyCard);
        if (shouldTintPreviewCard()) {
            Drawable tintDrawable = ContextCompat.getDrawable(
                    getContext(),
                    getPreviewCardTintDrawableRes(previewImageMode));
            if (tintDrawable != null) {
                try {
                    targetColor = PreviewImageTintUtils.calculateCardTint(getContext(), tintDrawable);
                } catch (RuntimeException ignored) {
                    targetColor = getUntintedCardBackgroundColor(storyCard);
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

    private void cancelCardAppearanceAnimator() {
        if (cardAppearanceAnimator == null) {
            return;
        }

        cardAppearanceAnimator.removeAllUpdateListeners();
        cardAppearanceAnimator.removeAllListeners();
        cardAppearanceAnimator.cancel();
        cardAppearanceAnimator = null;
    }

    private void cancelPreviewHeightAnimator() {
        if (previewHeightAnimator == null) {
            return;
        }

        previewHeightAnimator.removeAllUpdateListeners();
        previewHeightAnimator.removeAllListeners();
        previewHeightAnimator.cancel();
        previewHeightAnimator = null;
    }

    private void cancelMetaContainerAnimator() {
        if (metaContainerAnimator == null) {
            return;
        }

        metaContainerAnimator.removeAllUpdateListeners();
        metaContainerAnimator.removeAllListeners();
        metaContainerAnimator.cancel();
        metaContainerAnimator = null;
    }

    private int getDefaultCardBackgroundColor(View view) {
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
    }

    private int getUntintedCardBackgroundColor(View view) {
        return cardStyle ? getDefaultCardBackgroundColor(view) : getSettingsBackgroundColor(view);
    }

    private int getSettingsBackgroundColor(View view) {
        return MaterialColors.getColor(
                view,
                android.R.attr.colorBackground,
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT));
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

        String targetText = showCommentsCount ? "18" : "";
        int targetVisibility = compact || !showCommentsCount ? View.GONE : View.VISIBLE;
        currentComments.animate().cancel();

        if (!animate) {
            currentComments.setText(targetText);
            currentComments.setVisibility(targetVisibility);
            currentComments.setAlpha(1f);
            return;
        }

        if (currentComments.getVisibility() != targetVisibility) {
            currentComments.setText(targetText);
            currentComments.setAlpha(1f);
            currentComments.setScaleX(1f);
            currentComments.setScaleY(1f);
            currentComments.setVisibility(targetVisibility);
            return;
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
