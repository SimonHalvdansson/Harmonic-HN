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
    private static final long PREVIEW_IMAGE_FADE_DURATION_MS = 110;
    private static final long PREVIEW_IMAGE_FADE_START_DELAY_MS = 70;
    private static final int LARGE_PREVIEW_IMAGE_HEIGHT_DP = 176;
    private static final int MIN_STABLE_PREVIEW_WIDTH_DP = 240;
    private static final String PREVIEW_STORY_TITLE = "Algorithm breaks speed limit for solving linear equations";
    private static final String PREVIEW_STORY_COMMENTS = "18";

    private ViewGroup previewRoot;
    private ViewGroup previewItemContainer;
    private ViewGroup storyContainer;
    private View metaContainer;
    private View storyLinkLayout;
    private View commentLayout;
    private View storyRow;
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
    private int commentsIconResId = R.drawable.ic_comment;
    private ValueAnimator cardTintAnimator;
    private ValueAnimator cardAppearanceAnimator;
    private ValueAnimator previewHeightAnimator;
    private ValueAnimator largePreviewImageHeightAnimator;
    private Integer currentCardBackgroundColor;
    private String previewImageModeOverride;
    private String displayStyleOverride;
    private int previewImageAnimationToken;
    private int metaAnimationToken;
    private int commentCountAnimationToken;
    private boolean storyContainerChangingTransitionSuspended;
    private boolean storyContainerChangingTransitionWasEnabled;
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
        if (comments != null) {
            comments.animate().cancel();
        }
        if (commentsIcon != null) {
            commentsIcon.animate().cancel();
        }
        cancelCardTintAnimator();
        cancelCardAppearanceAnimator();
        cancelPreviewHeightAnimator();
        cancelPreviewImageAnimations();
        cancelMetaAnimation();
        resumeStoryContainerChangingTransition();
        previewRoot = null;
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        previewItemContainer = null;
        storyContainer = null;
        metaContainer = null;
        storyLinkLayout = null;
        commentLayout = null;
        storyRow = null;
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

        cancelCardTintAnimator();
        previewItemContainer.removeAllViews();
        PreviewStoryItemBinding binding =
                inflatePreviewStoryItemBinding(leftAlign);
        View itemView = binding.root;
        previewItemContainer.addView(itemView, createPreviewItemLayoutParams());
        bindPreviewItem(binding);
    }

    private void bindPreviewItem(PreviewStoryItemBinding binding) {
        storyContainer = binding.storyContainer;
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
        int currentStrokeColor = targetCard.getStrokeColor();
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
        ArgbEvaluator strokeColorEvaluator = new ArgbEvaluator();
        cardAppearanceAnimator.addUpdateListener(animation -> {
            if (storyCard != targetCard) {
                animation.cancel();
                return;
            }

            float progress = (float) animation.getAnimatedValue();
            targetCard.setStrokeWidth(lerp(currentStrokeWidth, targetStrokeWidth, progress));
            targetCard.setStrokeColor((int) strokeColorEvaluator.evaluate(
                    progress,
                    currentStrokeColor,
                    targetStrokeColor));
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
        if (storyContainer != null) {
            setPreviewLayoutTransition(storyContainer);
        }
        if (storyLinkLayout != null) {
            storyLinkLayout.setClickable(false);
            storyLinkLayout.setFocusable(false);
            if (storyLinkLayout instanceof ViewGroup) {
                setPreviewLayoutTransition((ViewGroup) storyLinkLayout);
            }
        }
        if (metaContainer != null && metaContainer.getParent() instanceof ViewGroup) {
            setPreviewLayoutTransition((ViewGroup) metaContainer.getParent());
        }
        if (metaContainer instanceof ViewGroup) {
            setPreviewLayoutTransition((ViewGroup) metaContainer);
        }
        if (commentLayout != null) {
            commentLayout.setClickable(false);
            commentLayout.setFocusable(false);
            commentLayout.setContentDescription("Preview comment button");
            if (commentLayout.getParent() instanceof View) {
                storyRow = (View) commentLayout.getParent();
            }
            if (commentLayout instanceof ViewGroup) {
                setPreviewLayoutTransition((ViewGroup) commentLayout);
            }
        }
        if (favicon != null) {
            favicon.setImageResource(R.drawable.quanta);
        }
        if (commentsIcon != null) {
            commentsIconResId = R.drawable.ic_comment;
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
        transition.setAnimateParentHierarchy(false);
        viewGroup.setLayoutTransition(transition);
    }

    private void updatePreview(boolean animate) {
        updatePreview(null, null, null, null, null, null, null, null, animate);
    }

    private void applyPreviewImageMode(String previewImageMode, boolean animate) {
        String displayedPreviewImageMode = getDisplayedPreviewImageMode();
        previewImageModeOverride = previewImageMode;
        if (!animate || !ViewCompat.isLaidOut(previewItemContainer)) {
            updatePreview(null, null, null, null, null, null, null, previewImageMode, false);
            requestPreviewRemeasure();
            return;
        }
        if (previewImageMode.equals(displayedPreviewImageMode)) {
            cancelPreviewImageAnimations();
            updatePreview(null, null, null, null, null, null, null, previewImageMode, false);
            requestPreviewRemeasure();
            return;
        }

        updatePreview(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                previewImageMode,
                animate,
                false,
                false);
        animatePreviewImageModeChange(displayedPreviewImageMode, previewImageMode);
    }

    private void applyDisplayStyle(String displayStyle, boolean animate) {
        displayStyleOverride = displayStyle;
        boolean useCardStyle = SettingsUtils.STORY_DISPLAY_STYLE_CARD.equals(displayStyle);
        if (useCardStyle == cardStyle) {
            return;
        }

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
                true,
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
            boolean syncHeight,
            boolean updateImages) {
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
            leftAligned = shouldLeftAlign;
            applyCommentButtonAlignment(shouldLeftAlign, animate);
        }

        int targetMetaVisibility = compact ? View.GONE : View.VISIBLE;
        int targetCommentsVisibility = compact ? View.GONE : View.VISIBLE;
        boolean compactVisibilityChanged =
                metaContainer != null && metaContainer.getVisibility() != targetMetaVisibility
                        || comments != null && comments.getVisibility() != targetCommentsVisibility;
        boolean animateCompactVisibilityChange = animate && compactVisibilityChanged;
        boolean animateCompactShow = animateCompactVisibilityChange && !compact;
        boolean animateCompactHide = animateCompactVisibilityChange && compact;

        updateMetaContainer(targetMetaVisibility == View.VISIBLE, animate, animateCompactVisibilityChange);
        updateFavicon(showThumbnails, animate);

        updateStoryIndex(showIndex, animate);
        updateStoryCardBackground(previewImageMode, animate);
        updatePointsText(showPoints, compactPoints, includeTopLevelDomain, animate && !compactVisibilityChanged);
        updateCommentCount(showCommentsCount, compact, animate, animateCompactVisibilityChange);
        updateHotnessIcon(hotness, animate);
        if (syncHeight) {
            if (!animateCompactHide) {
                syncPreviewContainerHeight(
                        previewImageMode,
                        animate,
                        animateCompactShow,
                        animateCompactShow ? this::fadeInCompactTextAfterHeightAnimation : null);
            }
        }
        if (updateImages) {
            updatePreviewImage(previewImageMode, animate);
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

    private void syncPreviewContainerHeight(String previewImageMode) {
        syncPreviewContainerHeight(previewImageMode, false);
    }

    private void syncPreviewContainerHeight(String previewImageMode, boolean animate) {
        syncPreviewContainerHeight(previewImageMode, animate, false);
    }

    private void syncPreviewContainerHeight(String previewImageMode, boolean animate, boolean animateStoryRowHeight) {
        syncPreviewContainerHeight(previewImageMode, animate, animateStoryRowHeight, null);
    }

    private void syncPreviewContainerHeight(
            String previewImageMode,
            boolean animate,
            boolean animateStoryRowHeight,
            Runnable endAction) {
        PreviewHeights heights = calculatePreviewHeights(previewImageMode);
        if (heights.isValid()) {
            applyPreviewHeights(heights, animate, animateStoryRowHeight, endAction);
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
        applyPreviewHeights(heights, animate, false);
    }

    private void applyPreviewHeights(PreviewHeights heights, boolean animate, boolean animateStoryRowHeight) {
        applyPreviewHeights(heights, animate, animateStoryRowHeight, null);
    }

    private void applyPreviewHeights(
            PreviewHeights heights,
            boolean animate,
            boolean animateStoryRowHeight,
            Runnable endAction) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null || !heights.isValid()) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        if (animate && ViewCompat.isLaidOut(previewItemContainer)) {
            animatePreviewHeights(heights, animateStoryRowHeight, endAction);
            return;
        }

        cancelPreviewHeightAnimator();
        clearStoryRowHeight();
        setPreviewHeights(heights);
        if (endAction != null) {
            endAction.run();
        }
    }

    private void setPreviewHeights(PreviewHeights heights) {
        setPreviewHeights(heights, false);
    }

    private void setPreviewHeights(PreviewHeights heights, boolean constrainPreviewItemHeight) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null || !heights.isValid()) {
            return;
        }

        View previewItem = previewItemContainer.getChildAt(0);
        if (constrainPreviewItemHeight) {
            PreviewPreferenceViewUtils.setExactHeight(previewItem, heights.contentHeight);
        } else {
            PreviewPreferenceViewUtils.setWrapContentHeight(previewItem);
        }
        PreviewPreferenceViewUtils.setExactHeight(previewItemContainer, heights.containerHeight);
        previewRoot.setMinimumHeight(heights.rootHeight);
        setMinimumHeight(heights.rootHeight);
        requestPreviewRemeasure();
    }

    private void animatePreviewHeights(PreviewHeights targetHeights) {
        animatePreviewHeights(targetHeights, false);
    }

    private void animatePreviewHeights(PreviewHeights targetHeights, boolean animateStoryRowHeight) {
        animatePreviewHeights(targetHeights, animateStoryRowHeight, null);
    }

    private void animatePreviewHeights(
            PreviewHeights targetHeights,
            boolean animateStoryRowHeight,
            Runnable endAction) {
        if (previewItemContainer == null || previewRoot == null || !targetHeights.isValid()) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        int startContainerHeight = getCurrentHeightForAnimation(previewItemContainer, targetHeights.containerHeight);
        int startContentHeight = targetHeights.contentHeight;
        if (previewItemContainer.getChildCount() > 0) {
            startContentHeight = getCurrentHeightForAnimation(
                    previewItemContainer.getChildAt(0),
                    targetHeights.contentHeight);
        }
        int startRootHeight = Math.max(previewRoot.getHeight(), previewRoot.getMinimumHeight());
        if (startRootHeight <= 0) {
            startRootHeight = targetHeights.rootHeight;
        }
        int startSelfHeight = Math.max(getHeight(), getMinimumHeight());
        if (startSelfHeight <= 0) {
            startSelfHeight = targetHeights.rootHeight;
        }
        View rowForHeightAnimation = animateStoryRowHeight ? storyRow : null;
        int startStoryRowHeight = rowForHeightAnimation != null
                ? getCurrentHeightForAnimation(rowForHeightAnimation, 0)
                : 0;
        int targetStoryRowHeight = startStoryRowHeight + targetHeights.contentHeight - startContentHeight;
        if (rowForHeightAnimation == null || startStoryRowHeight <= 0 || targetStoryRowHeight <= 0) {
            rowForHeightAnimation = null;
        }

        if (startContainerHeight == targetHeights.containerHeight
                && startRootHeight == targetHeights.rootHeight
                && startSelfHeight == targetHeights.rootHeight) {
            clearStoryRowHeight();
            setPreviewHeights(targetHeights);
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        cancelPreviewHeightAnimator();
        int finalStartRootHeight = startRootHeight;
        int finalStartSelfHeight = startSelfHeight;
        View finalRowForHeightAnimation = rowForHeightAnimation;
        int finalStartStoryRowHeight = startStoryRowHeight;
        int finalTargetStoryRowHeight = targetStoryRowHeight;
        previewHeightAnimator = ValueAnimator.ofFloat(0f, 1f);
        previewHeightAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        previewHeightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        int finalStartContentHeight = startContentHeight;
        previewHeightAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (finalRowForHeightAnimation != null) {
                setStoryRowHeight(
                        finalRowForHeightAnimation,
                        lerp(finalStartStoryRowHeight, finalTargetStoryRowHeight, progress));
            }
            PreviewHeights frameHeights = new PreviewHeights(
                    lerp(finalStartContentHeight, targetHeights.contentHeight, progress),
                    lerp(startContainerHeight, targetHeights.containerHeight, progress),
                    lerp(finalStartRootHeight, targetHeights.rootHeight, progress));
            setPreviewHeights(frameHeights, true);
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
                if (endAction != null) {
                    endAction.run();
                } else {
                    clearStoryRowHeight();
                }
            }
        });
        previewHeightAnimator.start();
    }

    private void setStoryRowHeight(View row, int height) {
        if (row == null || height <= 0) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = row.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            row.setLayoutParams(layoutParams);
        }
        row.setMinimumHeight(height);
    }

    private void clearStoryRowHeight() {
        if (storyRow != null) {
            PreviewPreferenceViewUtils.setWrapContentHeight(storyRow);
        }
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
        clearStoryRowHeight();
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
                binding.storyContainer.getRoot(),
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
        final ViewGroup storyContainer;
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
                ViewGroup storyContainer,
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
            this.storyContainer = storyContainer;
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

    private void updateMetaContainer(boolean showMeta, boolean animate, boolean compactVisibilityChange) {
        if (metaContainer == null) {
            return;
        }

        if (showMeta && animate && metaContainer.getVisibility() != View.VISIBLE) {
            int animationToken = ++metaAnimationToken;
            cancelViewAnimation(metaContainer);
            metaContainer.setAlpha(0f);
            if (compactVisibilityChange) {
                setVisibilityWithoutLayoutTransition(metaContainer, View.VISIBLE);
                requestPreviewRemeasure();
                return;
            }
            setVisibilityWithChangingOnly(metaContainer, View.VISIBLE);
            metaContainer.animate()
                    .alpha(1f)
                    .setStartDelay(PREVIEW_ANIMATION_DURATION_MS)
                    .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animationToken != metaAnimationToken || metaContainer == null) {
                                return;
                            }
                            metaContainer.animate().setListener(null);
                            metaContainer.setAlpha(1f);
                        }
                    })
                    .start();
            requestPreviewRemeasure();
            return;
        }

        ++metaAnimationToken;
        if (!showMeta && animate && metaContainer.getVisibility() == View.VISIBLE) {
            cancelViewAnimation(metaContainer);
            metaContainer.setAlpha(1f);
            if (compactVisibilityChange) {
                int animationToken = metaAnimationToken;
                metaContainer.animate()
                        .alpha(0f)
                        .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animationToken != metaAnimationToken || metaContainer == null) {
                                    return;
                                }
                                metaContainer.animate().setListener(null);
                                setVisibilityWithoutLayoutTransition(metaContainer, View.GONE);
                                metaContainer.setAlpha(1f);
                                syncCompactHideHeightIfReady();
                            }
                        })
                        .start();
                return;
            }
            setVisibilityWithChangingOnly(metaContainer, View.GONE);
            return;
        }
        updateVisibilityWithLayoutTransition(metaContainer, showMeta, animate);
    }

    private void updateFavicon(boolean showThumbnails, boolean animate) {
        if (favicon == null) {
            return;
        }

        updateVisibilityWithLayoutTransition(favicon, showThumbnails, animate);
    }

    private void syncCompactHideHeightIfReady() {
        if (metaContainer != null && metaContainer.getVisibility() == View.VISIBLE) {
            return;
        }
        if (comments != null && comments.getVisibility() == View.VISIBLE) {
            return;
        }

        syncPreviewContainerHeight(getCurrentPreviewImageMode(), true, true);
    }

    private void fadeInCompactTextAfterHeightAnimation() {
        int metaToken = metaAnimationToken;
        int commentToken = commentCountAnimationToken;
        fadeInMetaContainerAfterCompactHeight();
        fadeInCommentCountAfterCompactHeight();
        postDelayed(() -> {
            if (metaToken == metaAnimationToken && commentToken == commentCountAnimationToken) {
                clearStoryRowHeight();
            }
        }, PREVIEW_TEXT_FADE_DURATION_MS);
    }

    private void fadeInMetaContainerAfterCompactHeight() {
        View currentMetaContainer = metaContainer;
        if (currentMetaContainer == null
                || currentMetaContainer.getVisibility() != View.VISIBLE
                || currentMetaContainer.getAlpha() >= 1f) {
            return;
        }

        int animationToken = metaAnimationToken;
        currentMetaContainer.animate().setListener(null);
        currentMetaContainer.animate().cancel();
        currentMetaContainer.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animationToken != metaAnimationToken || metaContainer != currentMetaContainer) {
                            return;
                        }
                        currentMetaContainer.animate().setListener(null);
                        currentMetaContainer.setAlpha(1f);
                    }
                })
                .start();
    }

    private void fadeInCommentCountAfterCompactHeight() {
        TextView currentComments = comments;
        if (currentComments == null
                || currentComments.getVisibility() != View.VISIBLE
                || currentComments.getAlpha() >= 1f) {
            return;
        }

        int animationToken = commentCountAnimationToken;
        currentComments.animate().setListener(null);
        currentComments.animate().cancel();
        currentComments.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animationToken != commentCountAnimationToken || comments != currentComments) {
                            return;
                        }
                        currentComments.animate().setListener(null);
                        currentComments.setAlpha(1f);
                    }
                })
                .start();
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

        cancelViewAnimation(view);
        view.setAlpha(1f);
        if (!animate) {
            setVisibilityWithoutLayoutTransition(view, visible ? View.VISIBLE : View.GONE);
            return;
        }

        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        requestPreviewRemeasure();
    }

    private void setVisibilityWithoutLayoutTransition(View view, int visibility) {
        if (!(view.getParent() instanceof ViewGroup)) {
            view.setVisibility(visibility);
            return;
        }

        ViewGroup parent = (ViewGroup) view.getParent();
        LayoutTransition transition = parent.getLayoutTransition();
        parent.setLayoutTransition(null);
        view.setVisibility(visibility);
        parent.setLayoutTransition(transition);
    }

    private void setVisibilityWithChangingOnly(View view, int visibility) {
        if (!(view.getParent() instanceof ViewGroup)) {
            view.setVisibility(visibility);
            return;
        }

        ViewGroup parent = (ViewGroup) view.getParent();
        LayoutTransition transition = parent.getLayoutTransition();
        if (transition == null) {
            view.setVisibility(visibility);
            return;
        }

        boolean appearingEnabled = transition.isTransitionTypeEnabled(LayoutTransition.APPEARING);
        boolean disappearingEnabled = transition.isTransitionTypeEnabled(LayoutTransition.DISAPPEARING);
        if (appearingEnabled) {
            transition.disableTransitionType(LayoutTransition.APPEARING);
        }
        if (disappearingEnabled) {
            transition.disableTransitionType(LayoutTransition.DISAPPEARING);
        }
        view.setVisibility(visibility);
        if (appearingEnabled) {
            transition.enableTransitionType(LayoutTransition.APPEARING);
        }
        if (disappearingEnabled) {
            transition.enableTransitionType(LayoutTransition.DISAPPEARING);
        }
        requestPreviewRemeasure();
    }

    private void updatePreviewImage(String previewImageMode, boolean animate) {
        if (!animate) {
            cancelPreviewImageAnimations();
        }
        boolean showSmallPreview = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(previewImageMode);
        boolean showLargePreview = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode);
        updateVisibilityWithLayoutTransition(smallPreviewImage, showSmallPreview, animate);
        updateVisibilityWithLayoutTransition(largePreviewImage, showLargePreview, animate);
    }

    private void animatePreviewImageModeChange(String fromMode, String toMode) {
        int animationToken = ++previewImageAnimationToken;
        cancelLargePreviewImageHeightAnimator();
        cancelImageViewAnimation(smallPreviewImage);
        cancelImageViewAnimation(largePreviewImage);

        boolean fromSmall = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(fromMode);
        boolean fromLarge = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(fromMode);
        boolean toSmall = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(toMode);
        boolean toLarge = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(toMode);

        if (fromLarge || toLarge) {
            suspendStoryContainerChangingTransition();
        } else {
            resumeStoryContainerChangingTransition();
        }

        if (fromLarge && !toLarge) {
            if (toSmall) {
                preparePreviewImageFadeIn(smallPreviewImage);
            } else {
                hidePreviewImageWithoutDrawing(smallPreviewImage);
            }
            PreviewHeights targetHeights = calculatePreviewHeights(toMode);
            animateLargePreviewImageOut(animationToken, targetHeights);
            if (toSmall) {
                fadeInPreviewImage(
                        smallPreviewImage,
                        animationToken,
                        PREVIEW_IMAGE_FADE_START_DELAY_MS + PREVIEW_IMAGE_FADE_DURATION_MS);
            }
            return;
        }

        if (fromSmall && !toSmall) {
            if (toLarge) {
                hidePreviewImageWithoutDrawing(smallPreviewImage);
            } else {
                fadeOutPreviewImageThenGone(smallPreviewImage, animationToken, () -> {
                    if (isPreviewImageAnimationActive(animationToken)) {
                        syncPreviewContainerHeight(toMode, true);
                    }
                });
                hidePreviewImageWithoutDrawing(largePreviewImage);
                return;
            }
        } else if (!toSmall) {
            hidePreviewImageWithoutDrawing(smallPreviewImage);
        }

        if (toLarge) {
            prepareLargePreviewImageFadeIn();
            resumeStoryContainerChangingTransitionAfterNextLayout();
            syncPreviewContainerHeight(toMode, true);
            fadeInPreviewImage(largePreviewImage, animationToken, 0);
            return;
        }

        resumeStoryContainerChangingTransition();
        hidePreviewImageWithoutDrawing(largePreviewImage);
        if (toSmall) {
            preparePreviewImageFadeIn(smallPreviewImage);
            syncPreviewContainerHeight(toMode, true);
            fadeInPreviewImage(smallPreviewImage, animationToken, 0);
            return;
        }

        syncPreviewContainerHeight(toMode, true);
    }

    private void preparePreviewImageFadeIn(ImageView previewImage) {
        if (previewImage == null) {
            return;
        }

        cancelImageViewAnimation(previewImage);
        previewImage.setAlpha(0f);
        setVisibilityWithChangingOnly(previewImage, View.VISIBLE);
    }

    private void prepareLargePreviewImageFadeIn() {
        if (largePreviewImage == null) {
            return;
        }

        cancelImageViewAnimation(largePreviewImage);
        setLargePreviewImageHeight(getLargePreviewImageTargetHeight());
        largePreviewImage.setAlpha(0f);
        setVisibilityWithChangingOnly(largePreviewImage, View.VISIBLE);
    }

    private void fadeInPreviewImage(ImageView previewImage, int animationToken, long startDelayMs) {
        if (previewImage == null) {
            return;
        }

        previewImage.animate()
                .alpha(1f)
                .setStartDelay(startDelayMs)
                .setDuration(PREVIEW_IMAGE_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isPreviewImageAnimationActive(animationToken)) {
                            return;
                        }
                        previewImage.animate().setListener(null);
                        previewImage.setAlpha(1f);
                    }
                })
                .start();
    }

    private void fadeOutPreviewImageThenGone(
            ImageView previewImage,
            int animationToken,
            Runnable afterHidden) {
        if (previewImage == null) {
            if (afterHidden != null) {
                afterHidden.run();
            }
            return;
        }

        if (previewImage.getVisibility() != View.VISIBLE || previewImage.getAlpha() <= 0f) {
            hidePreviewImageWithoutDrawing(previewImage);
            if (afterHidden != null) {
                afterHidden.run();
            }
            return;
        }

        previewImage.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(PREVIEW_IMAGE_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isPreviewImageAnimationActive(animationToken)) {
                            return;
                        }
                        previewImage.animate().setListener(null);
                        hidePreviewImageWithoutDrawing(previewImage);
                        if (afterHidden != null) {
                            afterHidden.run();
                        }
                    }
                })
                .start();
    }

    private void hidePreviewImageWithoutDrawing(ImageView previewImage) {
        if (previewImage == null) {
            return;
        }

        cancelImageViewAnimation(previewImage);
        previewImage.setAlpha(0f);
        setVisibilityWithChangingOnly(previewImage, View.GONE);
        previewImage.setAlpha(1f);
        if (previewImage == largePreviewImage) {
            setLargePreviewImageHeight(getLargePreviewImageTargetHeight());
        }
    }

    private boolean isPreviewImageAnimationActive(int animationToken) {
        return animationToken == previewImageAnimationToken && previewRoot != null;
    }

    private void cancelPreviewImageAnimations() {
        ++previewImageAnimationToken;
        cancelLargePreviewImageHeightAnimator();
        cancelImageViewAnimation(smallPreviewImage);
        cancelImageViewAnimation(largePreviewImage);
        resumeStoryContainerChangingTransition();
    }

    private void cancelImageViewAnimation(ImageView previewImage) {
        if (previewImage == null) {
            return;
        }

        cancelViewAnimation(previewImage);
    }

    private void cancelMetaAnimation() {
        ++metaAnimationToken;
        cancelViewAnimation(metaContainer);
    }

    private void cancelViewAnimation(View view) {
        if (view == null) {
            return;
        }

        view.animate().setListener(null);
        view.animate().cancel();
        view.animate().setStartDelay(0);
    }

    private void animateLargePreviewImageOut(int animationToken, PreviewHeights targetHeights) {
        if (largePreviewImage == null) {
            return;
        }

        int startHeight = getCurrentHeightForAnimation(
                largePreviewImage,
                getLargePreviewImageTargetHeight());
        largePreviewImage.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(PREVIEW_ANIMATION_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isPreviewImageAnimationActive(animationToken) || largePreviewImage == null) {
                            return;
                        }
                        largePreviewImage.animate().setListener(null);
                        hidePreviewImageWithoutDrawing(largePreviewImage);
                    }
                })
                .start();
        animateLargePreviewImageHeight(startHeight, 0, animationToken, targetHeights, null);
    }

    private void animateLargePreviewImageHeight(
            int startHeight,
            int targetHeight,
            int animationToken,
            PreviewHeights targetHeights,
            Runnable endAction) {
        if (largePreviewImage == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        cancelLargePreviewImageHeightAnimator();
        cancelPreviewHeightAnimator();
        boolean animatePreviewHeights = targetHeights != null && targetHeights.isValid()
                && previewItemContainer != null
                && previewRoot != null
                && previewItemContainer.getChildCount() > 0;
        int startContainerHeight = animatePreviewHeights
                ? getCurrentHeightForAnimation(previewItemContainer, targetHeights.containerHeight)
                : 0;
        int startContentHeight = animatePreviewHeights
                ? getCurrentHeightForAnimation(previewItemContainer.getChildAt(0), targetHeights.contentHeight)
                : 0;
        int startRootHeight = animatePreviewHeights
                ? Math.max(previewRoot.getHeight(), previewRoot.getMinimumHeight())
                : 0;
        int startSelfHeight = animatePreviewHeights
                ? Math.max(getHeight(), getMinimumHeight())
                : 0;
        if (startHeight == targetHeight) {
            setLargePreviewImageHeight(targetHeight);
            if (animatePreviewHeights) {
                setPreviewHeights(targetHeights);
            }
            if (endAction != null) {
                endAction.run();
            }
            resumeStoryContainerChangingTransition();
            return;
        }

        largePreviewImageHeightAnimator = ValueAnimator.ofFloat(0f, 1f);
        largePreviewImageHeightAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        largePreviewImageHeightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        largePreviewImageHeightAnimator.addUpdateListener(animation -> {
            if (!isPreviewImageAnimationActive(animationToken)) {
                animation.cancel();
                return;
            }
            float progress = (float) animation.getAnimatedValue();
            setLargePreviewImageHeight(lerp(startHeight, targetHeight, progress));
            if (animatePreviewHeights) {
                PreviewHeights frameHeights = new PreviewHeights(
                        lerp(startContentHeight, targetHeights.contentHeight, progress),
                        lerp(startContainerHeight, targetHeights.containerHeight, progress),
                        lerp(startRootHeight, targetHeights.rootHeight, progress));
                setPreviewHeights(frameHeights, true);
                setMinimumHeight(lerp(startSelfHeight, targetHeights.rootHeight, progress));
            }
        });
        largePreviewImageHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (largePreviewImageHeightAnimator != animation
                        || !isPreviewImageAnimationActive(animationToken)) {
                    return;
                }
                largePreviewImageHeightAnimator = null;
                setLargePreviewImageHeight(targetHeight);
                if (animatePreviewHeights) {
                    setPreviewHeights(targetHeights);
                }
                if (endAction != null) {
                    endAction.run();
                }
                if (endAction == null) {
                    resumeStoryContainerChangingTransition();
                }
            }
        });
        largePreviewImageHeightAnimator.start();
    }

    private void cancelLargePreviewImageHeightAnimator() {
        if (largePreviewImageHeightAnimator != null) {
            largePreviewImageHeightAnimator.cancel();
            largePreviewImageHeightAnimator = null;
        }
    }

    private void suspendStoryContainerChangingTransition() {
        if (storyContainerChangingTransitionSuspended
                || storyContainer == null
                || storyContainer.getLayoutTransition() == null) {
            return;
        }

        LayoutTransition transition = storyContainer.getLayoutTransition();
        storyContainerChangingTransitionWasEnabled =
                transition.isTransitionTypeEnabled(LayoutTransition.CHANGING);
        if (storyContainerChangingTransitionWasEnabled) {
            transition.disableTransitionType(LayoutTransition.CHANGING);
        }
        storyContainerChangingTransitionSuspended = true;
    }

    private void resumeStoryContainerChangingTransition() {
        if (!storyContainerChangingTransitionSuspended) {
            return;
        }

        if (storyContainer != null && storyContainer.getLayoutTransition() != null
                && storyContainerChangingTransitionWasEnabled) {
            storyContainer.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        }
        storyContainerChangingTransitionSuspended = false;
        storyContainerChangingTransitionWasEnabled = false;
    }

    private void resumeStoryContainerChangingTransitionAfterNextLayout() {
        if (storyContainer == null) {
            resumeStoryContainerChangingTransition();
            return;
        }

        storyContainer.post(this::resumeStoryContainerChangingTransition);
    }

    private int getLargePreviewImageTargetHeight() {
        return largePreviewImage != null
                ? Utils.pxFromDpInt(largePreviewImage.getResources(), LARGE_PREVIEW_IMAGE_HEIGHT_DP)
                : 0;
    }

    private void setLargePreviewImageHeight(int height) {
        if (largePreviewImage == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = largePreviewImage.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            largePreviewImage.setLayoutParams(layoutParams);
        }
        largePreviewImage.setMinimumHeight(height);
        requestPreviewRemeasure();
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

    private void updateCommentCount(
            boolean showCommentsCount,
            boolean compact,
            boolean animate,
            boolean compactVisibilityChange) {
        TextView currentComments = comments;
        if (currentComments == null) {
            return;
        }

        String targetText = showCommentsCount ? PREVIEW_STORY_COMMENTS : "";
        int targetVisibility = compact || !showCommentsCount ? View.GONE : View.VISIBLE;
        int animationToken = ++commentCountAnimationToken;
        cancelViewAnimation(currentComments);

        if (!animate) {
            currentComments.setText(targetText);
            setVisibilityWithoutLayoutTransition(currentComments, targetVisibility);
            currentComments.setAlpha(1f);
            return;
        }

        if (currentComments.getVisibility() != targetVisibility) {
            currentComments.setScaleX(1f);
            currentComments.setScaleY(1f);
            if (targetVisibility == View.VISIBLE) {
                currentComments.setText(targetText);
                currentComments.setAlpha(0f);
                if (compactVisibilityChange) {
                    setVisibilityWithoutLayoutTransition(currentComments, View.VISIBLE);
                    requestPreviewRemeasure();
                    return;
                }
                setVisibilityWithChangingOnly(currentComments, View.VISIBLE);
                currentComments.animate()
                        .alpha(1f)
                        .setStartDelay(PREVIEW_ANIMATION_DURATION_MS)
                        .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animationToken != commentCountAnimationToken || comments != currentComments) {
                                    return;
                                }
                                currentComments.animate().setListener(null);
                                currentComments.setAlpha(1f);
                            }
                        })
                        .start();
            } else {
                currentComments.setAlpha(1f);
                currentComments.animate()
                        .alpha(0f)
                        .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animationToken != commentCountAnimationToken || comments != currentComments) {
                                    return;
                                }
                                currentComments.animate().setListener(null);
                                currentComments.setText(targetText);
                                if (compactVisibilityChange) {
                                    setVisibilityWithoutLayoutTransition(currentComments, View.GONE);
                                } else {
                                    setVisibilityWithChangingOnly(currentComments, View.GONE);
                                }
                                currentComments.setAlpha(1f);
                                if (compactVisibilityChange) {
                                    syncCompactHideHeightIfReady();
                                } else {
                                    syncPreviewContainerHeight(getCurrentPreviewImageMode(), true);
                                }
                            }
                        })
                        .start();
            }
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
                    if (animationToken != commentCountAnimationToken || comments != currentComments) {
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

        int targetIconResId = hotness > 0 ? R.drawable.ic_whatshot : R.drawable.ic_comment;
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
