package com.simon.harmonichackernews.settings;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.AddBookmarksToFavoritesDialogBinding;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.Utils;

import okhttp3.Response;

public class AddBookmarksToFavoritesDialogFragment extends DialogFragment {

    private static final String ARG_BOOKMARK_IDS = "bookmark_ids";
    private static final long NEXT_FAVORITE_DELAY_MS = 1000;
    private static final long TRANSFER_ANIMATION_DURATION_MS = 1900;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearProgressIndicator progressIndicator;
    private TextView statusView;
    private View bookmarkTarget;
    private View favoriteTarget;
    private ImageView storyView;
    private ValueAnimator transferAnimator;
    private int[] bookmarkIds = new int[0];
    private String[] bookmarkTitles = new String[0];
    private boolean[] successfulResults = new boolean[0];
    private String[] resultMessages = new String[0];
    private int currentIndex = 0;
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
        bookmarkTarget = binding.addBookmarksToFavoritesBookmarkTarget;
        favoriteTarget = binding.addBookmarksToFavoritesFavoriteTarget;
        storyView = binding.addBookmarksToFavoritesStory;

        bookmarkTitles = resolveBookmarkTitles(requireContext(), bookmarkIds);
        successfulResults = new boolean[bookmarkIds.length];
        resultMessages = new String[bookmarkIds.length];

        progressIndicator.setMax(Math.max(bookmarkIds.length, 1));
        updateProgress();

