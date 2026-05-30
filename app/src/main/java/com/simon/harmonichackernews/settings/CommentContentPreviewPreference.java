package com.simon.harmonichackernews.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

public class CommentContentPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long PREVIEW_ANIMATION_DURATION_MS = 180;
    private static final long TEXT_SIZE_ANIMATION_DURATION_MS = 120;

    private ViewGroup previewRoot;
    private ViewGroup previewItemContainer;
    private HtmlTextView commentBody;
    private TextView commentBy;
    private TextView commentByTime;
    private TextView commentHiddenCount;
    private TextView commentHiddenText;
    private View commentIndentIndicator;
    private boolean cardStyle;
    private String displayStyleOverride;
    private ValueAnimator textSizeAnimator;
    private float textSizeTargetSp = -1;

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

        View root = holder.itemView.findViewById(R.id.comment_content_preview_root);
        previewRoot = root instanceof ViewGroup
                ? (ViewGroup) root
                : holder.itemView instanceof ViewGroup
                ? (ViewGroup) holder.itemView
                : null;
        previewItemContainer = holder.itemView.findViewById(R.id.comment_content_preview_item_container);
        cardStyle = SettingsUtils.shouldUseCardCommentDisplayStyle(getContext());
        inflatePreviewItem();
        updatePreview();
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
        previewRoot = null;
        previewItemContainer = null;
        commentBody = null;
        commentBy = null;
        commentByTime = null;
        commentHiddenCount = null;
        commentHiddenText = null;
        commentIndentIndicator = null;
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
        previewItemContainer.addView(itemView);

        commentBody = itemView.findViewById(R.id.comment_body);
        commentBy = itemView.findViewById(R.id.comment_by);
        commentByTime = itemView.findViewById(R.id.comment_by_time);
        commentHiddenCount = itemView.findViewById(R.id.comment_hidden_count);
        commentHiddenText = itemView.findViewById(R.id.comment_hidden_short);
        commentIndentIndicator = itemView.findViewById(R.id.comment_indent_indicator);

        itemView.setClickable(false);
        itemView.setFocusable(false);
        if (commentBody != null) {
            commentBody.setClickable(false);
            commentBody.setFocusable(false);
            commentBody.setHtml("This reminds me of the old systems where the boring path was often the most durable one. The less hidden state there is, the easier it is to reason about.");
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
    }

    private void updatePreview() {
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()), false);
        updateDepthIndicator();
    }

    private void applyTextSize(float textSize, boolean animate) {
        float clampedTextSize = SettingsUtils.clampCommentTextSize(textSize);
        if (commentBody == null) {
            textSizeTargetSp = clampedTextSize;
            return;
        }

        if (animate && Math.abs(textSizeTargetSp - clampedTextSize) < 0.01f) {
            return;
        }
        textSizeTargetSp = clampedTextSize;

        if (textSizeAnimator != null) {
            textSizeAnimator.cancel();
            textSizeAnimator = null;
        }

        if (!animate || !ViewCompat.isLaidOut(commentBody)) {
            commentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, clampedTextSize);
            requestPreviewRemeasure();
            return;
        }

        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        float currentTextSizeSp = commentBody.getTextSize() / scaledDensity;
        if (Math.abs(currentTextSizeSp - clampedTextSize) < 0.01f) {
            commentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, clampedTextSize);
            requestPreviewRemeasure();
            return;
        }

        textSizeAnimator = ValueAnimator.ofFloat(currentTextSizeSp, clampedTextSize);
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

    private void updateDepthIndicator() {
        if (commentIndentIndicator == null || getContext() == null) {
            return;
        }

        commentIndentIndicator.setVisibility(View.VISIBLE);
        int colorResource = CommentDepthIndicatorUtils.getColorResource(
                getContext(),
                SettingsUtils.getPreferredCommentDepthIndicatorMode(getContext()),
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
