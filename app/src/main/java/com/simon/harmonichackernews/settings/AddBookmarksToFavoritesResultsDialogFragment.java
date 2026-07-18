package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.AddBookmarksToFavoritesResultItemBinding;
import com.simon.harmonichackernews.databinding.AddBookmarksToFavoritesResultsDialogBinding;

public class AddBookmarksToFavoritesResultsDialogFragment extends DialogFragment {

    private static final String ARG_BOOKMARK_IDS = "bookmark_ids";
    private static final String ARG_BOOKMARK_TITLES = "bookmark_titles";
    private static final String ARG_SUCCESSFUL_RESULTS = "successful_results";
    private static final String ARG_RESULT_MESSAGES = "result_messages";

    public static AddBookmarksToFavoritesResultsDialogFragment newInstance(
            int[] bookmarkIds,
            String[] bookmarkTitles,
            boolean[] successfulResults,
            String[] resultMessages) {
        AddBookmarksToFavoritesResultsDialogFragment fragment =
                new AddBookmarksToFavoritesResultsDialogFragment();
        Bundle args = new Bundle();
        args.putIntArray(ARG_BOOKMARK_IDS, bookmarkIds);
        args.putStringArray(ARG_BOOKMARK_TITLES, bookmarkTitles);
        args.putBooleanArray(ARG_SUCCESSFUL_RESULTS, successfulResults);
        args.putStringArray(ARG_RESULT_MESSAGES, resultMessages);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        int[] bookmarkIds = getIntArray(args, ARG_BOOKMARK_IDS);
        String[] bookmarkTitles = getStringArray(args, ARG_BOOKMARK_TITLES);
        boolean[] successfulResults = getBooleanArray(args, ARG_SUCCESSFUL_RESULTS);
        String[] resultMessages = getStringArray(args, ARG_RESULT_MESSAGES);

        AddBookmarksToFavoritesResultsDialogBinding binding =
                AddBookmarksToFavoritesResultsDialogBinding.inflate(getLayoutInflater());
        populateResults(
                binding.addBookmarksToFavoritesResultsItems,
                bookmarkIds,
                bookmarkTitles,
                successfulResults,
                resultMessages);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Finished")
                .setView(binding.getRoot())
                .setPositiveButton("Done", null)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private void populateResults(
            LinearLayout container,
            int[] bookmarkIds,
            String[] bookmarkTitles,
            boolean[] successfulResults,
            String[] resultMessages) {
        int count = bookmarkIds.length;
        for (int i = 0; i < count; i++) {
            AddBookmarksToFavoritesResultItemBinding itemBinding =
                    AddBookmarksToFavoritesResultItemBinding.inflate(
                            getLayoutInflater(), container, false);
            boolean successful = i < successfulResults.length && successfulResults[i];
            String title = i < bookmarkTitles.length ? bookmarkTitles[i] : null;
            String message = i < resultMessages.length ? resultMessages[i] : null;

            itemBinding.addBookmarksToFavoritesResultTitle.setText(
                    TextUtils.isEmpty(title) ? "Story #" + bookmarkIds[i] : title);
            itemBinding.addBookmarksToFavoritesResultStatus.setText(
                    TextUtils.isEmpty(message)
                            ? (successful ? "In HN favorites" : "Couldn't add to favorites")
                            : message);
            itemBinding.addBookmarksToFavoritesResultIcon.setImageResource(
                    successful ? R.drawable.ic_check : R.drawable.ic_close);
            int tint = MaterialColors.getColor(
                    itemBinding.getRoot(),
                    successful
                            ? androidx.appcompat.R.attr.colorPrimary
                            : androidx.appcompat.R.attr.colorError);
            ImageViewCompat.setImageTintList(
                    itemBinding.addBookmarksToFavoritesResultIcon,
                    ColorStateList.valueOf(tint));

            container.addView(itemBinding.getRoot());
            if (i < count - 1) {
                addDivider(container);
            }
        }
    }

    private void addDivider(LinearLayout container) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(MaterialColors.getColor(
                container,
                com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(getResources().getDisplayMetrics().density)));
        params.setMarginStart(Math.round(38 * getResources().getDisplayMetrics().density));
        container.addView(divider, params);
    }

    private static int[] getIntArray(Bundle args, String key) {
        int[] values = args.getIntArray(key);
        return values == null ? new int[0] : values;
    }

    private static String[] getStringArray(Bundle args, String key) {
        String[] values = args.getStringArray(key);
        return values == null ? new String[0] : values;
    }

    private static boolean[] getBooleanArray(Bundle args, String key) {
        boolean[] values = args.getBooleanArray(key);
        return values == null ? new boolean[0] : values;
    }
}
