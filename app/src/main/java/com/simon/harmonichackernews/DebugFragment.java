package com.simon.harmonichackernews;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.databinding.ActivityDebugBinding;
import com.simon.harmonichackernews.settings.SettingsCallback;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.Locale;

public class DebugFragment extends Fragment {

    public DebugFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return ActivityDebugBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityDebugBinding binding = ActivityDebugBinding.bind(view);
        ViewCompat.setAccessibilityHeading(binding.debugTitle, true);

        boolean isSettingsTwoPane = getActivity() instanceof SettingsCallback
                && ((SettingsCallback) getActivity()).isTwoPane();
        applyInsets(binding, isSettingsTwoPane);

        binding.debugBuild.setText(String.format(Locale.US,
                "Version %s - Build %d - %s\nAndroid %s (API %d)",
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.BUILD_TYPE,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT));

        binding.debugAlwaysShowTapToRefresh.setChecked(SettingsUtils.shouldAlwaysShowTapToRefresh(requireContext()));
        binding.debugAlwaysShowTapToRefresh.setOnCheckedChangeListener((buttonView, isChecked) ->
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putBoolean(SettingsUtils.PREF_ALWAYS_SHOW_TAP_TO_REFRESH, isChecked)
                        .apply());

        binding.debugLink.setOnClickListener(v -> openDebugLink());
        binding.debugCollectedLinks.setOnClickListener(v -> openDebugCollectedLinks());
        binding.debugPoll.setOnClickListener(v -> openDebugPoll());
        binding.debugInternalHnLink.setOnClickListener(v -> openDebugInternalHnLink());
        binding.debugNitterVideoTest.setOnClickListener(v -> openDebugNitterVideoTest());
        binding.debugWelcome.setOnClickListener(v -> openDebugWelcome());
        binding.debugNotifications.setOnClickListener(v -> openDebugNotifications());
    }

    private void applyInsets(ActivityDebugBinding binding, boolean isSettingsTwoPane) {
        final View root = binding.getRoot();
        final View container = binding.debugContainer;
        final int sidePadding = isSettingsTwoPane
                ? 0
                : getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
        final int padTop = container.getPaddingTop();
        final int padBot = container.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            container.setPadding(
                    sidePadding + Math.max(bars.left, cutout.left),
                    padTop + bars.top,
                    sidePadding + Math.max(bars.right, cutout.right),
                    padBot + Math.max(bars.bottom, ime.bottom)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void openDebugLink() {
        Utils.openLinkMaybeHN(requireActivity(), "https://news.ycombinator.com/item?id=47938725");
    }

    private void openDebugCollectedLinks() {
        Utils.openLinkMaybeHN(requireActivity(), "https://news.ycombinator.com/item?id=48352939");
    }

    private void openDebugPoll() {
        Utils.openLinkMaybeHN(requireActivity(), "https://news.ycombinator.com/item?id=39572682");
    }

    private void openDebugInternalHnLink() {
        Utils.openLinkMaybeHN(requireActivity(), "https://news.ycombinator.com/item?id=30676384");
    }

    private void openDebugNitterVideoTest() {
        Utils.openLinkMaybeHN(requireActivity(), "https://news.ycombinator.com/item?id=48012735");
    }

    private void openDebugWelcome() {
        startActivity(new Intent(requireContext(), WelcomeActivity.class));
    }

    private void openDebugNotifications() {
        DebugNotificationsDialogFragment.show(getParentFragmentManager());
    }
}
