package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.ActivityAboutBinding;
import com.simon.harmonichackernews.settings.SettingsCallback;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

public class AboutFragment extends Fragment {

    private ActivityAboutBinding binding;

    public AboutFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityAboutBinding.inflate(inflater, container, false);

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setUpWrapperInsets(wrapper);

        MaterialToolbar toolbar = new MaterialToolbar(requireContext(), null,
                androidx.appcompat.R.attr.toolbarStyle);
        toolbar.setTitle("About");
        toolbar.setTitleCentered(false);
        ViewCompat.setAccessibilityHeading(toolbar, true);

        boolean isSettingsTwoPane = getActivity() instanceof SettingsCallback
                && ((SettingsCallback) getActivity()).isTwoPane();
        if (!isSettingsTwoPane) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        wrapper.addView(toolbar);
        wrapper.addView(binding.getRoot(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return wrapper;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void setUpWrapperInsets(@NonNull LinearLayout wrapper) {
        ViewCompat.setOnApplyWindowInsetsListener(wrapper, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            v.setPadding(
                    Math.max(systemBars.left, cutout.left),
                    systemBars.top,
                    Math.max(systemBars.right, cutout.right),
                    0);

            return insets;
        });
        ViewUtils.requestApplyInsetsWhenAttached(wrapper);
    }

    private void applyInsets(ActivityAboutBinding binding, boolean isSettingsTwoPane) {
        final View root = binding.getRoot();
        final View container = binding.aboutContainer;
        final int padTop = container.getPaddingTop();
        final int padBot = container.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int sidePadding = isSettingsTwoPane
                    ? 0
                    : getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);

            container.setPadding(
                    sidePadding,
                    padTop,
                    sidePadding,
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
