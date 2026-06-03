package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public class FaviconProviderDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_favicon_provider_dialog";

    private static final ProviderOption[] PROVIDERS = new ProviderOption[]{
            new ProviderOption(SettingsUtils.FAVICON_PROVIDER_GOOGLE, "Google"),
            new ProviderOption(SettingsUtils.FAVICON_PROVIDER_DUCKDUCKGO, "DuckDuckGo"),
            new ProviderOption(SettingsUtils.FAVICON_PROVIDER_TWENTY, "Twenty icons")
    };

    private final Map<String, MaterialRadioButton> radioButtons = new HashMap<>();
    private String selectedProvider;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.favicon_provider_dialog, null);
        LinearLayout container = rootView.findViewById(R.id.favicon_provider_options_container);

        selectedProvider = SettingsUtils.getPreferredFaviconProvider(requireContext());
        buildProviderOptions(container);
        updateSelection();

        builder.setTitle("Favicon provider");
        builder.setView(rootView);
        return builder.create();
    }

    public static void show(FragmentManager fm) {
        new FaviconProviderDialogFragment().show(fm, TAG);
    }

    @Override
    public void onDestroyView() {
        radioButtons.clear();
        super.onDestroyView();
    }

    private void buildProviderOptions(LinearLayout container) {
        Context context = container.getContext();
        int textColor = MaterialColors.getColor(container, R.attr.storyColorNormal);
        int secondaryTextColor = MaterialColors.getColor(container, R.attr.secondaryTextColor);

        for (ProviderOption provider : PROVIDERS) {
            container.addView(createOptionRow(context, provider, textColor, secondaryTextColor));
        }
    }

    private LinearLayout createOptionRow(
            Context context,
            ProviderOption provider,
            int textColor,
            int secondaryTextColor) {
        int horizontalPadding = Utils.pxFromDpInt(getResources(), 24);
        int verticalPadding = Utils.pxFromDpInt(getResources(), 8);
        int minHeight = Utils.pxFromDpInt(getResources(), 76);
        int iconSize = Utils.pxFromDpInt(getResources(), 32);
        int iconMarginEnd = Utils.pxFromDpInt(getResources(), 16);
        int radioMarginStart = Utils.pxFromDpInt(getResources(), 12);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(minHeight);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue selectableItemBackground = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableItemBackground, true);
        row.setBackgroundResource(selectableItemBackground.resourceId);

        ImageView icon = new ImageView(context);
        icon.setImageResource(SettingsUtils.getFaviconProviderIconResource(provider.value));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(iconMarginEnd);
        row.addView(icon, iconParams);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textContainer, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        TextView title = new TextView(context);
        title.setText(provider.title);
        title.setTextColor(textColor);
        title.setSingleLine(false);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        textContainer.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText(FaviconLoader.getFaviconUrlSchema(provider.value));
        subtitle.setTextColor(secondaryTextColor);
        subtitle.setSingleLine(false);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textContainer.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialRadioButton radioButton = new MaterialRadioButton(context);
        radioButton.setClickable(false);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        radioParams.setMarginStart(radioMarginStart);
        row.addView(radioButton, radioParams);
        radioButtons.put(provider.value, radioButton);

        row.setOnClickListener(view -> {
            selectedProvider = provider.value;
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .edit();
            editor.putString(SettingsUtils.PREF_FAVICON_PROVIDER, selectedProvider);
            editor.apply();
            updateSelection();
            dismiss();
        });

        return row;
    }

    private void updateSelection() {
        String safeSelectedProvider = SettingsUtils.sanitizeFaviconProvider(selectedProvider);
        for (Map.Entry<String, MaterialRadioButton> entry : radioButtons.entrySet()) {
            entry.getValue().setChecked(entry.getKey().equals(safeSelectedProvider));
        }
    }

    private static class ProviderOption {
        final String value;
        final String title;

        ProviderOption(String value, String title) {
            this.value = value;
            this.title = title;
        }
    }
}
