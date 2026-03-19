package com.simon.harmonichackernews.settings;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.AboutActivity;
import com.simon.harmonichackernews.BuildConfig;
import com.simon.harmonichackernews.R;

public class SettingsHeaderFragment extends BaseSettingsFragment {

    public static final String DEFAULT_KEY = "pref_header_appearance";

    private String selectedKey = DEFAULT_KEY;
    private GradientDrawable selectedDrawable;
    private RecyclerView.OnChildAttachStateChangeListener attachListener;

    @Override
    protected String getToolbarTitle() {
        return "Settings";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        Preference aboutPref = findPreference("pref_about");
        if (aboutPref != null) {
            aboutPref.setSummary("Version " + BuildConfig.VERSION_NAME);
            aboutPref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getContext(), AboutActivity.class));
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SettingsCallback callback = getSettingsCallback();
        if (callback == null || !callback.isTwoPane()) {
            return;
        }

        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                typedValue, true);
        int selectedColor = typedValue.data;

        float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 28,
                getResources().getDisplayMetrics());

        selectedDrawable = new GradientDrawable();
        selectedDrawable.setShape(GradientDrawable.RECTANGLE);
        selectedDrawable.setCornerRadius(cornerRadius);
        selectedDrawable.setColor(selectedColor);

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

    private void updateSelectionHighlight() {
        if (getView() == null || selectedKey == null || selectedDrawable == null) {
            return;
        }

        RecyclerView listView = getListView();
        PreferenceScreen screen = getPreferenceScreen();

        int selectedIndex = -1;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            if (selectedKey.equals(screen.getPreference(i).getKey())) {
                selectedIndex = i;
                break;
            }
        }

        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int position = listView.getChildAdapterPosition(child);
            if (position == selectedIndex && selectedDrawable.getConstantState() != null) {
                child.setBackground(
                        selectedDrawable.getConstantState().newDrawable().mutate());
            } else {
                child.setBackground(null);
            }
        }
    }
}
