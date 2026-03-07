package com.simon.harmonichackernews.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import com.simon.harmonichackernews.ManageUserTagsDialogFragment;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.Utils;

public class FiltersTagsPreferenceFragment extends BaseSettingsFragment {

    @Override
    protected String getToolbarTitle() {
        return "Filters & Tags";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_filters_tags, rootKey);

        updateUserTagsSubtitle();

        findPreference("pref_manage_user_tags").setOnPreferenceClickListener(preference -> {
            ManageUserTagsDialogFragment.showManageUserTagsDialog(getParentFragmentManager(), () -> updateUserTagsSubtitle());
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUserTagsSubtitle();
    }

    private void updateUserTagsSubtitle() {
        Preference pref = findPreference("pref_manage_user_tags");
        if (pref != null && getContext() != null) {
            int count = Utils.getUserTags(getContext()).size();
            if (count > 0) {
                pref.setSummary(count + " user" + (count == 1 ? "" : "s") + " with tag" + (count == 1 ? "" : "s"));
                changePrefStatus(pref, true);
            } else {
                pref.setSummary("");
                changePrefStatus(pref, false);
            }
        }
    }
}
