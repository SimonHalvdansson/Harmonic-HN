package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class AiSummarySystemPromptPreference extends Preference {

    public AiSummarySystemPromptPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AiSummarySystemPromptPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setMaxLines(10);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        }
    }
}
