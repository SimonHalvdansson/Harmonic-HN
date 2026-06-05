package com.simon.harmonichackernews.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.CommentsItemBinding;
import com.simon.harmonichackernews.databinding.CommentsItemCardBinding;
import com.simon.harmonichackernews.databinding.PreferenceCommentContentPreviewBinding;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

public class CommentContentPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long TEXT_SIZE_ANIMATION_DURATION_MS = 120;
    private static final int MIN_STABLE_PREVIEW_WIDTH_DP = 240;
    private static final int REFERENCE_LINK_MIN_HEIGHT_DP = 38;
    private static final int REFERENCE_LINK_CORNER_RADIUS_DP = 6;
    private static final int REFERENCE_LINK_ICON_SIZE_DP = 17;
    private static final String PREVIEW_COMMENT_BODY = "This reminds me of the old systems where the boring path was often the most durable one. The less hidden state there is, the easier it is to reason about. [0]<p>[0] <a href=\"https://example.com/reference\" rel=\"nofollow\">https://example.com/reference</a>";

    private ViewGroup previewRoot;
    private ViewGroup previewItemContainer;
    private View boundItemView;
    private HtmlTextView commentBody;
    private TextView commentBy;
    private TextView commentByTime;
    private TextView commentHiddenCount;
    private TextView commentHiddenText;
    private View commentIndentIndicator;
    private LinearLayout referenceLinksContainer;
    private boolean cardStyle;
    private String displayStyleOverride;
    private ValueAnimator textSizeAnimator;
    private float textSizeTargetSp = -1;
    private final View.OnLayoutChangeListener previewContainerLayoutChangeListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft) {
                    syncReservedPreviewHeight();
                }
            };

    public CommentContentPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_comment_content_preview);
        setSelectable(false);
    }

    public CommentContentPreviewPreference(Context context) {
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
        PreferenceCommentContentPreviewBinding binding =
                PreferenceCommentContentPreviewBinding.bind(itemView);
        previewRoot = binding.commentContentPreviewRoot;
        previewItemContainer = binding.commentContentPreviewItemContainer;
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
            previewItemContainer.addOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        cardStyle = SettingsUtils.shouldUseCardCommentDisplayStyle(getContext());
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
        cancelTextSizeAnimator();
        if (previewItemContainer != null) {
            previewItemContainer.removeOnLayoutChangeListener(previewContainerLayoutChangeListener);
        }
        previewRoot = null;
        previewItemContainer = null;
        boundItemView = null;
        commentBody = null;
        commentBy = null;
        commentByTime = null;
        commentHiddenCount = null;
        commentHiddenText = null;
        commentIndentIndicator = null;
        referenceLinksContainer = null;
        displayStyleOverride = null;
        textSizeTargetSp = -1;
    }

    private void applyDisplayStyle(String displayStyle) {
        displayStyleOverride = displayStyle;
        boolean useCardStyle = SettingsUtils.COMMENT_DISPLAY_STYLE_CARD.equals(displayStyle);
        if (useCardStyle == cardStyle) {
            return;
        }

        cardStyle = useCardStyle;
        inflatePreviewItem();
        updatePreview();
        syncReservedPreviewHeight();
        requestPreviewRemeasure();
    }

    @SuppressLint("SetTextI18n")
    private void inflatePreviewItem() {
        if (previewItemContainer == null) {
            return;
        }

        cancelTextSizeAnimator();
        previewItemContainer.removeAllViews();
        PreviewCommentItemBinding binding = inflatePreviewCommentItemBinding();
        View itemView = binding.root;
        previewItemContainer.addView(itemView, createPreviewItemLayoutParams());

        commentBody = binding.commentBody;
        commentBy = binding.commentBy;
        commentByTime = binding.commentByTime;
        commentHiddenCount = binding.commentHiddenCount;
        commentHiddenText = binding.commentHiddenText;
        commentIndentIndicator = binding.commentIndentIndicator;
        referenceLinksContainer = binding.referenceLinksContainer;

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
        disablePreviewItemScrollbars();
    }

    private void updatePreview() {
        bindPreviewCommentContent(commentBody, referenceLinksContainer);
        disablePreviewItemScrollbars();
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false);
        updateDepthIndicator();
    }

    private void updateCollectedLinksPreview(boolean animate) {
        if (commentBody == null) {
            return;
        }

        bindPreviewCommentContent(commentBody, referenceLinksContainer);
        disablePreviewItemScrollbars();
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false);
        syncReservedPreviewHeight();
        requestPreviewRemeasure();
    }

    private void bindPreviewCommentContent(HtmlTextView body, LinearLayout linksContainer) {
        boolean collectLinks = SettingsUtils.shouldCollectLinksInComments(getContext());
        CollectedReferenceLinks.Result referenceLinks = collectLinks
                ? CollectedReferenceLinks.parse(PREVIEW_COMMENT_BODY)
                : null;
        boolean hasCollectedLinks = referenceLinks != null && referenceLinks.hasLinks();

        if (body != null) {
            body.setHtml(hasCollectedLinks ? referenceLinks.getBodyHtml() : PREVIEW_COMMENT_BODY);
            disablePreviewLinks(body);
        }

        bindPreviewReferenceLinks(linksContainer, referenceLinks);
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

    private View createPreviewReferenceLinkRow(LinearLayout container, CollectedReferenceLinks.ReferenceLink link) {
        Context context = container.getContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(false);
        row.setVerticalScrollBarEnabled(false);
        row.setHorizontalScrollBarEnabled(false);
        row.setMinimumHeight(Utils.pxFromDpInt(context.getResources(), REFERENCE_LINK_MIN_HEIGHT_DP));
        row.setPadding(
                Utils.pxFromDpInt(context.getResources(), 8),
                Utils.pxFromDpInt(context.getResources(), 5),
                Utils.pxFromDpInt(context.getResources(), 8),
                Utils.pxFromDpInt(context.getResources(), 5));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(Utils.pxFromDpInt(context.getResources(), REFERENCE_LINK_CORNER_RADIUS_DP));
        background.setStroke(
                Utils.pxFromDpInt(context.getResources(), 1),
                MaterialColors.getColor(container, R.attr.commentDividerColor));
        row.setBackground(background);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Utils.pxFromDpInt(context.getResources(), 4);
        row.setLayoutParams(rowParams);

        ImageView favicon = new ImageView(context);
        int iconSize = Utils.pxFromDpInt(context.getResources(), REFERENCE_LINK_ICON_SIZE_DP);
        LinearLayout.LayoutParams faviconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        faviconParams.rightMargin = Utils.pxFromDpInt(context.getResources(), 8);
        favicon.setLayoutParams(faviconParams);
        favicon.setImageResource(R.drawable.ic_action_web);
        favicon.setContentDescription(null);
        favicon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(favicon);

        if (link.hasNumber()) {
            TextView number = new TextView(context);
            number.setText("[" + link.getNumber() + "]");
            number.setTextColor(MaterialColors.getColor(container, R.attr.storyColorDisabled));
            number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            FontUtils.setTypefaceForFont(number, SettingsUtils.getPreferredFont(context), true, 13);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            numberParams.rightMargin = Utils.pxFromDpInt(context.getResources(), 8);
            number.setLayoutParams(numberParams);
            row.addView(number);
        }

        TextView label = new TextView(context);
        label.setText(getPreviewReferenceLinkLabel(link));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(MaterialColors.getColor(container, R.attr.storyColorNormal));
        FontUtils.setTypefaceForFont(label, SettingsUtils.getPreferredFont(context), false,
                getReferenceLinkLabelTextSize(SettingsUtils.getPreferredCommentTextSize(context)));
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(label);

        row.setContentDescription("Reference link preview: " + getPreviewReferenceLinkLabel(link));
        row.setOnClickListener(v -> {
        });

        return row;
    }

    private String getPreviewReferenceLinkLabel(CollectedReferenceLinks.ReferenceLink link) {
        String label = link.getLabel();
        if (TextUtils.isEmpty(label)) {
            return link.getUrl();
        }
        return label.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private void applyTextSize(float textSize, boolean animate) {
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
            syncReservedPreviewHeight();
            requestPreviewRemeasure();
            return;
        }

        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        float currentTextSizeSp = targetBody.getTextSize() / scaledDensity;
        if (Math.abs(currentTextSizeSp - adjustedTextSize) < 0.01f) {
            targetBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize);
            syncReservedPreviewHeight();
            requestPreviewRemeasure();
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
            syncReservedPreviewHeight();
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

    private void cancelTextSizeAnimator() {
        if (textSizeAnimator == null) {
            return;
        }

        textSizeAnimator.removeAllUpdateListeners();
        textSizeAnimator.removeAllListeners();
        textSizeAnimator.cancel();
        textSizeAnimator = null;
    }

    private void applyReferenceLinkTextSize(float commentTextSize) {
        if (referenceLinksContainer == null) {
            return;
        }

        String font = SettingsUtils.getPreferredFont(referenceLinksContainer.getContext());
        float labelTextSize = getReferenceLinkLabelTextSize(commentTextSize);
        for (int i = 0; i < referenceLinksContainer.getChildCount(); i++) {
            View child = referenceLinksContainer.getChildAt(i);
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
        PreviewPreferenceViewUtils.setExactHeight(previewItemContainer, reservedHeight);
        requestPreviewRemeasure();
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
    }

    private PreviewCommentItemBinding inflatePreviewCommentItemBinding() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (cardStyle) {
            CommentsItemCardBinding binding =
                    CommentsItemCardBinding.inflate(inflater, previewItemContainer, false);
            return new PreviewCommentItemBinding(
                    binding.getRoot(),
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
                    binding.commentHiddenCount,
                    binding.commentHiddenShort,
                    binding.commentIndentIndicator,
                    binding.commentReferenceLinksContainer);
        }

        CommentsItemBinding binding = CommentsItemBinding.inflate(inflater, previewItemContainer, false);
        return new PreviewCommentItemBinding(
                binding.getRoot(),
                binding.commentBody,
                binding.commentBy,
                binding.commentByTime,
                binding.commentHiddenCount,
                binding.commentHiddenShort,
                binding.commentIndentIndicator,
                binding.commentReferenceLinksContainer);
    }

    private static class PreviewCommentItemBinding {
        final View root;
        final HtmlTextView commentBody;
        final TextView commentBy;
        final TextView commentByTime;
        final TextView commentHiddenCount;
        final TextView commentHiddenText;
        final View commentIndentIndicator;
        final LinearLayout referenceLinksContainer;

        PreviewCommentItemBinding(
                View root,
                HtmlTextView commentBody,
                TextView commentBy,
                TextView commentByTime,
                TextView commentHiddenCount,
                TextView commentHiddenText,
                View commentIndentIndicator,
                LinearLayout referenceLinksContainer) {
            this.root = root;
            this.commentBody = commentBody;
            this.commentBy = commentBy;
            this.commentByTime = commentByTime;
            this.commentHiddenCount = commentHiddenCount;
            this.commentHiddenText = commentHiddenText;
            this.commentIndentIndicator = commentIndentIndicator;
            this.referenceLinksContainer = referenceLinksContainer;
        }
    }

    private void requestPreviewRemeasure() {
        PreviewPreferenceViewUtils.requestPreviewRemeasure(previewItemContainer, previewRoot, boundItemView);
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
