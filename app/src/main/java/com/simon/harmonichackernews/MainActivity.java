package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.ActivityMainBinding;
import com.simon.harmonichackernews.databinding.ActivityMainFoldableBinding;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MainActivity extends BaseActivity implements StoriesFragment.StoryClickListener {

    /**
     * The window width from which foldables show the stories and comments panes side by side,
     * matching the threshold the activity embedding split rules previously used.
     */
    private static final int FOLDABLE_TWO_PANE_MIN_WIDTH_DP = 700;
    private static final String STATE_COMPACT_PANE_SHOWS_COMMENTS = "compact_pane_shows_comments";

    public static ArrayList<CommentsScrollProgress> commentsScrollProgresses = new ArrayList<>();
    private static WeakReference<MainActivity> currentMainActivity = new WeakReference<>(null);

    int lastPosition = 0;
    public OnBackPressedCallback backPressedCallback;
    private View mainFragmentsContainer;
    private View mainFragmentStoriesContainer;
    private View mainFragmentCommentsContainer;
    /** Whether the comments pane is the one showing when only one pane fits (folded foldable). */
    private boolean compactPaneShowsComments = false;
    private OnBackPressedCallback compactCommentsBackCallback;

    public int bottom = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentMainActivity = new WeakReference<>(this);

        ThemeUtils.setupTheme(this);

        if (Utils.isFoldableDevice(this)) {
            ActivityMainFoldableBinding binding = ActivityMainFoldableBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            mainFragmentsContainer = binding.mainFragmentsContainer;
            mainFragmentStoriesContainer = binding.mainFragmentStoriesContainer;
            mainFragmentCommentsContainer = binding.mainFragmentCommentsContainer;
        } else {
            ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            mainFragmentsContainer = binding.mainFragmentsContainer;
            mainFragmentStoriesContainer = binding.mainFragmentStoriesContainer;
            mainFragmentCommentsContainer = binding.mainFragmentCommentsContainer;
        }

        if (savedInstanceState != null) {
            compactPaneShowsComments =
                    savedInstanceState.getBoolean(STATE_COMPACT_PANE_SHOWS_COMMENTS, false);
        }

        // Closes the comments pane when only one pane fits. Registered before the fragments and
        // the search callback below so that back handling inside them takes precedence.
        compactCommentsBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                compactPaneShowsComments = false;
                updatePaneLayout();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, compactCommentsBackCallback);

        updatePaneLayout();
        setupSplitDividerHandle();
        removeUnavailableCommentsPaneFragment();

        boolean shouldShowWelcomeDialog = Utils.shouldShowWelcomeDialog(this);
        boolean justUpdated = Utils.justUpdated(this);
        if (shouldShowWelcomeDialog) {
            showWelcomeDialog();
        } else if (justUpdated && SettingsUtils.shouldShowChangelog(this)) {
            showUpdateDialog();
        }

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackCancelled() {
                cancelSearchBackProgress();
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                updateSearchBackProgress(backEvent.getProgress());
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                startSearchBackProgress(backEvent.getProgress());
            }

            @Override
            public void handleOnBackPressed() {
                finishSearchBackProgress();
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        setSearchBackEnabled(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_COMPACT_PANE_SHOWS_COMMENTS, compactPaneShowsComments);
    }

    @Override
    protected void onDestroy() {
        if (getCurrentMainActivity() == this) {
            currentMainActivity.clear();
        }
        super.onDestroy();
    }

    public void setSearchBackEnabled(boolean enabled) {
        backPressedCallback.setEnabled(enabled);
    }

    public static void applyWelcomePresetToActiveUi() {
        MainActivity activity = getCurrentMainActivity();
        if (activity != null) {
            activity.applyWelcomePresetToUi();
        }
    }

    private static MainActivity getCurrentMainActivity() {
        return currentMainActivity.get();
    }

    private void applyWelcomePresetToUi() {
        final StoriesFragment fragment = getStoriesFragment();

        if (fragment != null) {
            fragment.applyWelcomePresetSettings();
        }
    }

    private void startSearchBackProgress(float progress) {
        final StoriesFragment fragment = getStoriesFragment();

        if (fragment != null) {
            fragment.startSearchBackProgress(progress);
        }
    }

    private void updateSearchBackProgress(float progress) {
        final StoriesFragment fragment = getStoriesFragment();

        if (fragment != null) {
            fragment.updateSearchBackProgress(progress);
        }
    }

    private void cancelSearchBackProgress() {
        final StoriesFragment fragment = getStoriesFragment();

        if (fragment != null) {
            fragment.cancelSearchBackProgress();
        }
    }

    private boolean finishSearchBackProgress() {
        final StoriesFragment fragment = getStoriesFragment();

        return fragment != null && fragment.finishSearchBackProgress();
    }

    private StoriesFragment getStoriesFragment() {
        return (StoriesFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment_stories_container);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Folding or unfolding the device lands here; the window width decides the pane layout
        updatePaneLayout();
    }

    @Override
    public void openStory(Story story, int pos, boolean showWebsite) {
        if (switchOpenStoryViewIfMatching(story, showWebsite)) {
            lastPosition = pos;
            return;
        }

        Bundle bundle = story.toBundle();

        bundle.putInt(CommentsFragment.EXTRA_FORWARD, pos - lastPosition);
        bundle.putBoolean(CommentsFragment.EXTRA_SHOW_WEBSITE, showWebsite);

        lastPosition = pos;

        if (shouldOpenCommentsInMainPane()) {
            CommentsFragment fragment = new CommentsFragment();
            fragment.setArguments(bundle);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_comments_container, fragment);
            transaction.commit();

            compactPaneShowsComments = true;
            updatePaneLayout();
        } else {
            Intent intent = new Intent(MainActivity.this, CommentsActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);

            if (!SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext())) {
                overridePendingTransition(R.anim.activity_in_animation, R.anim.hold);
            }
        }
    }

    private boolean switchOpenStoryViewIfMatching(Story story, boolean showWebsite) {
        if (story == null || !shouldOpenCommentsInMainPane()) {
            return false;
        }

        CommentsFragment fragment = (CommentsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.main_fragment_comments_container);
        if (fragment == null || !fragment.switchStoryViewIfMatching(story.id, showWebsite)) {
            return false;
        }

        // When only one pane fits, the story may already be open while the stories list is showing
        compactPaneShowsComments = true;
        updatePaneLayout();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String volumeNavigationMode = SettingsUtils.getCommentsVolumeNavigationMode(getApplicationContext());
        if (!SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED.equals(volumeNavigationMode)) {
            boolean topLevelOnly = SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL.equals(volumeNavigationMode);
            CommentsFragment fragment = (CommentsFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.main_fragment_comments_container);
            if (fragment != null && fragment.isAdded() && fragment.isBottomSheetFullyExpanded()) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    fragment.navigateToNextComment(topLevelOnly, true);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    fragment.navigateToPreviousComment(topLevelOnly, true);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Lays the panes out for the current window: side by side when two panes fit, otherwise the
     * stories list or the comments pane alone, whichever the user is at. Called again whenever
     * the window size changes, which is how folding and unfolding the device is handled.
     */
    private void updatePaneLayout() {
        if (!shouldOpenCommentsInMainPane()) {
            return;
        }

        View divider = findViewById(R.id.main_split_divider);
        boolean twoPane = isTwoPaneWidth();

        if (twoPane) {
            mainFragmentStoriesContainer.setVisibility(View.VISIBLE);
            if (mainFragmentCommentsContainer != null) {
                mainFragmentCommentsContainer.setVisibility(View.VISIBLE);
            }
            if (divider != null) {
                divider.setVisibility(View.VISIBLE);
            }
        } else {
            mainFragmentStoriesContainer.setVisibility(
                    compactPaneShowsComments ? View.GONE : View.VISIBLE);
            if (mainFragmentCommentsContainer != null) {
                mainFragmentCommentsContainer.setVisibility(
                        compactPaneShowsComments ? View.VISIBLE : View.GONE);
            }
            if (divider != null) {
                divider.setVisibility(View.GONE);
            }
        }

        // With one pane gone, the weight of the remaining pane makes it fill the window
        applyPaneWeights(SettingsUtils.getSplitPaneRatio(this));
        mainFragmentsContainer.setPadding(0, 0, 0, 0);

        if (compactCommentsBackCallback != null) {
            compactCommentsBackCallback.setEnabled(!twoPane && compactPaneShowsComments);
        }
    }

    /** Whether the current window fits the stories and comments panes side by side. */
    private boolean isTwoPaneWidth() {
        if (!Utils.isFoldableDevice(this)) {
            // Tablets which use the two pane layout always show both panes
            return true;
        }
        return getResources().getConfiguration().screenWidthDp >= FOLDABLE_TWO_PANE_MIN_WIDTH_DP;
    }

    /**
     * Sizes the two panes so that the stories list takes {@code storiesRatio} percent of the
     * width, minus the divider between them, and the comments pane the rest.
     */
    private void applyPaneWeights(int storiesRatio) {
        mainFragmentStoriesContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                storiesRatio));

        if (mainFragmentCommentsContainer != null) {
            mainFragmentCommentsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    100 - storiesRatio));
        }
    }

    /**
     * Lets the divider between the two panes be dragged, resizing the panes as it moves. The
     * ratio it is dropped at is remembered.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupSplitDividerHandle() {
        View divider = findViewById(R.id.main_split_divider);
        if (divider == null) {
            return;
        }

        if (!shouldOpenCommentsInMainPane()) {
            divider.setVisibility(View.GONE);
            return;
        }

        divider.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_MOVE:
                    applyPaneWeights(dragRatioFromTouch(event));
                    return true;
                case MotionEvent.ACTION_UP:
                    SettingsUtils.setSplitPaneRatio(this, dragRatioFromTouch(event));
                    applyPaneWeights(SettingsUtils.getSplitPaneRatio(this));
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    applyPaneWeights(SettingsUtils.getSplitPaneRatio(this));
                    return true;
                default:
                    return false;
            }
        });
    }

    private int dragRatioFromTouch(MotionEvent event) {
        int[] containerLocation = new int[2];
        mainFragmentsContainer.getLocationOnScreen(containerLocation);

        int width = mainFragmentsContainer.getWidth();
        if (width == 0) {
            return SettingsUtils.getSplitPaneRatio(this);
        }

        float fraction = (event.getRawX() - containerLocation[0]) / width;
        if (mainFragmentsContainer.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            fraction = 1f - fraction;
        }

        return SettingsUtils.clampSplitPaneRatio(Math.round(fraction * 100));
    }

    /**
     * Whether comments open as a fragment beside (or in place of) the stories list instead of in
     * a separate activity. True whenever a two pane capable layout was inflated: tablets and
     * foldables.
     */
    private boolean shouldOpenCommentsInMainPane() {
        return mainFragmentsContainer instanceof LinearLayout;
    }

    private void removeUnavailableCommentsPaneFragment() {
        if (shouldOpenCommentsInMainPane()) {
            return;
        }

        Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.main_fragment_comments_container);
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss();
        }
    }

    public void onAccountStateChanged() {
        StoriesFragment fragment = (StoriesFragment) getSupportFragmentManager()
                .findFragmentById(R.id.main_fragment_stories_container);
        if (fragment != null) {
            fragment.onAccountStateChanged();
        }
    }

    private void showWelcomeDialog() {
        WelcomeDialogFragment.show(getSupportFragmentManager());
    }

    private void showUpdateDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Changelog")
                .setMessage(Changelog.getFormatted(this))
                .setNeutralButton("GitHub", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String url = "https://github.com/SimonHalvdansson/Harmonic-HN";
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Done", null).create();

        dialog.show();
    }
}
