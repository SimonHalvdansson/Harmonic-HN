package com.simon.harmonichackernews.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

public class CommentContentPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ViewGroup previewItemContainer;
    private HtmlTextView commentBody;
    private TextView commentBy;
    private TextView commentByTime;
    private TextView commentHiddenCount;
    private TextView commentHiddenText;
    private View commentIndentIndicator;
    private boolean cardStyle;
    private String displayStyleOverride;

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

        previewItemContainer = holder.itemView.findViewById(R.id.comment_content_preview_item_container);
        cardStyle = SettingsUtils.shouldUseCardCommentDisplayStyle(getContext());
        inflatePreviewItem();
        updatePreview();
    }

    public void updateDisplayStyle(String displayStyle) {
        applyDisplayStyle(displayStyle);
    }

    public void updateTextSize(String textSize) {
        applyTextSize(parseTextSize(textSize));
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
            applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()));
            return;
        }

        if (SettingsUtils.PREF_COMMENT_DEPTH_INDICATORS.equals(key)
                || SettingsUtils.PREF_MONOCHROME_COMMENT_DEPTH.equals(key)) {
            updateDepthIndicator();
        }
    }

    private void clearPreviewViews() {
        previewItemContainer = null;
        commentBody = null;
        commentBy = null;
        commentByTime = null;
        commentHiddenCount = null;
        commentHiddenText = null;
        commentIndentIndicator = null;
        displayStyleOverride = null;
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
        if (previewItemContainer != null) {
            previewItemContainer.requestLayout();
        }
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
        applyTextSize(SettingsUtils.getPreferredCommentTextSize(getContext()));
        updateDepthIndicator();
    }

    private void applyTextSize(int textSize) {
        if (commentBody == null) {
            return;
        }
        commentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
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

    private int parseTextSize(String textSize) {
        try {
            return SettingsUtils.clampCommentTextSize(Integer.parseInt(textSize));
        } catch (NumberFormatException e) {
            return SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE;
        }
    }
}
