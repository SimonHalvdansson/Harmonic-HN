package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback;
import com.simon.harmonichackernews.ui.comments.CommentsComposeController;
import com.simon.harmonichackernews.ui.navigation.MainNavigationController;
import com.simon.harmonichackernews.ui.navigation.MainNavigationHost;
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract;
import com.simon.harmonichackernews.ui.debug.CoulombGasContract;
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract;
import com.simon.harmonichackernews.ui.stories.StoriesComposeController;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class MainActivity extends BaseActivity implements StoriesCoordinator.StoryClickListener,
        CommentsCoordinator.CommentsPaneCallback {

    public static final String ACTION_OPEN_SETTINGS =
            "com.simon.harmonichackernews.action.OPEN_SETTINGS";
    public static final String EXTRA_SETTINGS_SECTION =
            "com.simon.harmonichackernews.extra.SETTINGS_SECTION";
    public static ArrayList<CommentsScrollProgress> commentsScrollProgresses = new ArrayList<>();
    private static final Set<SearchBackStateListener> searchBackStateListeners =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static WeakReference<MainActivity> currentMainActivity = new WeakReference<>(null);

    int lastPosition = 0;
    public OnBackPressedCallback backPressedCallback;
    private boolean searchBackEnabled = false;
    private MainNavigationController mainNavigationController;

    public int bottom = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentMainActivity = new WeakReference<>(this);

        ThemeUtils.setupTheme(this);

        mainNavigationController = MainNavigationHost.install(this, savedInstanceState);
        initializeStoriesCoordinator(savedInstanceState);
        // A singleTask can be recreated with saved navigation state while also receiving a new
        // deep link or feature intent. Always apply that launch intent after restoring state so
        // the newly requested destination wins.
        openCommentsFromIntent(getIntent());

        boolean shouldShowWelcomeDialog = Utils.shouldShowWelcomeDialog(this);
        boolean justUpdated = Utils.justUpdated(this);
        if (shouldShowWelcomeDialog) {
            mainNavigationController.showWelcomeDialog();
        } else if (justUpdated && SettingsUtils.shouldShowChangelog(this)) {
            mainNavigationController.showChangelogDialog();
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openCommentsFromIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        StoriesCoordinator storiesCoordinator = getStoriesCoordinator();
        if (storiesCoordinator != null) {
            storiesCoordinator.onSaveInstanceState(outState);
        }
        if (mainNavigationController != null) {
            mainNavigationController.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        StoriesCoordinator storiesCoordinator = getStoriesCoordinator();
        if (storiesCoordinator != null) {
            storiesCoordinator.onDestroy();
        }
        if (getCurrentMainActivity() == this) {
            currentMainActivity.clear();
            searchBackEnabled = false;
            notifySearchBackStateListeners(false);
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        StoriesCoordinator storiesCoordinator = getStoriesCoordinator();
        if (storiesCoordinator != null) storiesCoordinator.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        StoriesCoordinator storiesCoordinator = getStoriesCoordinator();
        if (storiesCoordinator != null) storiesCoordinator.onResume();
    }

    @Override
    protected void onStop() {
        StoriesCoordinator storiesCoordinator = getStoriesCoordinator();
        if (storiesCoordinator != null) storiesCoordinator.onStop();
        super.onStop();
    }

    public void setSearchBackEnabled(boolean enabled) {
        searchBackEnabled = enabled;
        backPressedCallback.setEnabled(enabled);
        notifySearchBackStateListeners(enabled);
    }

    public static boolean isSearchBackActive() {
        MainActivity activity = getCurrentMainActivity();
        return activity != null && activity.searchBackEnabled;
    }

    public static void addSearchBackStateListener(SearchBackStateListener listener) {
        searchBackStateListeners.add(listener);
        listener.onSearchBackStateChanged(isSearchBackActive());
    }

    public static void removeSearchBackStateListener(SearchBackStateListener listener) {
        searchBackStateListeners.remove(listener);
    }

    public static void startActiveSearchBackProgress(float progress) {
        MainActivity activity = getCurrentMainActivity();
        if (activity != null) {
            activity.startSearchBackProgress(progress);
        }
    }

    public static void updateActiveSearchBackProgress(float progress) {
        MainActivity activity = getCurrentMainActivity();
        if (activity != null) {
            activity.updateSearchBackProgress(progress);
        }
    }

    public static void cancelActiveSearchBackProgress() {
        MainActivity activity = getCurrentMainActivity();
        if (activity != null) {
            activity.cancelSearchBackProgress();
        }
    }

    public static boolean finishActiveSearchBackProgress() {
        MainActivity activity = getCurrentMainActivity();
        return activity != null && activity.finishSearchBackProgress();
    }

    public static void applyWelcomePresetToActiveUi() {
        MainActivity activity = getCurrentMainActivity();
        if (activity != null) {
            activity.applyWelcomePresetToUi();
        }
    }

    public static boolean showLoginPrompt() {
        MainActivity activity = getCurrentMainActivity();
        if (activity == null || activity.mainNavigationController == null) {
            return false;
        }
        activity.mainNavigationController.showLoginDialog();
        return true;
    }

    public void showCaptchaDialog(
            @NonNull UserActions.CaptchaChallenge challenge,
            @NonNull CaptchaResultCallback callback) {
        if (mainNavigationController == null) {
            callback.onCaptchaCancelled();
            return;
        }
        mainNavigationController.showCaptchaDialog(challenge, callback);
    }

    public void showUserDialog(@NonNull String userName, @Nullable Runnable onTagChanged) {
        if (mainNavigationController != null) {
            mainNavigationController.showUserDialog(userName, onTagChanged);
        }
    }

    private static MainActivity getCurrentMainActivity() {
        return currentMainActivity.get();
    }

    private void applyWelcomePresetToUi() {
        final StoriesCoordinator coordinator = getStoriesCoordinator();

        if (coordinator != null) {
            coordinator.applyWelcomePresetSettings();
        }
    }

    private void startSearchBackProgress(float progress) {
        final StoriesCoordinator coordinator = getStoriesCoordinator();

        if (coordinator != null) {
            coordinator.startSearchBackProgress(progress);
        }
    }

    private void updateSearchBackProgress(float progress) {
        final StoriesCoordinator coordinator = getStoriesCoordinator();

        if (coordinator != null) {
            coordinator.updateSearchBackProgress(progress);
        }
    }

    private void cancelSearchBackProgress() {
        final StoriesCoordinator coordinator = getStoriesCoordinator();

        if (coordinator != null) {
            coordinator.cancelSearchBackProgress();
        }
    }

    private boolean finishSearchBackProgress() {
        final StoriesCoordinator coordinator = getStoriesCoordinator();

        return coordinator != null && coordinator.finishSearchBackProgress();
    }

    private StoriesCoordinator getStoriesCoordinator() {
        return mainNavigationController == null ? null : mainNavigationController.getStoriesCoordinator();
    }

    private void initializeStoriesCoordinator(@Nullable Bundle savedInstanceState) {
        StoriesCoordinator coordinator = new StoriesCoordinator(this, savedInstanceState);
        mainNavigationController.attachStoriesCoordinator(coordinator);
        StoriesComposeController composeController = coordinator.getComposeController();
        if (composeController != null) {
            mainNavigationController.attachStoriesComposeController(composeController);
        }
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
    public void openStory(Story story, int pos, boolean showWebsite) {
        if (switchOpenStoryViewIfMatching(story, showWebsite)) {
            lastPosition = pos;
            return;
        }

        Bundle bundle = story.toBundle();

        bundle.putInt(CommentsContract.EXTRA_FORWARD, pos - lastPosition);
        bundle.putBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, showWebsite);

        lastPosition = pos;
        mainNavigationController.openStory(bundle);
    }

    private boolean switchOpenStoryViewIfMatching(Story story, boolean showWebsite) {
        if (story == null) {
            return false;
        }
        return mainNavigationController.switchOpenStoryViewIfMatching(story.id, showWebsite);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String volumeNavigationMode = SettingsUtils.getCommentsVolumeNavigationMode(getApplicationContext());
        if (!SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED.equals(volumeNavigationMode)) {
            boolean topLevelOnly = SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL.equals(volumeNavigationMode);
            CommentsCoordinator coordinator = getCommentsCoordinator();
            if (coordinator != null && coordinator.isAdded() && coordinator.isBottomSheetFullyExpanded()) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    coordinator.navigateToNextComment(topLevelOnly, true);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    coordinator.navigateToPreviousComment(topLevelOnly, true);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onAccountStateChanged() {
        StoriesCoordinator coordinator = getStoriesCoordinator();
        if (coordinator != null) {
            coordinator.onAccountStateChanged();
        }
    }

    public void closeStory() {
        mainNavigationController.closeStory();
    }

    public void showCacheStoriesDialog() {
        if (mainNavigationController != null) {
            mainNavigationController.showCacheStoriesDialog();
        }
    }

    public void attachStoriesComposeController(StoriesComposeController controller) {
        if (mainNavigationController != null) {
            mainNavigationController.attachStoriesComposeController(controller);
        }
    }

    public void detachStoriesComposeController(StoriesComposeController controller) {
        if (mainNavigationController != null) {
            mainNavigationController.detachStoriesComposeController(controller);
        }
    }

    public void attachCommentsComposeController(CommentsComposeController controller) {
        if (mainNavigationController != null) {
            mainNavigationController.attachCommentsComposeController(controller);
        }
    }

    public void detachCommentsComposeController(CommentsComposeController controller) {
        if (mainNavigationController != null) {
            mainNavigationController.detachCommentsComposeController(controller);
        }
    }

    public boolean openCommentsItem(int itemId) {
        return openCommentsItem(itemId, -1);
    }

    public boolean openCommentsItem(int itemId, int scrollToCommentId) {
        if (itemId <= 0) {
            return false;
        }
        Bundle bundle = new Bundle();
        bundle.putInt(CommentsContract.EXTRA_ID, itemId);
        bundle.putString(CommentsContract.EXTRA_TITLE, "");
        bundle.putBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, false);
        if (scrollToCommentId > 0) {
            bundle.putInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, scrollToCommentId);
        }
        mainNavigationController.openStory(bundle);
        return true;
    }

    private boolean openCommentsFromIntent(Intent intent) {
        if (intent == null || mainNavigationController == null) {
            return false;
        }

        if (ACTION_OPEN_SETTINGS.equals(intent.getAction())) {
            mainNavigationController.openSettings(
                    intent.getStringExtra(EXTRA_SETTINGS_SECTION));
            return true;
        }

        if (ComposeEditorContract.ACTION_OPEN_EDITOR.equals(intent.getAction())) {
            Bundle editorArguments = intent.getExtras() == null
                    ? new Bundle()
                    : new Bundle(intent.getExtras());
            int editorType = editorArguments.getInt(
                    ComposeEditorContract.EXTRA_TYPE,
                    ComposeEditorContract.TYPE_POST);
            if (editorType != ComposeEditorContract.TYPE_POST
                    && editorArguments.getInt(ComposeEditorContract.EXTRA_ID, -1) <= 0) {
                Toast.makeText(this, "Invalid comment id", Toast.LENGTH_SHORT).show();
                return false;
            }
            mainNavigationController.openEditor(editorArguments);
            return true;
        }

        if (SubmissionsContract.ACTION_OPEN_SUBMISSIONS.equals(intent.getAction())) {
            String userName = intent.getStringExtra(SubmissionsContract.EXTRA_USER);
            if (TextUtils.isEmpty(userName)) {
                Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show();
                return false;
            }
            mainNavigationController.openSubmissions(userName);
            return true;
        }

        if (CoulombGasContract.ACTION_OPEN.equals(intent.getAction())) {
            mainNavigationController.openCoulombGas();
            return true;
        }

        Bundle arguments = intent.getExtras() == null
                ? new Bundle()
                : new Bundle(intent.getExtras());
        Uri hackerNewsUri = null;
        boolean commentsIntent = false;

        if (Intent.ACTION_VIEW.equalsIgnoreCase(intent.getAction())) {
            commentsIntent = true;
            hackerNewsUri = intent.getData();
        } else if (Intent.ACTION_SEND.equalsIgnoreCase(intent.getAction())) {
            commentsIntent = true;
            CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            hackerNewsUri = Utils.getHackerNewsItemUriFromText(
                    sharedText == null ? null : sharedText.toString());
        }

        int itemId = arguments.getInt(CommentsContract.EXTRA_ID, -1);
        if (hackerNewsUri != null && Utils.isHackerNewsItemUri(hackerNewsUri)) {
            try {
                itemId = Integer.parseInt(hackerNewsUri.getQueryParameter("id"));
                String fragment = hackerNewsUri.getFragment();
                if (!TextUtils.isEmpty(fragment) && TextUtils.isDigitsOnly(fragment)) {
                    arguments.putInt(
                            CommentsContract.EXTRA_SCROLL_TO_COMMENT,
                            Integer.parseInt(fragment));
                }
            } catch (RuntimeException ignored) {
                itemId = -1;
            }
        }

        if (itemId <= 0) {
            if (commentsIntent) {
                Toast.makeText(this, "Unable to parse story", Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        arguments.putInt(CommentsContract.EXTRA_ID, itemId);
        if (!arguments.containsKey(CommentsContract.EXTRA_TITLE)) {
            arguments.putString(CommentsContract.EXTRA_TITLE, "");
        }
        arguments.putBoolean(
                CommentsContract.EXTRA_SHOW_WEBSITE,
                arguments.getBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, false));
        mainNavigationController.openStory(arguments);
        return true;
    }

    public void restartAfterSettingsChange() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent == null) {
            recreate();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launchIntent);
    }

    public void setImmersiveContentEnabled(boolean enabled) {
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView());
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            insetsController.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    public boolean isAdaptiveTwoPaneNavigation() {
        return mainNavigationController.isAdaptiveTwoPane();
    }

    public boolean isAdaptiveFoldableNavigation() {
        return mainNavigationController.isAdaptiveFoldable();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        CommentsCoordinator coordinator = getCommentsCoordinator();
        if (coordinator != null) coordinator.onConfigurationChanged(newConfig);
    }

    @Override
    public void onSwitchView(boolean isAtWebView) {
        // Navigation 3 and the comments coordinator's back handler own the relevant state now.
    }

    private CommentsCoordinator getCommentsCoordinator() {
        return mainNavigationController == null ? null : mainNavigationController.getCommentsCoordinator();
    }

}
