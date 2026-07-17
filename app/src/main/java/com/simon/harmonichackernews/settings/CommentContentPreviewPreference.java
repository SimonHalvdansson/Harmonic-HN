package com.simon.harmonichackernews.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.CommentsItemBinding;
import com.simon.harmonichackernews.databinding.CommentsItemCardBinding;
import com.simon.harmonichackernews.databinding.PreferenceCommentContentPreviewBinding;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

public class CommentContentPreviewPreference extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long TEXT_SIZE_ANIMATION_DURATION_MS = 120;
    private static final long DISPLAY_STYLE_ANIMATION_DURATION_MS = 180;
    private static final long META_HIGHLIGHT_ANIMATION_DURATION_MS = 180;
    private static final long COLLECT_LINKS_ANIMATION_DURATION_MS = 260;
    private static final int DISPLAY_STYLE_TRANSLATION_DP = 8;
    private static final int MIN_STABLE_PREVIEW_WIDTH_DP = 240;
    private static final String PREVIEW_COMMENT_BODY = "This reminds me of the old systems where the boring path was often the most durable one. The less hidden state there is, the easier it is to reason about. [0]<p>[0] <a href=\"https://example.com/reference\" rel=\"nofollow\">https://example.com/reference</a>";
    private static final String PREVIEW_INLINE_REFERENCE = "[0] <a href=\"https://example.com/reference\" rel=\"nofollow\">https://example.com/reference</a>";

    private ViewGroup previewRoot;
    private ViewGroup previewItemContainer;
    private HtmlTextView commentBody;
    private TextView commentBy;
    private TextView commentByTime;
    private LinearLayout commentMetaContainer;
    private TextView commentHiddenCount;
    private TextView commentHiddenText;
    private View commentIndentIndicator;
    private LinearLayout referenceLinksContainer;
    private MaterialCardView commentCard;
    private boolean cardStyle;
    private boolean cardBorder;
    private String displayStyleOverride;
    private ValueAnimator cardAppearanceAnimator;
    private ValueAnimator displayStyleAnimator;
    private ValueAnimator metaHighlightAnimator;
    private ValueAnimator textSizeAnimator;
    private boolean metaHighlight;
    private float metaHighlightProgress;
    private int collectLinksAnimationToken;
    private float textSizeTargetSp = -1;
    private final View.OnLayoutChangeListener previewContainerLayoutChangeListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft) {
                    syncReservedPreviewHeight();
                }
            };

    public CommentContentPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        View view = LayoutInflater.from(context).inflate(
                R.layout.preference_comment_content_preview,
                this,
                false);
        addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        bindPreviewLayout(view);
    }

    public CommentContentPreviewPreference(Context context) {
        this(context, null);
    }

    private void bindPreviewLayout(View itemView) {
        itemView.setClickable(false);
        itemView.setFocusable(false);
        PreferenceCommentContentPreviewBinding binding =
                PreferenceCommentContentPreviewBinding.bind(itemView);
        previewRoot = binding.commentContentPreviewRoot;
        previewItemContainer = binding.commentContentPreviewItemContainer;
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
            previewItemContainer.addOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        cardStyle = SettingsUtils.shouldUseCardCommentDisplayStyle(getContext());
        cardBorder = SettingsUtils.shouldShowCommentCardBorder(getContext());
        inflatePreviewItem();
        updatePreview();
        syncReservedPreviewHeight();
        if (previewItemContainer != null) {
            previewItemContainer.post(() -> {
                disablePreviewItemScrollbars();
                syncReservedPreviewHeight();
            });
        }
    }

    public void updateDisplayStyle(String displayStyle) {
        applyDisplayStyle(displayStyle);
    }

    public void updateTextSize(String textSize) {
        applyTextSize(parseTextSize(textSize), true);
    }

    public void updateBorder(boolean showBorder) {
        applyBorder(showBorder, true);
    }

    public void updateMetaHighlight(boolean highlight) {
        applyMetaHighlight(highlight, true);
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsUtils.PREF_COMMENT_DISPLAY_STYLE.equals(key)) {
            String displayStyle = sharedPreferences.getString(
                    key,
                    SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD);
            if (displayStyle != null && displayStyle.equals(displayStyleOverride)) {
                return;
            }
            applyDisplayStyle(displayStyle);
            return;
        }

        if (SettingsUtils.PREF_COMMENT_TEXT_SIZE.equals(key)) {
            applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), true);
            return;
        }

        if (SettingsUtils.PREF_COMMENT_CARD_BORDER.equals(key)) {
            applyBorder(SettingsUtils.shouldShowCommentCardBorder(getContext()), true);
            return;
        }

        if (SettingsUtils.PREF_HIGHLIGHT_COMMENT_META.equals(key)) {
            applyMetaHighlight(SettingsUtils.shouldHighlightCommentMeta(getContext()), true);
            return;
        }

        if (SettingsUtils.PREF_COLLECT_LINKS_IN_COMMENTS.equals(key)) {
            updateCollectedLinksPreview(true);
            return;
        }

        if (SettingsUtils.PREF_FONT.equals(key)) {
            FontUtils.init(getContext());
            applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false);
            syncReservedPreviewHeight();
            return;
        }

        if (SettingsUtils.PREF_COMMENT_DEPTH_INDICATORS.equals(key)
                || SettingsUtils.PREF_MONOCHROME_COMMENT_DEPTH.equals(key)) {
            updateDepthIndicator();
        }
    }

    private void clearPreviewViews() {
        cancelCollectedLinksTransition();
        cancelDisplayStyleAnimator();
        cancelCardAppearanceAnimator();
        cancelMetaHighlightAnimator();
        cancelTextSizeAnimator();
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        previewRoot = null;
        previewItemContainer = null;
        commentBody = null;
        commentBy = null;
        commentByTime = null;
        commentMetaContainer = null;
        commentHiddenCount = null;
        commentHiddenText = null;
        commentIndentIndicator = null;
        referenceLinksContainer = null;
        commentCard = null;
        displayStyleOverride = null;
        metaHighlight = false;
        metaHighlightProgress = 0f;
        textSizeTargetSp = -1;
    }

    private void applyDisplayStyle(String displayStyle) {
        cancelCollectedLinksTransition();
        displayStyleOverride = displayStyle;
        boolean useCardStyle = SettingsUtils.COMMENT_DISPLAY_STYLE_CARD.equals(displayStyle);
        if (useCardStyle == cardStyle) {
            return;
        }

        if (shouldAnimateDisplayStyleChange()) {
            animateDisplayStyleChange(useCardStyle);
            return;
        }

        cancelDisplayStyleAnimator();
        cardStyle = useCardStyle;
        inflatePreviewItem();
        updatePreview();
        syncReservedPreviewHeight();
        requestPreviewRemeasure();
    }

    private boolean shouldAnimateDisplayStyleChange() {
        if (previewItemContainer == null || previewRoot == null || previewItemContainer.getChildCount() == 0) {
            return false;
        }
        int containerWidth = getStablePreviewContentWidth();
        return ViewCompat.isLaidOut(previewItemContainer) && isStablePreviewWidth(containerWidth);
    }

    private void animateDisplayStyleChange(boolean useCardStyle) {
        if (previewItemContainer == null || previewRoot == null || previewItemContainer.getChildCount() == 0) {
            cardStyle = useCardStyle;
            inflatePreviewItem();
            updatePreview();
            syncReservedPreviewHeight();
            requestPreviewRemeasure();
            return;
        }

        cancelDisplayStyleAnimator();
        cancelCardAppearanceAnimator();
        if (previewItemContainer.getChildCount() > 1) {
            previewItemContainer.removeAllViews();
            inflatePreviewItem();
            updatePreview();
        }

        int containerWidth = getStablePreviewContentWidth();
        if (!isStablePreviewWidth(containerWidth)) {
            cardStyle = useCardStyle;
            inflatePreviewItem();
            updatePreview();
            syncReservedPreviewHeight();
            requestPreviewRemeasure();
            return;
        }

        View oldItemView = previewItemContainer.getChildAt(0);
        int startContainerHeight = getCurrentHeightForAnimation(
                previewItemContainer,
                measureCurrentPreviewItemHeight(containerWidth)
                        + previewItemContainer.getPaddingTop()
                        + previewItemContainer.getPaddingBottom());
        int startRootHeight = Math.max(previewRoot.getHeight(), previewRoot.getMinimumHeight());
        if (startRootHeight <= 0) {
            startRootHeight = startContainerHeight + previewRoot.getPaddingTop() + previewRoot.getPaddingBottom();
        }
        int startSelfHeight = Math.max(getHeight(), getMinimumHeight());
        if (startSelfHeight <= 0) {
            startSelfHeight = startRootHeight;
        }

        cardStyle = useCardStyle;
        PreviewCommentItemBinding binding = inflatePreviewCommentItemBinding();
        View newItemView = binding.root;
        previewItemContainer.addView(newItemView, createPreviewItemLayoutParams());
        bindPreviewItem(binding);
        updatePreview(false);

        int targetContentHeight = measureCurrentPreviewItemHeight(containerWidth);
        int targetContainerHeight = targetContentHeight
                + previewItemContainer.getPaddingTop()
                + previewItemContainer.getPaddingBottom();
        int targetRootHeight = targetContainerHeight + previewRoot.getPaddingTop() + previewRoot.getPaddingBottom();

        int translation = Utils.pxFromDpInt(getResources(), DISPLAY_STYLE_TRANSLATION_DP);
        newItemView.setAlpha(0f);
        newItemView.setTranslationY(translation);
        oldItemView.setAlpha(1f);
        oldItemView.setTranslationY(0f);
        setPreviewContainerHeight(startContainerHeight, startRootHeight, startSelfHeight);

        displayStyleAnimator = ValueAnimator.ofFloat(0f, 1f);
        displayStyleAnimator.setDuration(DISPLAY_STYLE_ANIMATION_DURATION_MS);
        displayStyleAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        int finalStartRootHeight = startRootHeight;
        int finalStartSelfHeight = startSelfHeight;
        displayStyleAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            oldItemView.setAlpha(1f - progress);
            oldItemView.setTranslationY(-translation * progress);
            newItemView.setAlpha(progress);
            newItemView.setTranslationY(translation * (1f - progress));
            setPreviewContainerHeight(
                    lerp(startContainerHeight, targetContainerHeight, progress),
                    lerp(finalStartRootHeight, targetRootHeight, progress),
                    lerp(finalStartSelfHeight, targetRootHeight, progress));
        });
        displayStyleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (displayStyleAnimator != animation) {
                    return;
                }
                displayStyleAnimator = null;
                newItemView.setAlpha(1f);
                newItemView.setTranslationY(0f);
                previewItemContainer.removeView(oldItemView);
                syncReservedPreviewHeight();
                requestPreviewRemeasure();
            }
        });
        displayStyleAnimator.start();
    }

    @SuppressLint("SetTextI18n")
    private void inflatePreviewItem() {
        if (previewItemContainer == null) {
            return;
        }

        cancelCollectedLinksTransition();
        cancelTextSizeAnimator();
        cancelCardAppearanceAnimator();
        previewItemContainer.removeAllViews();
        PreviewCommentItemBinding binding = inflatePreviewCommentItemBinding();
        previewItemContainer.addView(binding.root, createPreviewItemLayoutParams());
        bindPreviewItem(binding);
        disablePreviewItemScrollbars();
    }

    private void bindPreviewItem(PreviewCommentItemBinding binding) {
        View itemView = binding.root;
        commentBody = binding.commentBody;
        commentBy = binding.commentBy;
        commentByTime = binding.commentByTime;
        commentMetaContainer = binding.commentMetaContainer;
        commentHiddenCount = binding.commentHiddenCount;
        commentHiddenText = binding.commentHiddenText;
        commentIndentIndicator = binding.commentIndentIndicator;
        referenceLinksContainer = binding.referenceLinksContainer;
        commentCard = binding.commentCard;

        itemView.setClickable(false);
        itemView.setFocusable(false);
        disableScrollbarsInTree(itemView);
        if (commentBody != null) {
            commentBody.setClickable(true);
            commentBody.setFocusable(false);
            commentBody.setOnClickListener(v -> {
            });
        }
        if (commentBy != null) {
            commentBy.setText("pg");
            commentBy.setContentDescription("Comment by pg");
        }
        if (commentByTime != null) {
            commentByTime.setText("1h");
            commentByTime.setContentDescription("Posted 1 hour ago");
        }
        if (commentHiddenCount != null) {
            commentHiddenCount.setVisibility(View.GONE);
        }
        if (commentHiddenText != null) {
            commentHiddenText.setVisibility(View.GONE);
        }
        bindPreviewCommentContent(commentBody, referenceLinksContainer);
        applyMetaHighlight(SettingsUtils.shouldHighlightCommentMeta(getContext()), false);
        configureCommentCardAppearance(false);
    }

    private void applyMetaHighlight(boolean highlight, boolean animate) {
        if (commentMetaContainer == null) {
            return;
        }

        float targetProgress = highlight ? 1f : 0f;
        if (!animate || !ViewCompat.isLaidOut(commentMetaContainer)) {
            cancelMetaHighlightAnimator();
            metaHighlight = highlight;
            applyMetaHighlightProgress(targetProgress);
            return;
        }
        if (metaHighlight == highlight
                && (metaHighlightAnimator != null
                || Math.abs(metaHighlightProgress - targetProgress) < 0.001f)) {
            return;
        }

        float startProgress = metaHighlightProgress;
        cancelMetaHighlightAnimator();
        metaHighlight = highlight;
        metaHighlightAnimator = ValueAnimator.ofFloat(startProgress, targetProgress);
        metaHighlightAnimator.setDuration(Math.max(
                1L,
                Math.round(META_HIGHLIGHT_ANIMATION_DURATION_MS
                        * Math.abs(targetProgress - startProgress))));
        metaHighlightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        metaHighlightAnimator.addUpdateListener(animation ->
                applyMetaHighlightProgress((float) animation.getAnimatedValue()));
        metaHighlightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (metaHighlightAnimator != animation) {
                    return;
                }
                metaHighlightAnimator = null;
                applyMetaHighlightProgress(targetProgress);
            }
        });
        metaHighlightAnimator.start();
    }

    private void applyMetaHighlightProgress(float progress) {
        if (commentMetaContainer == null) {
            return;
        }

        metaHighlightProgress = Math.max(0f, Math.min(1f, progress));
        int horizontalPadding = Math.round(
                Utils.pxFromDpInt(getResources(), 7) * metaHighlightProgress);
        int verticalPadding = Math.round(
                Utils.pxFromDpInt(getResources(), 2) * metaHighlightProgress);
        commentMetaContainer.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding);

        int disabledTextColor = MaterialColors.getColor(
                commentMetaContainer,
                R.attr.storyColorDisabled,
                Color.BLACK);
        int emphasizedTextColor = MaterialColors.getColor(
                commentMetaContainer,
                R.attr.storyColorNormal,
                Color.BLACK);
        int textColor = ColorUtils.blendARGB(
                disabledTextColor,
                emphasizedTextColor,
                metaHighlightProgress);
        commentBy.setTextColor(textColor);
        commentByTime.setTextColor(textColor);

        if (metaHighlightProgress <= 0.001f) {
            commentMetaContainer.setBackground(null);
        } else {
            int fillColor = MaterialColors.getColor(
                    commentMetaContainer,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                    Color.TRANSPARENT);
            int strokeColor = MaterialColors.getColor(
                    commentMetaContainer,
                    R.attr.commentDividerColor,
                    Color.TRANSPARENT);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(Utils.pxFromDpInt(getResources(), 12));
            background.setColor(ColorUtils.setAlphaComponent(
                    fillColor,
                    Math.round(Color.alpha(fillColor) * metaHighlightProgress)));
            background.setStroke(
                    Utils.pxFromDpInt(getResources(), 1),
                    ColorUtils.setAlphaComponent(
                            strokeColor,
                            Math.round(Color.alpha(strokeColor) * metaHighlightProgress)));
            commentMetaContainer.setBackground(background);
        }

        syncReservedPreviewHeight();
        requestPreviewRemeasure();
    }

    private void applyBorder(boolean showBorder, boolean animate) {
        if (cardBorder == showBorder) {
            return;
        }

        cardBorder = showBorder;
        configureCommentCardAppearance(animate);
    }

    private void configureCommentCardAppearance(boolean animate) {
        MaterialCardView targetCard = commentCard;
        if (targetCard == null) {
            return;
        }

        int targetStrokeWidth = cardBorder ? Utils.pxFromDpInt(targetCard.getResources(), 1) : 0;
        int targetStrokeColor = cardBorder
                ? MaterialColors.getColor(targetCard, R.attr.commentDividerColor, Color.TRANSPARENT)
                : Color.TRANSPARENT;
        float targetElevation = cardBorder ? Utils.pxFromDpInt(targetCard.getResources(), 1) : 0f;

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
        cardAppearanceAnimator.setDuration(DISPLAY_STYLE_ANIMATION_DURATION_MS);
        cardAppearanceAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        ArgbEvaluator colorEvaluator = new ArgbEvaluator();
        cardAppearanceAnimator.addUpdateListener(animation -> {
            if (commentCard != targetCard) {
                animation.cancel();
                return;
            }

            float progress = (float) animation.getAnimatedValue();
            targetCard.setStrokeWidth(lerp(currentStrokeWidth, targetStrokeWidth, progress));
            targetCard.setStrokeColor((int) colorEvaluator.evaluate(
                    progress,
                    currentStrokeColor,
                    targetStrokeColor));
            targetCard.setCardElevation(
                    currentElevation + (targetElevation - currentElevation) * progress);
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

    private void updatePreview() {
        updatePreview(true);
    }

    private void updatePreview(boolean syncHeight) {
        bindPreviewCommentContent(commentBody, referenceLinksContainer);
        disablePreviewItemScrollbars();
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false, syncHeight);
        updateDepthIndicator();
    }

    private void updateCollectedLinksPreview(boolean animate) {
        if (commentBody == null) {
            return;
        }

        if (animate
                && previewRoot != null
                && previewItemContainer != null
                && ViewCompat.isLaidOut(previewItemContainer)) {
            animateCollectedLinksPreview();
            return;
        }

        bindPreviewCommentContent(commentBody, referenceLinksContainer);
        disablePreviewItemScrollbars();
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false);
        syncReservedPreviewHeight();
        requestPreviewRemeasure();
    }

    private void animateCollectedLinksPreview() {
        cancelCollectedLinksTransition();
        int animationToken = ++collectLinksAnimationToken;

        TransitionSet transition = new TransitionSet();
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.setDuration(COLLECT_LINKS_ANIMATION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        transition.addTransition(new ChangeBounds());
        transition.addTransition(new Fade(Fade.IN | Fade.OUT));
        transition.addListener(new Transition.TransitionListener() {
            private boolean finished;

            private void finish() {
                if (finished || animationToken != collectLinksAnimationToken) {
                    return;
                }
                finished = true;
                transition.removeListener(this);
                disablePreviewItemScrollbars();
                syncReservedPreviewHeight();
                requestPreviewRemeasure();
            }

            @Override
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                finish();
            }

            @Override
            public void onTransitionCancel(Transition transition) {
                finish();
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }
        });

        TransitionManager.beginDelayedTransition(previewRoot, transition);
        clearReservedPreviewHeight();
        bindPreviewCommentContent(commentBody, referenceLinksContainer);
        disablePreviewItemScrollbars();
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false, false);
        requestPreviewRemeasure();
    }

    private void cancelCollectedLinksTransition() {
        collectLinksAnimationToken++;
        if (previewRoot != null) {
            TransitionManager.endTransitions(previewRoot);
        }
    }

    private void bindPreviewCommentContent(HtmlTextView body, LinearLayout linksContainer) {
        boolean collectLinks = SettingsUtils.shouldCollectLinksInComments(getContext());
        CollectedReferenceLinks.Result referenceLinks = CollectedReferenceLinks.parse(PREVIEW_COMMENT_BODY);
        boolean hasCollectedLinks = referenceLinks != null && referenceLinks.hasLinks();

        if (body != null) {
            body.setHtml(hasCollectedLinks ? referenceLinks.getBodyHtml() : PREVIEW_COMMENT_BODY);
            disablePreviewLinks(body);
        }

        if (collectLinks) {
            bindPreviewReferenceLinks(linksContainer, referenceLinks);
        } else {
            bindPreviewInlineReference(linksContainer, hasCollectedLinks);
        }
    }

    private void disablePreviewLinks(HtmlTextView body) {
        body.setLinksClickable(false);
        body.setMovementMethod(null);
        body.setVerticalScrollBarEnabled(false);
        body.setHorizontalScrollBarEnabled(false);
        body.setClickable(true);
        body.setFocusable(false);
        body.setOnClickListener(v -> {
        });
    }

    private boolean bindPreviewReferenceLinks(LinearLayout container, CollectedReferenceLinks.Result referenceLinks) {
        if (container == null) {
            return false;
        }

        container.setVerticalScrollBarEnabled(false);
        container.setHorizontalScrollBarEnabled(false);
        if (referenceLinks == null || !referenceLinks.hasLinks()) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
            return false;
        }

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);
        for (CollectedReferenceLinks.ReferenceLink link : referenceLinks.getLinks()) {
            container.addView(createPreviewReferenceLinkRow(container, link));
        }
        return true;
    }

    private void bindPreviewInlineReference(LinearLayout container, boolean hasReference) {
        if (container == null) {
            return;
        }

        container.removeAllViews();
        if (!hasReference) {
            container.setVisibility(View.GONE);
            return;
        }

        HtmlTextView inlineReference = new HtmlTextView(container.getContext());
        inlineReference.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inlineReference.setHtml(PREVIEW_INLINE_REFERENCE);
        inlineReference.setTextColor(MaterialColors.getColor(
                container,
                R.attr.storyColorNormal,
                Color.BLACK));
        inlineReference.setTypeface(FontUtils.activeRegular);
        inlineReference.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                FontUtils.getCommentTextSize(SettingsUtils.getPreferredCommentTextSize(getContext())));
        disablePreviewLinks(inlineReference);
        container.addView(inlineReference);
        container.setVisibility(View.VISIBLE);
    }

    private View createPreviewReferenceLinkRow(LinearLayout container, CollectedReferenceLinks.ReferenceLink link) {
        Context context = container.getContext();
        return ReferenceLinkRowUtils.createReferenceLinkRow(
                container,
                link,
                SettingsUtils.getPreferredFont(context),
                getReferenceLinkLabelTextSize(SettingsUtils.getPreferredCommentTextSize(context)),
                "Reference link preview: " + ReferenceLinkRowUtils.getReferenceLinkLabel(link),
                null,
                null);
    }

    private void applyTextSize(float textSize, boolean animate) {
        applyTextSize(textSize, animate, true);
    }

    private void applyTextSize(float textSize, boolean animate, boolean syncHeight) {
        float clampedTextSize = SettingsUtils.clampCommentTextSize(textSize);
        ensureSelectedFontLoaded();
        applyCommentMetaTypefaces(commentBy, commentByTime, commentHiddenText, commentHiddenCount);
        applyReferenceLinkTextSize(clampedTextSize);
        if (commentBody == null) {
            textSizeTargetSp = FontUtils.getCommentTextSize(clampedTextSize);
            return;
        }

        float adjustedTextSize = FontUtils.getCommentTextSize(clampedTextSize);
        commentBody.setTypeface(FontUtils.activeRegular);

        if (animate && Math.abs(textSizeTargetSp - adjustedTextSize) < 0.01f) {
            return;
        }
        textSizeTargetSp = adjustedTextSize;

        if (textSizeAnimator != null) {
            cancelTextSizeAnimator();
        }

        HtmlTextView targetBody = commentBody;
        if (!animate || !ViewCompat.isLaidOut(targetBody)) {
            targetBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize);
            if (syncHeight) {
                syncReservedPreviewHeight();
                requestPreviewRemeasure();
            }
            return;
        }

        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        float currentTextSizeSp = targetBody.getTextSize() / scaledDensity;
        if (Math.abs(currentTextSizeSp - adjustedTextSize) < 0.01f) {
            targetBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize);
            if (syncHeight) {
                syncReservedPreviewHeight();
                requestPreviewRemeasure();
            }
            return;
        }

        syncReservedPreviewHeight();
        textSizeAnimator = ValueAnimator.ofFloat(currentTextSizeSp, adjustedTextSize);
        textSizeAnimator.setDuration(TEXT_SIZE_ANIMATION_DURATION_MS);
        textSizeAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        textSizeAnimator.addUpdateListener(animation -> {
            if (commentBody != targetBody) {
                animation.cancel();
                return;
            }
            float animatedTextSize = (float) animation.getAnimatedValue();
            targetBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, animatedTextSize);
            if (syncHeight) {
                syncReservedPreviewHeight();
            }
        });
        textSizeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (textSizeAnimator == animation) {
                    textSizeAnimator = null;
                }
            }
        });
        textSizeAnimator.start();
    }

    private void cancelDisplayStyleAnimator() {
        if (displayStyleAnimator == null) {
            return;
        }

        displayStyleAnimator.removeAllUpdateListeners();
        displayStyleAnimator.removeAllListeners();
        displayStyleAnimator.cancel();
        displayStyleAnimator = null;
    }

    private void cancelTextSizeAnimator() {
        if (textSizeAnimator == null) {
            return;
        }

        textSizeAnimator.removeAllUpdateListeners();
        textSizeAnimator.removeAllListeners();
        textSizeAnimator.cancel();
        textSizeAnimator = null;
    }

    private void cancelMetaHighlightAnimator() {
        if (metaHighlightAnimator == null) {
            return;
        }

        metaHighlightAnimator.removeAllUpdateListeners();
        metaHighlightAnimator.removeAllListeners();
        metaHighlightAnimator.cancel();
        metaHighlightAnimator = null;
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

    private void applyReferenceLinkTextSize(float commentTextSize) {
        if (referenceLinksContainer == null) {
            return;
        }

        String font = SettingsUtils.getPreferredFont(referenceLinksContainer.getContext());
        float labelTextSize = getReferenceLinkLabelTextSize(commentTextSize);
        for (int i = 0; i < referenceLinksContainer.getChildCount(); i++) {
            View child = referenceLinksContainer.getChildAt(i);
            if (child instanceof HtmlTextView) {
                HtmlTextView inlineReference = (HtmlTextView) child;
                inlineReference.setTypeface(FontUtils.activeRegular);
                inlineReference.setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        FontUtils.getCommentTextSize(commentTextSize));
                continue;
            }
            if (!(child instanceof ViewGroup)) {
                continue;
            }

            applyReferenceLinkRowTextSize((ViewGroup) child, font, labelTextSize);
        }
    }

    private void applyReferenceLinkRowTextSize(ViewGroup row, String font, float labelTextSize) {
        TextView label = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView) {
                label = (TextView) child;
            }
        }

        if (label == null) {
            return;
        }

        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }

            TextView textView = (TextView) child;
            if (textView == label) {
                FontUtils.setTypefaceForFont(textView, font, false, labelTextSize);
            } else {
                FontUtils.setTypefaceForFont(textView, font, true, 13);
            }
        }
    }

    private float getReferenceLinkLabelTextSize(float commentTextSize) {
        return Math.max(12f, SettingsUtils.clampCommentTextSize(commentTextSize) - 2f);
    }

    private void applyCommentMetaTypefaces(
            TextView by,
            TextView byTime,
            TextView hiddenText,
            TextView hiddenCount) {
        if (by != null) {
            by.setTypeface(FontUtils.activeBold);
        }
        if (byTime != null) {
            byTime.setTypeface(FontUtils.activeRegular);
        }
        if (hiddenText != null) {
            hiddenText.setTypeface(FontUtils.activeRegular);
        }
        if (hiddenCount != null) {
            hiddenCount.setTypeface(FontUtils.activeRegular);
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

    private void updateDepthIndicator() {
        if (commentIndentIndicator == null || getContext() == null) {
            return;
        }

        String mode = SettingsUtils.getPreferredCommentDepthIndicatorMode(getContext());
        if (!CommentDepthIndicatorUtils.shouldShowIndicators(mode)) {
            commentIndentIndicator.setVisibility(View.INVISIBLE);
            return;
        }

        commentIndentIndicator.setVisibility(View.VISIBLE);
        int colorResource = CommentDepthIndicatorUtils.getColorResource(
                getContext(),
                mode,
                ThemeUtils.getPreferredTheme(getContext()),
                0);
        commentIndentIndicator.setBackgroundColor(ContextCompat.getColor(getContext(), colorResource));
    }

    private float parseTextSize(String textSize) {
        try {
            return SettingsUtils.clampCommentTextSize(Float.parseFloat(textSize));
        } catch (NumberFormatException e) {
            return SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE;
        }
    }

    private FrameLayout.LayoutParams createPreviewItemLayoutParams() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        return layoutParams;
    }

    private void syncReservedPreviewHeight() {
        if (previewItemContainer == null) {
            return;
        }

        int containerWidth = previewItemContainer.getWidth()
                - previewItemContainer.getPaddingLeft()
                - previewItemContainer.getPaddingRight();
        if (!isStablePreviewWidth(containerWidth)) {
            clearReservedPreviewHeight();
            return;
        }

        int reservedContentHeight = measureCurrentPreviewItemHeight(containerWidth);
        if (reservedContentHeight <= 0) {
            return;
        }

        int reservedHeight = reservedContentHeight
                + previewItemContainer.getPaddingTop()
                + previewItemContainer.getPaddingBottom();
        int rootHeight = previewRoot == null
                ? reservedHeight
                : reservedHeight + previewRoot.getPaddingTop() + previewRoot.getPaddingBottom();
        setPreviewContainerHeight(reservedHeight, rootHeight, rootHeight);
        requestPreviewRemeasure();
    }

    private int getStablePreviewContentWidth() {
        if (previewItemContainer == null) {
            return 0;
        }
        return previewItemContainer.getWidth()
                - previewItemContainer.getPaddingLeft()
                - previewItemContainer.getPaddingRight();
    }

    private boolean isStablePreviewWidth(int containerWidth) {
        return containerWidth >= Utils.pxFromDpInt(previewItemContainer.getResources(), MIN_STABLE_PREVIEW_WIDTH_DP);
    }

    private int measureCurrentPreviewItemHeight(int containerWidth) {
        if (previewItemContainer == null || previewItemContainer.getChildCount() == 0) {
            return 0;
        }
        PreviewCommentItemBinding binding = inflatePreviewCommentItemBinding();
        View itemView = binding.root;
        bindCurrentPreviewItemForMeasurement(binding);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        itemView.measure(widthSpec, heightSpec);
        return PreviewPreferenceViewUtils.getMeasuredOuterHeight(itemView);
    }

    private void bindCurrentPreviewItemForMeasurement(PreviewCommentItemBinding binding) {
        bindPreviewCommentContent(binding.commentBody, binding.referenceLinksContainer);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(commentBody, binding.commentBody);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(commentBy, binding.commentBy);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(commentByTime, binding.commentByTime);
        PreviewPreferenceViewUtils.copyViewPaddingForMeasurement(commentMetaContainer, binding.commentMetaContainer);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(commentHiddenCount, binding.commentHiddenCount);
        PreviewPreferenceViewUtils.copyTextViewForMeasurement(commentHiddenText, binding.commentHiddenText);
        PreviewPreferenceViewUtils.copyViewVisibilityForMeasurement(commentIndentIndicator, binding.commentIndentIndicator);
    }

    private void clearReservedPreviewHeight() {
        if (previewItemContainer == null) {
            return;
        }

        if (previewItemContainer.getChildCount() > 0) {
            PreviewPreferenceViewUtils.setWrapContentHeight(previewItemContainer.getChildAt(0));
        }
        PreviewPreferenceViewUtils.setWrapContentHeight(previewItemContainer);
        if (previewRoot != null) {
            previewRoot.setMinimumHeight(0);
        }
        setMinimumHeight(0);
    }

    private void setPreviewContainerHeight(int containerHeight, int rootHeight, int selfHeight) {
        PreviewPreferenceViewUtils.setExactHeight(previewItemContainer, containerHeight);
        if (previewRoot != null) {
            previewRoot.setMinimumHeight(rootHeight);
        }
        setMinimumHeight(selfHeight);
        requestPreviewRemeasure();
    }

    private int getCurrentHeightForAnimation(View view, int fallbackHeight) {
        if (view == null) {
            return fallbackHeight;
        }
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

    private PreviewCommentItemBinding inflatePreviewCommentItemBinding() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (cardStyle) {
            CommentsItemCardBinding binding =
                    CommentsItemCardBinding.inflate(inflater, previewItemContainer, false);
            return new PreviewCommentItemBinding(
                    binding.getRoot(),
                    binding.commentCard,
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
                    binding.commentMetaContainer,
                    binding.commentHiddenCount,
                    binding.commentHiddenShort,
                    binding.commentIndentIndicator,
                    binding.commentReferenceLinksContainer);
        }

        CommentsItemBinding binding = CommentsItemBinding.inflate(inflater, previewItemContainer, false);
        return new PreviewCommentItemBinding(
                binding.getRoot(),
                null,
                binding.commentBody,
                binding.commentBy,
                binding.commentByTime,
                binding.commentMetaContainer,
                binding.commentHiddenCount,
                binding.commentHiddenShort,
                binding.commentIndentIndicator,
                binding.commentReferenceLinksContainer);
    }

    private static class PreviewCommentItemBinding {
        final View root;
        final MaterialCardView commentCard;
        final HtmlTextView commentBody;
        final TextView commentBy;
        final TextView commentByTime;
        final LinearLayout commentMetaContainer;
        final TextView commentHiddenCount;
        final TextView commentHiddenText;
        final View commentIndentIndicator;
        final LinearLayout referenceLinksContainer;

        PreviewCommentItemBinding(
                View root,
                MaterialCardView commentCard,
                HtmlTextView commentBody,
                TextView commentBy,
                TextView commentByTime,
                LinearLayout commentMetaContainer,
                TextView commentHiddenCount,
                TextView commentHiddenText,
                View commentIndentIndicator,
                LinearLayout referenceLinksContainer) {
            this.root = root;
            this.commentCard = commentCard;
            this.commentBody = commentBody;
            this.commentBy = commentBy;
            this.commentByTime = commentByTime;
            this.commentMetaContainer = commentMetaContainer;
            this.commentHiddenCount = commentHiddenCount;
            this.commentHiddenText = commentHiddenText;
            this.commentIndentIndicator = commentIndentIndicator;
            this.referenceLinksContainer = referenceLinksContainer;
        }
    }

    private void requestPreviewRemeasure() {
        PreviewPreferenceViewUtils.requestPreviewRemeasure(previewItemContainer, previewRoot, this);
    }

    private void disablePreviewItemScrollbars() {
        if (previewItemContainer == null) {
            return;
        }

        disableScrollbarsInTree(previewItemContainer);
    }

    private void disableScrollbarsInTree(View view) {
        if (view == null) {
            return;
        }

        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableScrollbarsInTree(viewGroup.getChildAt(i));
            }
        }
    }

}
