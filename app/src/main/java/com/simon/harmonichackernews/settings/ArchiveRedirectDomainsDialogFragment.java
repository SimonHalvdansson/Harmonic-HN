package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.ArrayList;
import java.util.List;

public class ArchiveRedirectDomainsDialogFragment extends AppCompatDialogFragment {

    private static final String TAG = "archive_redirect_domains_dialog";
    private static final String STATE_DOMAINS = "domains";
    private static final String[] SUGGESTED_DOMAINS = new String[]{
            "ft.com",
            "wsj.com",
            "bloomberg.com",
            "economist.com",
            "foreignpolicy.com",
            "nytimes.com",
            "washingtonpost.com",
            "theatlantic.com",
            "newyorker.com",
            "technologyreview.com"
    };

    private final ArrayList<String> domains = new ArrayList<>();
    private LinearLayout listContainer;
    private TextView emptyView;
    private TextInputLayout inputLayout;
    private TextInputEditText inputEditText;
    private LinearLayout suggestionsSection;
    private ChipGroup suggestionsGroup;

    public static void show(androidx.fragment.app.FragmentManager fm) {
        new ArchiveRedirectDomainsDialogFragment().show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        if (savedInstanceState != null) {
            ArrayList<String> savedDomains = savedInstanceState.getStringArrayList(STATE_DOMAINS);
            if (savedDomains != null) {
                domains.clear();
                domains.addAll(savedDomains);
            }
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            domains.clear();
            domains.addAll(SettingsUtils.parseArchiveRedirectDomains(
                    prefs.getString(SettingsUtils.PREF_ARCHIVE_REDIRECT_DOMAINS, "")));
        }

        View rootView = LayoutInflater.from(context).inflate(R.layout.archive_redirect_domains_dialog, null, false);
        listContainer = rootView.findViewById(R.id.archive_redirect_items);
        emptyView = rootView.findViewById(R.id.archive_redirect_empty);
        inputLayout = rootView.findViewById(R.id.archive_redirect_input_layout);
        inputEditText = rootView.findViewById(R.id.archive_redirect_input);
        suggestionsSection = rootView.findViewById(R.id.archive_redirect_suggestions_section);
        suggestionsGroup = rootView.findViewById(R.id.archive_redirect_suggestions);
        MaterialButton addButton = rootView.findViewById(R.id.archive_redirect_add);

        renderDomains();
        renderSuggestions();

        addButton.setOnClickListener(view -> addCurrentInput());
        inputEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addCurrentInput();
                return true;
            }
            return false;
        });

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Redirect to archive version")
                .setView(rootView)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(dialogInterface -> inputEditText.requestFocus());
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_DOMAINS, new ArrayList<>(domains));
    }

    @Override
    public void onDestroyView() {
        if (listContainer != null) {
            listContainer.removeAllViews();
        }
        if (suggestionsGroup != null) {
            suggestionsGroup.removeAllViews();
        }
        if (inputEditText != null) {
            inputEditText.setOnEditorActionListener(null);
        }

        listContainer = null;
        emptyView = null;
        inputLayout = null;
        inputEditText = null;
        suggestionsSection = null;
        suggestionsGroup = null;

        super.onDestroyView();
    }

    private boolean addCurrentInput() {
        inputLayout.setError(null);
        List<String> newDomains = SettingsUtils.parseArchiveRedirectDomains(String.valueOf(inputEditText.getText()));
        if (newDomains.isEmpty()) {
            inputLayout.setError("Enter a domain");
            return false;
        }

        boolean added = false;
        for (String domain : newDomains) {
            if (addDomain(domain)) {
                added = true;
            }
        }

        if (!added) {
            inputLayout.setError("Already added");
            return false;
        }

        inputEditText.setText("");
        return true;
    }

    private boolean addDomain(String domain) {
        if (containsDomain(domain)) {
            return false;
        }

        domains.add(domain);
        addDomainView(domain);
        updateEmptyState();
        renderSuggestions();
        saveDomains();
        return true;
    }

    private boolean containsDomain(String domain) {
        for (String existing : domains) {
            if (existing.equalsIgnoreCase(domain)) {
                return true;
            }
        }
        return false;
    }

    private void renderDomains() {
        listContainer.removeAllViews();
        for (String domain : domains) {
            addDomainView(domain);
        }
        updateEmptyState();
    }

    private void addDomainView(String domain) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.archive_redirect_domain_item, listContainer, false);
        TextView textView = row.findViewById(R.id.filter_list_item_text);
        ImageButton removeButton = row.findViewById(R.id.filter_list_item_remove);
        textView.setText(domain);
        removeButton.setContentDescription("Remove " + domain);
        removeButton.setOnClickListener(view -> removeDomain(row, domain));

        listContainer.addView(row);
    }

    private void removeDomain(View row, String domain) {
        domains.remove(domain);
        listContainer.removeView(row);
        updateEmptyState();
        renderSuggestions();
        saveDomains();
    }

    private void updateEmptyState() {
        if (emptyView == null) {
            return;
        }
        emptyView.setVisibility(domains.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderSuggestions() {
        if (suggestionsSection == null || suggestionsGroup == null) {
            return;
        }

        suggestionsGroup.removeAllViews();
        List<String> remainingSuggestions = new ArrayList<>();
        for (String suggestion : SUGGESTED_DOMAINS) {
            if (!containsDomain(suggestion)) {
                remainingSuggestions.add(suggestion);
            }
        }

        suggestionsSection.setVisibility(remainingSuggestions.isEmpty() ? View.GONE : View.VISIBLE);
        for (String suggestion : remainingSuggestions) {
            suggestionsGroup.addView(createSuggestionChip(suggestion));
        }
    }

    private Chip createSuggestionChip(String domain) {
        Chip chip = new Chip(requireContext());
        chip.setText(domain);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription("Add " + domain);
        chip.setOnClickListener(view -> {
            inputLayout.setError(null);
            addDomain(domain);
        });
        return chip;
    }

    private void saveDomains() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(SettingsUtils.PREF_ARCHIVE_REDIRECT_DOMAINS, TextUtils.join(",", domains))
                .apply();
    }
}
