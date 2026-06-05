package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.databinding.FilterListDialogBinding;
import com.simon.harmonichackernews.databinding.FilterListItemBinding;

import java.util.ArrayList;
import java.util.List;

public class FilterListDialogFragment extends AppCompatDialogFragment {

    private static final String ARG_KEY = "key";
    private static final String ARG_TITLE = "title";
    private static final String ARG_INPUT_HINT = "input_hint";
    private static final String ARG_EMPTY_MESSAGE = "empty_message";
    private static final String STATE_ITEMS = "items";

    private final ArrayList<String> items = new ArrayList<>();
    private LinearLayout listContainer;
    private TextView emptyView;
    private TextInputLayout inputLayout;
    private TextInputEditText inputEditText;
    private String preferenceKey;

    public static void show(androidx.fragment.app.FragmentManager fm,
                            String key,
                            String title,
                            String inputHint,
                            String emptyMessage) {
        FilterListDialogFragment fragment = new FilterListDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_INPUT_HINT, inputHint);
        args.putString(ARG_EMPTY_MESSAGE, emptyMessage);
        fragment.setArguments(args);
        fragment.show(fm, key + "_filter_list_dialog");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        Bundle args = requireArguments();
        preferenceKey = args.getString(ARG_KEY, "");
        String title = args.getString(ARG_TITLE, "");
        String inputHint = args.getString(ARG_INPUT_HINT, "");
        String emptyMessage = args.getString(ARG_EMPTY_MESSAGE, "");

        if (savedInstanceState != null) {
            ArrayList<String> savedItems = savedInstanceState.getStringArrayList(STATE_ITEMS);
            if (savedItems != null) {
                items.clear();
                items.addAll(savedItems);
            }
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            items.clear();
            items.addAll(parseItems(prefs.getString(preferenceKey, "")));
        }

        FilterListDialogBinding binding = FilterListDialogBinding.inflate(getLayoutInflater());
        listContainer = binding.filterListItems;
        emptyView = binding.filterListEmpty;
        inputLayout = binding.filterListInputLayout;
        inputEditText = binding.filterListInput;
        MaterialButton addButton = binding.filterListAdd;

        inputLayout.setHint(inputHint);
        emptyView.setText(emptyMessage);
        renderItems();

        addButton.setOnClickListener(view -> addCurrentInput());
        inputEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addCurrentInput();
                return true;
            }
            return false;
        });

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(binding.getRoot())
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(dialogInterface -> inputEditText.requestFocus());
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_ITEMS, new ArrayList<>(items));
    }

    @Override
    public void onDestroyView() {
        if (listContainer != null) {
            listContainer.removeAllViews();
        }
        if (inputEditText != null) {
            inputEditText.setOnEditorActionListener(null);
        }

        listContainer = null;
        emptyView = null;
        inputLayout = null;
        inputEditText = null;

        super.onDestroyView();
    }

    private boolean addCurrentInput() {
        inputLayout.setError(null);
        List<String> newItems = parseItems(String.valueOf(inputEditText.getText()));
        if (newItems.isEmpty()) {
            inputLayout.setError("Enter a value");
            return false;
        }

        boolean added = false;
        for (String item : newItems) {
            if (containsItem(item)) {
                continue;
            }
            items.add(item);
            addItemView(item);
            added = true;
        }

        if (!added) {
            inputLayout.setError("Already added");
            return false;
        }

        inputEditText.setText("");
        updateEmptyState();
        saveItems();
        return true;
    }

    private boolean containsItem(String item) {
        for (String existing : items) {
            if (existing.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    private void renderItems() {
        listContainer.removeAllViews();
        for (String item : items) {
            addItemView(item);
        }
        updateEmptyState();
    }

    private void addItemView(String item) {
        FilterListItemBinding binding =
                FilterListItemBinding.inflate(getLayoutInflater(), listContainer, false);
        View row = binding.getRoot();
        binding.filterListItemText.setText(item);
        binding.filterListItemRemove.setContentDescription("Remove " + item);
        binding.filterListItemRemove.setOnClickListener(view -> removeItem(row, item));

        listContainer.addView(row);
    }

    private void removeItem(View row, String item) {
        items.remove(item);
        listContainer.removeView(row);
        updateEmptyState();
        saveItems();
    }

    private void updateEmptyState() {
        if (emptyView == null) {
            return;
        }
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void saveItems() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(preferenceKey, TextUtils.join(",", items))
                .apply();
    }

    private static ArrayList<String> parseItems(String value) {
        ArrayList<String> parsedItems = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return parsedItems;
        }

        String[] parts = value.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!TextUtils.isEmpty(trimmed) && !containsItem(parsedItems, trimmed)) {
                parsedItems.add(trimmed);
            }
        }
        return parsedItems;
    }

    private static boolean containsItem(List<String> values, String item) {
        for (String value : values) {
            if (value.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }
}
