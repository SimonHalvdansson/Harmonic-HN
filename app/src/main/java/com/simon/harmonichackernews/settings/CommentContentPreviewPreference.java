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
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

public class CommentContentPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long PREVIEW_ANIMATION_DURATION_MS = 180;
    private static final long TEXT_SIZE_ANIMATION_DURATION_MS = 120;
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
        View root = itemView.findViewById(R.id.comment_content_preview_root);
        previewRoot = root instanceof ViewGroup
                ? (ViewGroup) root
                : itemView instanceof ViewGroup
                ? (ViewGroup) itemView
                : null;
        previewItemContainer = itemView.findViewById(R.id.comment_content_preview_item_container);
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
        if (textSizeAnimator != null) {
            textSizeAnimator.cancel();
            textSizeAnimator = null;
        }
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

        beginPreviewTransition();
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

        previewItemContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int layout = cardStyle ? R.layout.comments_item_card : R.layout.comments_item;
        View itemView = inflater.inflate(layout, previewItemContainer, false);
        previewItemContainer.addView(itemView, createPreviewItemLayoutParams());

        commentBody = itemView.findViewById(R.id.comment_body);
        commentBy = itemView.findViewById(R.id.comment_by);
        commentByTime = itemView.findViewById(R.id.comment_by_time);
        commentHiddenCount = itemView.findViewById(R.id.comment_hidden_count);
        commentHiddenText = itemView.findViewById(R.id.comment_hidden_short);
        commentIndentIndicator = itemView.findViewById(R.id.comment_indent_indicator);
        referenceLinksContainer = itemView.findViewById(R.id.comment_reference_links_container);

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

        if (animate) {
            beginPreviewTransition();
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
            textSizeAnimator.cancel();
            textSizeAnimator = null;
        }

        if (!animate || !ViewCompat.isLaidOut(commentBody)) {
            commentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize);
            syncReservedPreviewHeight();
            requestPreviewRemeasure();
            return;
        }

        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        float currentTextSizeSp = commentBody.getTextSize() / scaledDensity;
        if (Math.abs(currentTextSizeSp - adjustedTextSize) < 0.01f) {
            commentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize);
            syncReservedPreviewHeight();
            requestPreviewRemeasure();
            return;
        }

        syncReservedPreviewHeight();
        textSizeAnimator = ValueAnimator.ofFloat(currentTextSizeSp, adjustedTextSize);
        textSizeAnimator.setDuration(TEXT_SIZE_ANIMATION_DURATION_MS);
        textSizeAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        textSizeAnimator.addUpdateListener(animation -> {
            float animatedTextSize = (float) animation.getAnimatedValue();
            commentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, animatedTextSize);
            requestPreviewRemeasure();
        });
        textSizeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                textSizeAnimator = null;
            }
        });
        textSizeAnimator.start();
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
        if (containerWidth <= 0) {
            return;
        }

        int reservedContentHeight = calculateMaxPreviewItemHeight(containerWidth);
        if (reservedContentHeight <= 0) {
            return;
        }

        int reservedHeight = reservedContentHeight
                + previewItemContainer.getPaddingTop()
                + previewItemContainer.getPaddingBottom();
        setExactHeight(previewItemContainer, reservedHeight);
        requestPreviewRemeasure();
    }

    private int calculateMaxPreviewItemHeight(int containerWidth) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        return Math.max(
                measurePreviewItemHeight(inflater, R.layout.comments_item, containerWidth),
                measurePreviewItemHeight(inflater, R.layout.comments_item_card, containerWidth));
    }

    @SuppressLint("SetTextI18n")
    private int measurePreviewItemHeight(LayoutInflater inflater, int layout, int containerWidth) {
        View itemView = inflater.inflate(layout, previewItemContainer, false);
        HtmlTextView body = itemView.findViewById(R.id.comment_body);
        TextView by = itemView.findViewById(R.id.comment_by);
        TextView byTime = itemView.findViewById(R.id.comment_by_time);
        TextView hiddenCount = itemView.findViewById(R.id.comment_hidden_count);
        TextView hiddenText = itemView.findViewById(R.id.comment_hidden_short);
        View indentIndicator = itemView.findViewById(R.id.comment_indent_indicator);
        LinearLayout measuredReferenceLinksContainer = itemView.findViewById(R.id.comment_reference_links_container);

        if (body != null) {
            bindPreviewCommentContent(body, measuredReferenceLinksContainer);
            ensureSelectedFontLoaded();
            FontUtils.setCommentTextTypeface(body, SettingsUtils.MAX_COMMENT_TEXT_SIZE);
        }
        if (by != null) {
            by.setText("pg");
        }
        if (byTime != null) {
            byTime.setText("1h");
        }
        if (hiddenCount != null) {
            hiddenCount.setVisibility(View.GONE);
        }
        if (hiddenText != null) {
            hiddenText.setVisibility(View.GONE);
        }
        if (indentIndicator != null) {
            indentIndicator.setVisibility(View.VISIBLE);
        }
        applyCommentMetaTypefaces(by, byTime, hiddenText, hiddenCount);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        itemView.measure(widthSpec, heightSpec);
        return getMeasuredOuterHeight(itemView);
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
        ViewGroup settingsList = findAncestorOfType(previewRoot, RecyclerView.class);
        if (settingsList != null && ViewCompat.isLaidOut(settingsList)) {
            TransitionManager.beginDelayedTransition(settingsList, createSettingsListTransition());
        }
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
}
