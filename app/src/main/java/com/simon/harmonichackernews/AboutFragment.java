package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.ActivityAboutBinding;
import com.simon.harmonichackernews.settings.SettingsCallback;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.Utils;

public class AboutFragment extends Fragment {

    public AboutFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return ActivityAboutBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityAboutBinding binding = ActivityAboutBinding.bind(view);
        ViewCompat.setAccessibilityHeading(binding.aboutTitle, true);
        ViewCompat.setAccessibilityHeading(binding.aboutLicensesHeader, true);

        boolean isSettingsTwoPane = getActivity() instanceof SettingsCallback
                && ((SettingsCallback) getActivity()).isTwoPane();
        applyInsets(binding, isSettingsTwoPane);

        String versionText = BuildConfig.DEBUG
                ? String.format("Version %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE)
                : "Version " + BuildConfig.VERSION_NAME;
        binding.aboutVersion.setText(versionText);

        binding.aboutGithub.setOnClickListener(v -> openGithub());
        binding.aboutChangelog.setOnClickListener(v -> openChangelog());
        binding.aboutPrivacy.setOnClickListener(v -> openPrivacy());
    }

    private void applyInsets(ActivityAboutBinding binding, boolean isSettingsTwoPane) {
        final View root = binding.getRoot();
        final View container = binding.aboutContainer;
        final int padTop = container.getPaddingTop();
        final int padBot = container.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int sidePadding = isSettingsTwoPane
                    ? 0
                    : getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);

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

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getView() != null) {
            ViewCompat.requestApplyInsets(getView());
        }
    }

    private void openGithub() {
        String url = "https://github.com/SimonHalvdansson/Harmonic-HN";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private void openChangelog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Changelog")
                .setMessage(Changelog.getFormatted(requireContext()))
                .setNegativeButton("Done", null).create();

        dialog.show();
    }

    private void openPrivacy() {
        Utils.launchCustomTab(requireActivity(), "https://simonhalvdansson.github.io/harmonic_privacy.html");
    }
}
