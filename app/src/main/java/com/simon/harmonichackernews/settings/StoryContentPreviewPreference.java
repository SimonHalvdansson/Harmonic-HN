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
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryMetaPreviewAnimator;
import com.simon.harmonichackernews.utils.Utils;

public class StoryContentPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long PREVIEW_ANIMATION_DURATION_MS = 180;
    private static final long PREVIEW_TEXT_FADE_DURATION_MS = 90;
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
    private ValueAnimator previewHeightAnimator;
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
        View root = itemView.findViewById(R.id.story_content_preview_root);
        previewRoot = root instanceof ViewGroup
                ? (ViewGroup) root
                : itemView instanceof ViewGroup
                ? (ViewGroup) itemView
                : null;
        previewItemContainer = itemView.findViewById(R.id.story_content_preview_item_container);
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
        updatePointsText(SettingsUtils.shouldShowPoints(getContext()), compactPoints, true);
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
        cancelPreviewHeightAnimator();
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

        cancelPreviewHeightAnimator();
        cancelCardTintAnimator();
        previewItemContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int layout = getStoryPreviewLayout(leftAlign, shouldUsePreviewCardShell());
        View itemView = inflater.inflate(layout, previewItemContainer, false);
        previewItemContainer.addView(itemView, createPreviewItemLayoutParams());

        storyLinkLayout = itemView.findViewById(R.id.story_link_layout);
        commentLayout = itemView.findViewById(R.id.story_comment_layout);
        metaContainer = itemView.findViewById(R.id.story_meta_container);
        favicon = itemView.findViewById(R.id.story_meta_favicon);
        commentsIcon = itemView.findViewById(R.id.story_comments_icon);
        smallPreviewImage = itemView.findViewById(R.id.story_preview_image_small);
        largePreviewImage = itemView.findViewById(R.id.story_preview_image_large);
        storyCard = itemView.findViewById(R.id.story_card);
        storyTitle = itemView.findViewById(R.id.story_title);
        storyIndex = itemView.findViewById(R.id.story_index);
        storyMeta = itemView.findViewById(R.id.story_meta);
        comments = itemView.findViewById(R.id.story_comments);
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

    private int getStoryPreviewLayout(boolean leftAlign, boolean useCardStyle) {
        if (useCardStyle) {
            return leftAlign ? R.layout.story_list_item_card_left : R.layout.story_list_item_card;
        }
        return leftAlign ? R.layout.story_list_item_left : R.layout.story_list_item;
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
        previewImageModeOverride = previewImageMode;
        if (animate && previewRoot != null && ViewCompat.isLaidOut(previewRoot)) {
            animatePreviewImageMode(previewImageMode);
            return;
        }
        updatePreview(null, null, null, null, null, null, null, previewImageMode, false);
        requestPreviewRemeasure();
    }

    private void applyDisplayStyle(String displayStyle, boolean animate) {
        displayStyleOverride = displayStyle;
        boolean useCardStyle = SettingsUtils.STORY_DISPLAY_STYLE_CARD.equals(displayStyle);
        if (useCardStyle == cardStyle) {
            return;
        }

        if (animate) {
            beginPreviewTransition();
        }
        cardStyle = useCardStyle;
        inflatePreviewItem(leftAligned);
        updatePreview(false);
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
            if (animate) {
                beginPreviewTransition();
            }
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
        syncPreviewContainerHeight(getCurrentPreviewImageMode());
        applyStoryTypefacesAndTextSizes(storyTitle, storyMeta, storyIndex, comments, clampedTextSize);
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
        if (animate) {
            beginPreviewTransition();
        }

        if (shouldLeftAlign != leftAligned) {
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
        updatePointsText(showPoints, compactPoints, animate && !compactVisibilityChanged);
        updateCommentCount(showCommentsCount, compact, animate && !compactVisibilityChanged);
        updateHotnessIcon(hotness, animate);
        if (syncHeight) {
            syncPreviewContainerHeight(previewImageMode);
        }
    }

    private void animatePreviewImageMode(String previewImageMode) {
        cancelPreviewHeightAnimator();

        boolean largePreviewTransition = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                || (largePreviewImage != null && largePreviewImage.getVisibility() == View.VISIBLE);
        boolean animatePreviewDetails = !largePreviewTransition;
        if (animatePreviewDetails) {
            setPreviewImageLayoutTransitionsEnabled(true);
        }

        PreviewHeights startHeights = getCurrentPreviewHeights();
        updatePreview(null, null, null, null, null, null, null, previewImageMode, animatePreviewDetails, false);
        PreviewHeights targetHeights = calculatePreviewHeights(previewImageMode);
        if (!targetHeights.isValid()) {
            syncPreviewContainerHeight(previewImageMode);
            requestPreviewRemeasure();
            setPreviewImageLayoutTransitionsEnabled(false);
            return;
        }

        if (!startHeights.isValid()) {
            startHeights = targetHeights;
        }
        applyPreviewHeights(startHeights);

        PreviewHeights animationStartHeights = startHeights;
        previewHeightAnimator = ValueAnimator.ofFloat(0f, 1f);
        previewHeightAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        previewHeightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        previewHeightAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            applyPreviewHeights(new PreviewHeights(
                    lerp(animationStartHeights.contentHeight, targetHeights.contentHeight, progress),
                    lerp(animationStartHeights.containerHeight, targetHeights.containerHeight, progress),
                    lerp(animationStartHeights.rootHeight, targetHeights.rootHeight, progress)));
        });
        previewHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (previewHeightAnimator == animation) {
                    previewHeightAnimator = null;
                }
                setPreviewImageLayoutTransitionsEnabled(false);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                setPreviewImageLayoutTransitionsEnabled(false);
            }
        });
        previewHeightAnimator.start();
    }

    private void syncPreviewContainerHeight(String previewImageMode) {
        PreviewHeights heights = calculatePreviewHeights(previewImageMode);
        if (heights.isValid()) {
            applyPreviewHeights(heights);
        }
    }

    private PreviewHeights getCurrentPreviewHeights() {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null) {
            return PreviewHeights.invalid();
        }

        View previewItem = previewItemContainer.getChildAt(0);
        return new PreviewHeights(
                previewItem.getHeight(),
                previewItemContainer.getHeight(),
                previewRoot.getHeight());
    }

    private PreviewHeights calculatePreviewHeights(String previewImageMode) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null) {
            return PreviewHeights.invalid();
        }

        int containerWidth = previewItemContainer.getWidth()
                - previewItemContainer.getPaddingLeft()
                - previewItemContainer.getPaddingRight();
        if (containerWidth <= 0) {
            return PreviewHeights.invalid();
        }

        int targetContentHeight = measureMaxPreviewItemHeight(previewImageMode, containerWidth);
        int targetContainerHeight = targetContentHeight
                + previewItemContainer.getPaddingTop()
                + previewItemContainer.getPaddingBottom();
        int previewHeaderHeight = previewRoot.getChildCount() > 0
                ? getOuterHeight(previewRoot.getChildAt(0))
                : 0;
        int targetRootHeight = targetContainerHeight
                + previewHeaderHeight
                + previewRoot.getPaddingTop()
                + previewRoot.getPaddingBottom();
        return new PreviewHeights(targetContentHeight, targetContainerHeight, targetRootHeight);
    }

    private int measureMaxPreviewItemHeight(String previewImageMode, int containerWidth) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        boolean largePreview = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode);
        int maxHeight = 0;
        maxHeight = Math.max(maxHeight, measureMaxPreviewItemHeight(inflater, false, false, largePreview, containerWidth));
        maxHeight = Math.max(maxHeight, measureMaxPreviewItemHeight(inflater, true, false, largePreview, containerWidth));
        maxHeight = Math.max(maxHeight, measureMaxPreviewItemHeight(inflater, false, true, largePreview, containerWidth));
        maxHeight = Math.max(maxHeight, measureMaxPreviewItemHeight(inflater, true, true, largePreview, containerWidth));
        return maxHeight;
    }

    private int measureMaxPreviewItemHeight(
            LayoutInflater inflater,
            boolean measureLeftAligned,
            boolean measureCardStyle,
            boolean largePreview,
            int containerWidth) {
        View itemView = inflater.inflate(
                getStoryPreviewLayout(measureLeftAligned, measureCardStyle),
                previewItemContainer,
                false);
        bindMaxPreviewItem(itemView, largePreview);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        itemView.measure(widthSpec, heightSpec);
        return getMeasuredOuterHeight(itemView);
    }

    private void bindMaxPreviewItem(View itemView, boolean largePreview) {
        TextView measuredStoryTitle = itemView.findViewById(R.id.story_title);
        TextView measuredStoryIndex = itemView.findViewById(R.id.story_index);
        TextView measuredStoryMeta = itemView.findViewById(R.id.story_meta);
        TextView measuredComments = itemView.findViewById(R.id.story_comments);
        View measuredMetaContainer = itemView.findViewById(R.id.story_meta_container);
        View measuredFavicon = itemView.findViewById(R.id.story_meta_favicon);
        View measuredSmallPreviewImage = itemView.findViewById(R.id.story_preview_image_small);
        View measuredLargePreviewImage = itemView.findViewById(R.id.story_preview_image_large);

        if (measuredStoryTitle != null) {
            measuredStoryTitle.setText(PREVIEW_STORY_TITLE);
        }
        if (measuredStoryIndex != null) {
            measuredStoryIndex.setText("3.");
            measuredStoryIndex.setVisibility(View.VISIBLE);
        }
        if (measuredStoryMeta != null) {
            StoryMetaPreviewAnimator.setPointsVisible(
                    measuredStoryMeta,
                    true,
                    SettingsUtils.shouldUseCompactPoints(getContext()),
                    false);
        }
        if (measuredComments != null) {
            measuredComments.setText(PREVIEW_STORY_COMMENTS);
            measuredComments.setVisibility(View.VISIBLE);
        }
        applyStoryTypefacesAndTextSizes(
                measuredStoryTitle,
                measuredStoryMeta,
                measuredStoryIndex,
                measuredComments,
                SettingsUtils.MAX_STORY_TEXT_SIZE);
        if (measuredMetaContainer != null) {
            measuredMetaContainer.setVisibility(View.VISIBLE);
        }
        if (measuredFavicon != null) {
            measuredFavicon.setVisibility(View.VISIBLE);
        }
        if (measuredSmallPreviewImage != null) {
            measuredSmallPreviewImage.setVisibility(largePreview ? View.GONE : View.VISIBLE);
        }
        if (measuredLargePreviewImage != null) {
            measuredLargePreviewImage.setVisibility(largePreview ? View.VISIBLE : View.GONE);
        }
    }

    private void applyPreviewHeights(PreviewHeights heights) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0 || previewRoot == null || !heights.isValid()) {
            return;
        }

        View previewItem = previewItemContainer.getChildAt(0);
        setWrapContentHeight(previewItem);
        setExactHeight(previewItemContainer, heights.containerHeight);
        previewRoot.setMinimumHeight(heights.rootHeight);
        if (boundItemView != null) {
            boundItemView.setMinimumHeight(heights.rootHeight);
        }
        requestPreviewRemeasure();
    }

    private void setExactHeight(View view, int height) {
        if (view == null || height <= 0) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
        view.setMinimumHeight(height);
    }

    private void setWrapContentHeight(View view) {
        if (view == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(layoutParams);
        }
        view.setMinimumHeight(0);
    }

    private FrameLayout.LayoutParams createPreviewItemLayoutParams() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        return layoutParams;
    }

    private int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    private int getOuterHeight(View view) {
        if (view == null || view.getVisibility() == View.GONE) {
            return 0;
        }

        int height = view.getMeasuredHeight();
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height > 0) {
            height = layoutParams.height;
        }
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
            height += margins.topMargin + margins.bottomMargin;
        }
        return height;
    }

    private int getMeasuredOuterHeight(View view) {
        if (view == null || view.getVisibility() == View.GONE) {
            return 0;
        }

        int height = view.getMeasuredHeight();
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
            height += margins.topMargin + margins.bottomMargin;
        }
        return height;
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
        if (previewItemContainer != null) {
            previewItemContainer.requestLayout();
        }
        if (previewRoot != null) {
            previewRoot.requestLayout();
            ViewGroup settingsList = findAncestorOfType(previewRoot, RecyclerView.class);
            if (settingsList != null) {
                settingsList.requestLayout();
            }
        }
        if (boundItemView != null) {
            boundItemView.requestLayout();
        }
    }

    private void beginPreviewTransition() {
        if (previewRoot == null || !ViewCompat.isLaidOut(previewRoot)) {
            return;
        }

        beginSettingsListTransition();
        TransitionManager.beginDelayedTransition(previewRoot, createPreviewTransition());
    }

    private void beginSettingsListTransition() {
        if (previewRoot == null || !ViewCompat.isLaidOut(previewRoot)) {
            return;
        }

        ViewGroup settingsList = findAncestorOfType(previewRoot, RecyclerView.class);
        if (settingsList != null && ViewCompat.isLaidOut(settingsList)) {
            TransitionManager.beginDelayedTransition(settingsList, createSettingsListTransition());
        }
    }

    private ChangeBounds createSettingsListTransition() {
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        transition.excludeChildren(previewRoot, true);
        return transition;
    }

    private AutoTransition createPreviewTransition() {
        AutoTransition transition = new AutoTransition();
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        return transition;
    }

    private void setPreviewImageLayoutTransitionsEnabled(boolean enabled) {
        setParentLayoutTransitionEnabled(smallPreviewImage, enabled);
        setParentLayoutTransitionEnabled(largePreviewImage, enabled);
    }

    private void setParentLayoutTransitionEnabled(View view, boolean enabled) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) {
            return;
        }

        setLayoutTransitionEnabled((ViewGroup) view.getParent(), enabled);
    }

    private void setLayoutTransitionEnabled(ViewGroup viewGroup, boolean enabled) {
        if (!enabled) {
            viewGroup.setLayoutTransition(null);
            return;
        }

        if (viewGroup.getLayoutTransition() != null) {
            return;
        }

        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        transition.setAnimateParentHierarchy(false);
        transition.enableTransitionType(LayoutTransition.CHANGING);
        viewGroup.setLayoutTransition(transition);
    }

    private <T extends ViewGroup> T findAncestorOfType(View view, Class<T> type) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (type.isInstance(parent)) {
                return type.cast(parent);
            }
            parent = parent.getParent();
        }
        return null;
    }

    private void updatePointsText(boolean showPoints, boolean compactPoints, boolean animate) {
        StoryMetaPreviewAnimator.setPointsVisible(storyMeta, showPoints, compactPoints, animate);
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

    private void cancelPreviewHeightAnimator() {
        if (previewHeightAnimator == null) {
            return;
        }

        previewHeightAnimator.removeAllUpdateListeners();
        previewHeightAnimator.removeAllListeners();
        previewHeightAnimator.cancel();
        previewHeightAnimator = null;
        setPreviewImageLayoutTransitionsEnabled(false);
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
