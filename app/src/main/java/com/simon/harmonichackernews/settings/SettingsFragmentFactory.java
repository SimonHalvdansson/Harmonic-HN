package com.simon.harmonichackernews.settings;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public final class SettingsFragmentFactory {

    private static final String TAG = "SettingsFragmentFactory";

    private SettingsFragmentFactory() {
    }

    @Nullable
    public static Fragment create(
            @NonNull FragmentManager fragmentManager,
            @NonNull ClassLoader classLoader,
            @Nullable String fragmentClassName) {
        if (fragmentClassName == null) {
            return null;
        }

        // These fragment names are stored as strings in preference XML, so instantiate known
        // screens directly instead of relying on their release-build class names staying stable.
        switch (fragmentClassName) {
            case "com.simon.harmonichackernews.settings.AppearancePreferenceFragment":
                return new AppearancePreferenceFragment();
            case "com.simon.harmonichackernews.settings.StoriesPreferenceFragment":
                return new StoriesPreferenceFragment();
            case "com.simon.harmonichackernews.settings.WebLinksPreferenceFragment":
                return new WebLinksPreferenceFragment();
            case "com.simon.harmonichackernews.settings.CommentsPreferenceFragment":
                return new CommentsPreferenceFragment();
            case "com.simon.harmonichackernews.settings.FiltersTagsPreferenceFragment":
                return new FiltersTagsPreferenceFragment();
            case "com.simon.harmonichackernews.settings.DataStoragePreferenceFragment":
                return new DataStoragePreferenceFragment();
            default:
                try {
                    return fragmentManager.getFragmentFactory()
                            .instantiate(classLoader, fragmentClassName);
                } catch (Fragment.InstantiationException e) {
                    Log.e(TAG, "Unable to instantiate settings fragment " + fragmentClassName, e);
                    return null;
                }
        }
    }
}
