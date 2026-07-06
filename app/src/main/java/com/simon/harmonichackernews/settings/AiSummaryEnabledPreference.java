package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

public class AiSummaryEnabledPreference extends SwitchPreferenceCompat {

    public AiSummaryEnabledPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AiSummaryEnabledPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setActivated(true);
    }
}
