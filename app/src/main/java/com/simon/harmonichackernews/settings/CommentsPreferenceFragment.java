package com.simon.harmonichackernews.settings;

import android.os.Bundle;

import com.simon.harmonichackernews.R;

public class CommentsPreferenceFragment extends BaseSettingsFragment {

    @Override
    protected String getToolbarTitle() {
        return "Comments";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_comments, rootKey);
    }
}
