package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceCommentTapActionBinding;

/**
 * Selects the action performed when a comment is tapped.
 *
 * <p>The persisted boolean intentionally retains the semantics of the former
 * "swap tap/long press behavior" switch so existing user preferences continue
 * to work without a migration.</p>
 */
public class CommentTapActionPreference extends Preference {

    private static final boolean DEFAULT_SWAP_LONG_PRESS_TAP = false;

    public CommentTapActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_comment_tap_action);
        setSelectable(false);
    }

    public CommentTapActionPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        PreferenceCommentTapActionBinding binding =
                PreferenceCommentTapActionBinding.bind(holder.itemView);
        MaterialButtonToggleGroup group = binding.commentTapActionGroup;
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        boolean swapLongPressTap = getPersistedBoolean(DEFAULT_SWAP_LONG_PRESS_TAP);

        group.clearOnButtonCheckedListeners();
        group.check(getButtonId(swapLongPressTap));
        updateLongPressSummary(summary, swapLongPressTap);
        group.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked
                    || (checkedId != R.id.comment_tap_action_toggle_visibility
                    && checkedId != R.id.comment_tap_action_details)) {
                return;
            }

            boolean newSwapLongPressTap = checkedId == R.id.comment_tap_action_details;
            boolean currentSwapLongPressTap =
                    getPersistedBoolean(DEFAULT_SWAP_LONG_PRESS_TAP);
            if (newSwapLongPressTap == currentSwapLongPressTap) {
                return;
            }

            if (!callChangeListener(newSwapLongPressTap)) {
                buttonGroup.check(getButtonId(currentSwapLongPressTap));
                return;
            }

            persistBoolean(newSwapLongPressTap);
            updateLongPressSummary(summary, newSwapLongPressTap);
        });
    }

    private int getButtonId(boolean swapLongPressTap) {
        return swapLongPressTap
                ? R.id.comment_tap_action_details
                : R.id.comment_tap_action_toggle_visibility;
    }

    private void updateLongPressSummary(TextView summary, boolean swapLongPressTap) {
        summary.setVisibility(View.VISIBLE);
        summary.setText(swapLongPressTap
                ? "Long press: Toggle visibility"
                : "Long press: Details");
    }
}