        Dialog alertDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Adding bookmarks to HN favorites")
                .setView(binding.getRoot())
                .setNegativeButton("Cancel", (dialog, which) -> cancelled = true)
                .create();
        alertDialog.setCanceledOnTouchOutside(false);
        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        startTransferAnimation();
        if (!started) {
            started = true;
            addNextFavorite();
        }
    }

    @Override
    public void onStop() {
        stopTransferAnimation();
        super.onStop();
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

        int resultIndex = currentIndex;
        int bookmarkId = bookmarkIds[resultIndex];
        statusView.setText("Adding bookmark " + (resultIndex + 1) + " of " + bookmarkIds.length);
        UserActions.setFavorite(ctx, bookmarkId, true, getParentFragmentManager(), new UserActions.ActionCallback() {
            @Override
            public void onItemTitleLoaded(int itemId, String title) {
                if (itemId == bookmarkId && !TextUtils.isEmpty(title)) {
                    bookmarkTitles[resultIndex] = title;
                }
            }

            @Override
            public void onSuccess(Response response) {
                response.close();
                Utils.setFavorite(ctx, bookmarkId, true);
                successfulResults[resultIndex] = true;
                resultMessages[resultIndex] = "In HN favorites";
                onFavoriteFinished();
            }

            @Override
            public void onFailure(String summary, String response) {
                successfulResults[resultIndex] = false;
                resultMessages[resultIndex] = formatFailure(summary, response);
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
        if (bookmarkIds.length == 0) {
            statusView.setText("No bookmarks to add");
        } else {
            statusView.setText(currentIndex + " of " + bookmarkIds.length + " complete");
        }
    }

    private void finish() {
        if (cancelled) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();
        AddBookmarksToFavoritesResultsDialogFragment resultsDialog =
                AddBookmarksToFavoritesResultsDialogFragment.newInstance(
                        bookmarkIds,
                        bookmarkTitles,
                        successfulResults,
                        resultMessages);
        dismissAllowingStateLoss();
        if (!fragmentManager.isStateSaved()) {
            resultsDialog.show(fragmentManager, "AddBookmarksToFavoritesResultsDialogFragment");
        }
    }

    private void startTransferAnimation() {
        if (storyView == null || bookmarkTarget == null || favoriteTarget == null) {
            return;
        }

        storyView.post(() -> {
            if (storyView == null || bookmarkTarget == null || favoriteTarget == null) {
                return;
            }

            stopTransferAnimation();

            final float startX = bookmarkTarget.getX()
                    + (bookmarkTarget.getWidth() - storyView.getWidth()) / 2f;
            final float endX = favoriteTarget.getX()
                    + (favoriteTarget.getWidth() - storyView.getWidth()) / 2f;
            final float startY = bookmarkTarget.getY()
                    + (bookmarkTarget.getHeight() - storyView.getHeight()) / 2f;
            final float endY = favoriteTarget.getY()
                    + (favoriteTarget.getHeight() - storyView.getHeight()) / 2f;
            final float controlX = (startX + endX) / 2f;
            final float controlY = Math.min(startY, endY)
                    - Utils.pxFromDpInt(getResources(), 34);

            transferAnimator = ValueAnimator.ofFloat(0f, 1f);
            transferAnimator.setDuration(TRANSFER_ANIMATION_DURATION_MS);
            transferAnimator.setStartDelay(180);
            transferAnimator.setRepeatCount(ValueAnimator.INFINITE);
            transferAnimator.setInterpolator(new LinearInterpolator());
            transferAnimator.addUpdateListener(animation -> {
                if (storyView == null || favoriteTarget == null) {
                    return;
                }

                float progress = (float) animation.getAnimatedValue();
                float remaining = 1f - progress;
                storyView.setX(remaining * remaining * startX
                        + 2f * remaining * progress * controlX
                        + progress * progress * endX);
                storyView.setY(remaining * remaining * startY
                        + 2f * remaining * progress * controlY
                        + progress * progress * endY);

                float alpha;
                if (progress < 0.16f) {
                    alpha = progress / 0.16f;
                } else if (progress > 0.82f) {
                    alpha = (1f - progress) / 0.18f;
                } else {
                    alpha = 1f;
                }
                storyView.setAlpha(Math.max(0f, Math.min(1f, alpha)));

                float storyScale = 0.86f + 0.14f * (float) Math.sin(Math.PI * progress);
                storyView.setScaleX(storyScale);
                storyView.setScaleY(storyScale);

                float arrival = Math.max(0f, 1f - Math.abs(progress - 0.88f) / 0.12f);
                float targetScale = 1f + 0.08f * arrival;
                favoriteTarget.setScaleX(targetScale);
                favoriteTarget.setScaleY(targetScale);
            });
            transferAnimator.start();
        });
    }

    private void stopTransferAnimation() {
        if (transferAnimator != null) {
            transferAnimator.cancel();
            transferAnimator.removeAllUpdateListeners();
            transferAnimator = null;
        }
        if (storyView != null) {
            storyView.setAlpha(0f);
        }
        if (favoriteTarget != null) {
            favoriteTarget.setScaleX(1f);
            favoriteTarget.setScaleY(1f);
        }
    }

    private static String[] resolveBookmarkTitles(Context context, int[] bookmarkIds) {
        String[] titles = new String[bookmarkIds.length];
        for (int i = 0; i < bookmarkIds.length; i++) {
            Story story = new Story();
            story.id = bookmarkIds[i];
            if (Utils.loadCachedStorySummary(context, story) && !TextUtils.isEmpty(story.title)) {
                titles[i] = story.title;
            } else {
                titles[i] = "Story #" + bookmarkIds[i];
            }
        }
        return titles;
    }

    private static String formatFailure(String summary, String response) {
        String safeSummary = TextUtils.isEmpty(summary) ? "Couldn't add to favorites" : summary.trim();
        String safeResponse = TextUtils.isEmpty(response) ? "" : response.trim();
        if (safeResponse.isEmpty() || safeResponse.equals(safeSummary)) {
            return safeSummary;
        }

        safeResponse = safeResponse.replace('\n', ' ').replaceAll("\\s+", " ");
        if (safeResponse.length() > 160) {
            safeResponse = safeResponse.substring(0, 157) + "…";
        }
        return safeSummary + ": " + safeResponse;
    }

    @Override
    public void onDestroyView() {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        stopTransferAnimation();
        progressIndicator = null;
        statusView = null;
        bookmarkTarget = null;
        favoriteTarget = null;
        storyView = null;
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        stopTransferAnimation();
        super.onDismiss(dialog);
    }
}
