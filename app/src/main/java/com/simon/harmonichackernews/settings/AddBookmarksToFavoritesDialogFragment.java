package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.databinding.AddBookmarksToFavoritesDialogBinding;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.Utils;

import okhttp3.Response;

public class AddBookmarksToFavoritesDialogFragment extends DialogFragment {

    private static final String ARG_BOOKMARK_IDS = "bookmark_ids";
    private static final long NEXT_FAVORITE_DELAY_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearProgressIndicator progressIndicator;
    private TextView statusView;
    private int[] bookmarkIds = new int[0];
    private int currentIndex = 0;
    private int addedCount = 0;
    private int failedCount = 0;
    private boolean started = false;
    private boolean cancelled = false;

    public static AddBookmarksToFavoritesDialogFragment newInstance(int[] bookmarkIds) {
        AddBookmarksToFavoritesDialogFragment fragment = new AddBookmarksToFavoritesDialogFragment();
        Bundle args = new Bundle();
        args.putIntArray(ARG_BOOKMARK_IDS, bookmarkIds);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            int[] ids = args.getIntArray(ARG_BOOKMARK_IDS);
            if (ids != null) {
                bookmarkIds = ids;
            }
        }

        AddBookmarksToFavoritesDialogBinding binding =
                AddBookmarksToFavoritesDialogBinding.inflate(getLayoutInflater());
        progressIndicator = binding.addBookmarksToFavoritesProgress;
        statusView = binding.addBookmarksToFavoritesStatus;

        progressIndicator.setMax(Math.max(bookmarkIds.length, 1));
        updateProgress();

        Dialog alertDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Adding favorites")
                .setView(binding.getRoot())
                .setNegativeButton("Cancel", (dialog, which) -> cancelled = true)
                .create();
        alertDialog.setCanceledOnTouchOutside(false);
        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!started) {
            started = true;
            addNextFavorite();
        }
    }

    private void addNextFavorite() {
        Context ctx = getContext();
        if (cancelled || ctx == null) {
            return;
        }

        if (currentIndex >= bookmarkIds.length) {
            finish();
            return;
        }

        int bookmarkId = bookmarkIds[currentIndex];
        UserActions.setFavorite(ctx, bookmarkId, true, getParentFragmentManager(), new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                response.close();
                Utils.setFavorite(ctx, bookmarkId, true);
                addedCount++;
                onFavoriteFinished();
            }

            @Override
            public void onFailure(String summary, String response) {
                failedCount++;
                onFavoriteFinished();
            }
        });
    }

    private void onFavoriteFinished() {
        if (cancelled) {
            return;
        }

        currentIndex++;
        updateProgress();

        if (currentIndex >= bookmarkIds.length) {
            finish();
        } else {
            handler.postDelayed(this::addNextFavorite, NEXT_FAVORITE_DELAY_MS);
        }
    }

    private void updateProgress() {
        if (progressIndicator == null || statusView == null) {
            return;
        }

        progressIndicator.setProgressCompat(currentIndex, true);
        statusView.setText(currentIndex + " of " + bookmarkIds.length + " complete");
    }

    private void finish() {
        Context ctx = getContext();
        if (ctx != null && !cancelled) {
            if (failedCount == 0) {
                Toast.makeText(ctx, "Added " + formatFavoriteCount(addedCount), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "Added " + addedCount + " of " + bookmarkIds.length + " favorites", Toast.LENGTH_SHORT).show();
            }
        }
        dismissAllowingStateLoss();
    }

    private static String formatFavoriteCount(int count) {
        return count == 1 ? "1 favorite" : count + " favorites";
    }

    @Override
    public void onDestroyView() {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        progressIndicator = null;
        statusView = null;
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        super.onDismiss(dialog);
    }
}
