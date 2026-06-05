package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.FontSelectionDialogBinding;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public class FontSelectionDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_font_selection_dialog";

    private final Map<String, MaterialRadioButton> radioButtons = new HashMap<>();
    private String selectedFont;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        FontSelectionDialogBinding binding = FontSelectionDialogBinding.inflate(getLayoutInflater());
        LinearLayout container = binding.fontOptionsContainer;

        selectedFont = SettingsUtils.getPreferredFont(requireContext());

        buildFontOptions(container);
        updateSelection();

        builder.setTitle("Title and comment font");
        builder.setView(binding.getRoot());
        return builder.create();
    }

    public static void show(FragmentManager fm) {
        new FontSelectionDialogFragment().show(fm, TAG);
    }

    private void buildFontOptions(LinearLayout container) {
        Context context = container.getContext();
        String[] entries = getResources().getStringArray(R.array.font_entries);
        String[] values = getResources().getStringArray(R.array.font_values);
        int optionCount = Math.min(entries.length, values.length);
        int textColor = MaterialColors.getColor(container, R.attr.storyColorNormal);
        int secondaryTextColor = MaterialColors.getColor(container, R.attr.secondaryTextColor);

        for (int i = 0; i < optionCount; i++) {
            String label = entries[i];
            String font = values[i];
            LinearLayout row = createOptionRow(context, label, font, textColor, secondaryTextColor);
            container.addView(row);
        }
    }

    private LinearLayout createOptionRow(Context context, String label, String font, int textColor, int secondaryTextColor) {
        int horizontalPadding = Utils.pxFromDpInt(getResources(), 24);
        int verticalPadding = Utils.pxFromDpInt(getResources(), 4);
        int minHeight = Utils.pxFromDpInt(getResources(), 64);
        int radioMarginEnd = Utils.pxFromDpInt(getResources(), 4);

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

        MaterialRadioButton radioButton = new MaterialRadioButton(context);
        radioButton.setClickable(false);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        radioParams.setMarginEnd(radioMarginEnd);
        row.addView(radioButton, radioParams);
        radioButtons.put(font, radioButton);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textContainer, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(textColor);
        title.setSingleLine(false);
        FontUtils.setTypefaceForFont(title, font, true, 16);
        textContainer.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView preview = new TextView(context);
        preview.setText("Example text");
        preview.setTextColor(secondaryTextColor);
        preview.setSingleLine(false);
        FontUtils.setTypefaceForFont(preview, font, false, 14);
        textContainer.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(view -> {
            selectedFont = font;
            SettingsUtils.setPreferredFont(requireContext(), selectedFont);
            FontUtils.init(requireContext());
            updateSelection();
            dismiss();
        });
        return row;
    }

    private void updateSelection() {
        String safeSelectedFont = SettingsUtils.sanitizeFont(selectedFont);
        for (Map.Entry<String, MaterialRadioButton> entry : radioButtons.entrySet()) {
            entry.getValue().setChecked(entry.getKey().equals(safeSelectedFont));
        }
    }
}
