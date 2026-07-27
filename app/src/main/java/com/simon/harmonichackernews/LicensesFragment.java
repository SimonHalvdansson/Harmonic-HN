package com.simon.harmonichackernews;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.simon.harmonichackernews.databinding.FragmentLicensesBinding;
import com.simon.harmonichackernews.settings.SettingsCallback;
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LicensesFragment extends Fragment {

    private static final String[][] COMMON_LICENSES_BEFORE_LOCAL_AI = {
            {"AndroidX", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"},
            {"Volley", "Google", "Apache License 2.0", "https://github.com/google/volley"},
            {"Material Components", "Google", "Apache License 2.0", "https://github.com/material-components/material-components-android"}
    };

    private static final String[][] LOCAL_AI_LICENSES = {
            {"ML Kit GenAI APIs", "Google", "ML Kit Terms of Service", "https://developers.google.com/ml-kit/genai"},
            {"LiteRT-LM", "Google", "Apache License 2.0", "https://github.com/google-ai-edge/LiteRT-LM"},
            {"llama.cpp", "ggml-org", "MIT License", "https://github.com/ggml-org/llama.cpp"},
            {"ggml", "ggml-org", "MIT License", "https://github.com/ggml-org/ggml"}
    };

    private static final String[][] COMMON_LICENSES_AFTER_LOCAL_AI = {
            {"Kotlin", "JetBrains", "Apache License 2.0", "https://kotlinlang.org/"},
            {"kotlinx.coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"},
            {"HtmlTextView", "SufficientlySecure", "Apache License 2.0", "https://github.com/SufficientlySecure/html-textview"},
            {"OkHttp", "Square", "Apache License 2.0", "https://square.github.io/okhttp/"},
            {"Coil", "Instacart", "Apache License 2.0", "https://coil-kt.github.io/coil/"},
            {"Shimmer", "Facebook", "BSD License", "https://github.com/facebookarchive/shimmer-android"},
            {"pdf.js", "Mozilla", "Apache License 2.0", "https://mozilla.github.io/pdf.js/"},
            {"Readability", "Mozilla", "Apache License 2.0", "https://github.com/mozilla/readability"},
            {"Materialistic", "Hidroh", "Apache License 2.0", "https://github.com/hidroh/materialistic"},
            {"jsoup", "Jonathan Hedley", "MIT License", "https://jsoup.org/"},
            {"Markwon", "Noties", "Apache License 2.0", "https://noties.io/Markwon/"},
            {"SwipeBackLayout", "Gongwen", "Apache License 2.0", "https://github.com/gongwen/SwipeBackLayout"}
    };

    private FragmentLicensesBinding binding;

    public LicensesFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentLicensesBinding.inflate(inflater, container, false);

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setUpWrapperInsets(wrapper);

        MaterialToolbar toolbar = new MaterialToolbar(requireContext(), null,
                androidx.appcompat.R.attr.toolbarStyle);
        toolbar.setTitle("Third-party licenses");
        toolbar.setTitleCentered(false);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription("Back to About");
        toolbar.setNavigationOnClickListener(v -> navigateBack());
        ViewCompat.setAccessibilityHeading(toolbar, true);

        wrapper.addView(toolbar);
        wrapper.addView(binding.getRoot(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return wrapper;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean isSettingsTwoPane = getActivity() instanceof SettingsCallback
                && ((SettingsCallback) getActivity()).isTwoPane();
        applyInsets(isSettingsTwoPane);
        populateLicenses();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getView() != null) {
            ViewCompat.requestApplyInsets(getView());
        }
    }

    private void populateLicenses() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<String[]> licenses = new ArrayList<>();
        Collections.addAll(licenses, COMMON_LICENSES_BEFORE_LOCAL_AI);
        if (LocalAiRuntimeManager.isLocalAiIncluded()) {
            Collections.addAll(licenses, LOCAL_AI_LICENSES);
        }
        Collections.addAll(licenses, COMMON_LICENSES_AFTER_LOCAL_AI);

        for (int i = 0; i < licenses.size(); i++) {
            String[] license = licenses.get(i);
            View row = inflater.inflate(R.layout.license_list_row, binding.licensesList, false);

            TextView name = row.findViewById(R.id.license_name);
            TextView creator = row.findViewById(R.id.license_creator);
            TextView type = row.findViewById(R.id.license_type);
            View divider = row.findViewById(R.id.license_divider);

            name.setText(license[0]);
            creator.setText(license[1]);
            creator.setVisibility(license[1].isEmpty() ? View.GONE : View.VISIBLE);
            type.setText(license[2]);
            divider.setVisibility(i == licenses.size() - 1 ? View.GONE : View.VISIBLE);
            row.setContentDescription(license[0] + ", " + license[2]
                    + (license[1].isEmpty() ? "" : ", by " + license[1])
                    + ". Open project page.");
            row.setOnClickListener(v -> Utils.launchCustomTab(requireActivity(), license[3]));

            binding.licensesList.addView(row);
        }
    }

    private void navigateBack() {
        if (getActivity() instanceof SettingsActivity
                && ((SettingsActivity) getActivity()).isTwoPane()) {
            ((SettingsActivity) getActivity()).showAbout();
        } else if (getActivity() != null) {
            getActivity().getOnBackPressedDispatcher().onBackPressed();
        }
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

    private void applyInsets(boolean isSettingsTwoPane) {
        final View root = binding.getRoot();
        final View container = binding.licensesContainer;
        final int padTop = container.getPaddingTop();
        final int padBottom = container.getPaddingBottom();

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
                    padBottom + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
