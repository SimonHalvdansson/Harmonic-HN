package com.simon.harmonichackernews.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.AboutActivity;
import com.simon.harmonichackernews.BuildConfig;
import com.simon.harmonichackernews.DebugActivity;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.SettingsActivity;

public class SettingsHeaderFragment extends BaseSettingsFragment {

    public static final String DEFAULT_KEY = "pref_header_appearance";
    public static final String ABOUT_KEY = "pref_about";
    public static final String DEBUG_KEY = "pref_debug";

    private String selectedKey = DEFAULT_KEY;
    private RecyclerView.OnChildAttachStateChangeListener attachListener;

    @Override
    protected String getToolbarTitle() {
        return "Settings";
    }

    @Override
    protected boolean showNavigationIconInTwoPane() {
        return true;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        Preference debugPref = findPreference(DEBUG_KEY);
        if (debugPref != null) {
            debugPref.setVisible(BuildConfig.DEBUG || "debugFast".equals(BuildConfig.BUILD_TYPE));
            debugPref.setOnPreferenceClickListener(preference -> {
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).showDebug();
                    return true;
                }
                startActivity(new Intent(getContext(), DebugActivity.class));
                return true;
            });
        }

        Preference aboutPref = findPreference(ABOUT_KEY);
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(preference -> {
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).showAbout();
                    return true;
                }
                startActivity(new Intent(getContext(), AboutActivity.class));
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!shouldShowSelectionHighlight()) {
            return;
        }

        RecyclerView listView = getListView();
        listView.post(this::updateSelectionHighlight);

        attachListener = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                updateSelectionHighlight();
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        };
        listView.addOnChildAttachStateChangeListener(attachListener);
    }

    @Override
    public void onDestroyView() {
        if (attachListener != null) {
            getListView().removeOnChildAttachStateChangeListener(attachListener);
            attachListener = null;
        }
        super.onDestroyView();
    }

    public void setSelectedKey(String key) {
        this.selectedKey = key;
        updateSelectionHighlight();
    }

    private boolean shouldShowSelectionHighlight() {
        if (getActivity() instanceof SettingsActivity
                && ((SettingsActivity) getActivity()).shouldShowHeaderSelection()) {
            return true;
        }

        SettingsCallback callback = getSettingsCallback();
        return callback != null && callback.isTwoPane();
    }

    private void updateSelectionHighlight() {
        if (getView() == null || selectedKey == null) {
            return;
        }

        RecyclerView listView = getListView();
        PreferenceScreen screen = getPreferenceScreen();

        int selectedIndex = -1;
        int visibleIndex = -1;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (!preference.isVisible()) {
                continue;
            }

            visibleIndex++;
            if (selectedKey.equals(preference.getKey())) {
                selectedIndex = visibleIndex;
                break;
            }
        }

        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int position = listView.getChildAdapterPosition(child);
            child.setActivated(position == selectedIndex);
        }
    }
}
