package com.simon.harmonichackernews;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.FoldableSplitInitializer;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class MainActivity extends BaseActivity implements StoriesFragment.StoryClickListener {

    public static ArrayList<CommentsScrollProgress> commentsScrollProgresses = new ArrayList<>();
    private static final Set<SearchBackStateListener> searchBackStateListeners =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static MainActivity currentMainActivity;

    int lastPosition = 0;
    public OnBackPressedCallback backPressedCallback;
    private boolean searchBackEnabled = false;

    public int bottom = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentMainActivity = this;

        if (Utils.isFirstAppStart(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        }

        ThemeUtils.setupTheme(this);

        setContentView(R.layout.activity_main);

        updateFragmentLayout();

        if (Utils.justUpdated(this) && SettingsUtils.shouldShowChangelog(this)) {
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
    protected void onDestroy() {
        if (currentMainActivity == this) {
            currentMainActivity = null;
            searchBackEnabled = false;
            notifySearchBackStateListeners(false);
        }
        super.onDestroy();
    }

    public void setSearchBackEnabled(boolean enabled) {
        searchBackEnabled = enabled;
        backPressedCallback.setEnabled(enabled);
        notifySearchBackStateListeners(enabled);
    }

    public static boolean isSearchBackActive() {
        return currentMainActivity != null && currentMainActivity.searchBackEnabled;
    }

    public static void addSearchBackStateListener(SearchBackStateListener listener) {
        searchBackStateListeners.add(listener);
        listener.onSearchBackStateChanged(isSearchBackActive());
    }

    public static void removeSearchBackStateListener(SearchBackStateListener listener) {
        searchBackStateListeners.remove(listener);
    }

    public static void startActiveSearchBackProgress(float progress) {
        if (currentMainActivity != null) {
            currentMainActivity.startSearchBackProgress(progress);
        }
    }

    public static void updateActiveSearchBackProgress(float progress) {
        if (currentMainActivity != null) {
            currentMainActivity.updateSearchBackProgress(progress);
        }
    }

    public static void cancelActiveSearchBackProgress() {
        if (currentMainActivity != null) {
            currentMainActivity.cancelSearchBackProgress();
        }
    }

    public static boolean finishActiveSearchBackProgress() {
        return currentMainActivity != null && currentMainActivity.finishSearchBackProgress();
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

    private static void notifySearchBackStateListeners(boolean enabled) {
        for (SearchBackStateListener listener : searchBackStateListeners) {
            listener.onSearchBackStateChanged(enabled);
        }
    }

    public interface SearchBackStateListener {
        void onSearchBackStateChanged(boolean enabled);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateFragmentLayout();
    }

    @Override
    public void openStory(Story story, int pos, boolean showWebsite) {
        Bundle bundle = story.toBundle();

        bundle.putInt(CommentsFragment.EXTRA_FORWARD, pos - lastPosition);
        bundle.putBoolean(CommentsFragment.EXTRA_SHOW_WEBSITE, showWebsite);

        if (FoldableSplitInitializer.isFoldableSplitEnabled(this)) {
            bundle.putBoolean(CommentsActivity.PREVENT_BACK, true);
        }

        lastPosition = pos;

        if (Utils.isTablet(getResources())) {
            CommentsFragment fragment = new CommentsFragment();
            fragment.setArguments(bundle);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_comments_container, fragment);
            transaction.commit();
        } else {
            Intent intent = new Intent(MainActivity.this, CommentsActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);

            if (!SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext())) {
                overridePendingTransition(R.anim.activity_in_animation, R.anim.hold);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String volumeNavigationMode = SettingsUtils.getCommentsVolumeNavigationMode(getApplicationContext());
        if (!SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED.equals(volumeNavigationMode)) {
            boolean topLevelOnly = SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL.equals(volumeNavigationMode);
            CommentsFragment fragment = (CommentsFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.main_fragment_comments_container);
            if (fragment != null && fragment.isAdded()) {
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

    private void updateFragmentLayout() {
        if (Utils.isTablet(getResources()) && findViewById(R.id.main_fragments_container) instanceof LinearLayout) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getInteger(R.integer.stories_pane_weight));
            findViewById(R.id.main_fragment_stories_container).setLayoutParams(params);

            int extraPadding = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
            findViewById(R.id.main_fragments_container).setPadding(extraPadding, 0, 0, 0);
        }
    }

    public void onAccountStateChanged() {
        StoriesFragment fragment = (StoriesFragment) getSupportFragmentManager()
                .findFragmentById(R.id.main_fragment_stories_container);
        if (fragment != null) {
            fragment.onAccountStateChanged();
        }
    }

    private void showUpdateDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Changelog")
                .setMessage(Html.fromHtml(Changelog.getHTML()))
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
