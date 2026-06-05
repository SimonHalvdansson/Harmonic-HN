package com.simon.harmonichackernews.settings;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FiltersTagsPreferenceFragment extends BaseSettingsFragment {

    private PreferenceCategory tagsCategory;

    @Override
    protected String getToolbarTitle() {
        return "Filters and tags";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_filters_tags, rootKey);

        setupFilterPreference(
                "pref_filter",
                "Word or phrase",
                "No story title filters");
        setupFilterPreference(
                "pref_filter_domains",
                "Domain",
                "No domain filters");
        setupFilterPreference(
                "pref_filter_users",
                "Username",
                "No blocked users");

        tagsCategory = findPreference("pref_category_tags");
        updateUserTags();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUserTags();
    }

    private void updateUserTags() {
        if (tagsCategory == null || getContext() == null) {
            return;
        }

        Map<String, String> tags = Utils.getUserTags(getContext());
        if (tags.isEmpty()) {
            removeTaggedUserPreferences(Collections.emptyList());
            if (tagsCategory.findPreference(UserTagPreference.EMPTY_KEY) == null) {
                tagsCategory.addPreference(UserTagPreference.empty(requireContext()));
            }
            return;
        }

        Preference emptyPreference = tagsCategory.findPreference(UserTagPreference.EMPTY_KEY);
        if (emptyPreference != null) {
            tagsCategory.removePreference(emptyPreference);
        }

        List<Map.Entry<String, String>> users = new ArrayList<>(tags.entrySet());
        Collections.sort(users, (a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
        List<String> wantedKeys = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            Map.Entry<String, String> user = users.get(i);
            String key = UserTagPreference.USER_KEY_PREFIX + user.getKey();
            wantedKeys.add(key);

            UserTagPreference preference = tagsCategory.findPreference(key);
            if (preference == null) {
                preference = new UserTagPreference(
                        requireContext(),
                        user.getKey(),
                        user.getValue(),
                        getParentFragmentManager(),
                        this::updateUserTags);
                tagsCategory.addPreference(preference);
            } else {
                preference.setTag(user.getValue());
            }
            preference.setOrder(i);
        }

        removeTaggedUserPreferences(wantedKeys);
    }

    private void removeTaggedUserPreferences(List<String> wantedKeys) {
        List<Preference> preferencesToRemove = new ArrayList<>();
        for (int i = 0; i < tagsCategory.getPreferenceCount(); i++) {
            Preference preference = tagsCategory.getPreference(i);
            String key = preference.getKey();
            if (key != null
                    && key.startsWith(UserTagPreference.USER_KEY_PREFIX)
                    && !wantedKeys.contains(key)) {
                preferencesToRemove.add(preference);
            }
        }
        for (Preference preference : preferencesToRemove) {
            tagsCategory.removePreference(preference);
        }
    }

    private void setupFilterPreference(String key, String inputHint, String emptyMessage) {
        Preference preference = findPreference(key);
        if (preference == null) {
            return;
        }

        preference.setOnPreferenceClickListener(clickedPreference -> {
            FilterListDialogFragment.show(
                    getParentFragmentManager(),
                    clickedPreference.getKey(),
                    String.valueOf(clickedPreference.getTitle()),
                    inputHint,
                    emptyMessage);
            return true;
        });
    }
}
