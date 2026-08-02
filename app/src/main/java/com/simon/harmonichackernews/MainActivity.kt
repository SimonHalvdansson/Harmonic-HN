package com.simon.harmonichackernews

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.simon.harmonichackernews.CommentsCoordinator.CommentsPaneCallback
import com.simon.harmonichackernews.StoriesCoordinator.StoryClickListener
import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.UserActions
import com.simon.harmonichackernews.network.UserActions.CaptchaChallenge
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.ui.navigation.MainNavigationHost
import com.simon.harmonichackernews.ui.navigation.MainNavigationHost.install
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

class MainActivity : BaseActivity(), StoryClickListener, CommentsPaneCallback {
    var lastPosition: Int = 0
    var backPressedCallback: OnBackPressedCallback? = null
    private var searchBackEnabled = false
    private var mainNavigationController: MainNavigationController? = null

    var bottom: Int = 0

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentMainActivity = WeakReference<MainActivity?>(this)

        ThemeUtils.setupTheme(this)

        mainNavigationController = install(this, savedInstanceState)
        initializeStoriesCoordinator(savedInstanceState)
        // A singleTask can be recreated with saved navigation state while also receiving a new
        // deep link or feature intent. Always apply that launch intent after restoring state so
        // the newly requested destination wins.
        openCommentsFromIntent(getIntent())

        val shouldShowWelcomeDialog = Utils.shouldShowWelcomeDialog(this)
        val justUpdated = Utils.justUpdated(this)
        if (shouldShowWelcomeDialog) {
            mainNavigationController!!.showWelcomeDialog()
        } else if (justUpdated && SettingsUtils.shouldShowChangelog(this)) {
            mainNavigationController!!.showChangelogDialog()
        }

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackCancelled() {
                cancelSearchBackProgress()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                updateSearchBackProgress(backEvent.progress)
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                startSearchBackProgress(backEvent.progress)
            }

            override fun handleOnBackPressed() {
                finishSearchBackProgress()
            }
        }

        onBackPressedDispatcher.addCallback(this, backPressedCallback!!)
        setSearchBackEnabled(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCommentsFromIntent(intent)
    }

    protected override fun onSaveInstanceState(outState: Bundle) {
        val storiesCoordinator: StoriesCoordinator? = this.storiesCoordinator
        if (storiesCoordinator != null) {
            storiesCoordinator.onSaveInstanceState(outState)
        }
        if (mainNavigationController != null) {
            mainNavigationController!!.saveState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    protected override fun onDestroy() {
        val storiesCoordinator: StoriesCoordinator? = this.storiesCoordinator
        if (storiesCoordinator != null) {
            storiesCoordinator.onDestroy()
        }
        if (getCurrentMainActivity() === this) {
            currentMainActivity.clear()
            searchBackEnabled = false
            notifySearchBackStateListeners(false)
        }
        super.onDestroy()
    }

    protected override fun onStart() {
        super.onStart()
        val storiesCoordinator: StoriesCoordinator? = this.storiesCoordinator
        if (storiesCoordinator != null) storiesCoordinator.onStart()
    }

    protected override fun onResume() {
        super.onResume()
        val storiesCoordinator: StoriesCoordinator? = this.storiesCoordinator
        if (storiesCoordinator != null) storiesCoordinator.onResume()
    }

    protected override fun onStop() {
        val storiesCoordinator: StoriesCoordinator? = this.storiesCoordinator
        if (storiesCoordinator != null) storiesCoordinator.onStop()
        super.onStop()
    }

    fun setSearchBackEnabled(enabled: Boolean) {
        searchBackEnabled = enabled
        backPressedCallback!!.isEnabled = enabled
        notifySearchBackStateListeners(enabled)
    }

    fun showCaptchaDialog(
        challenge: CaptchaChallenge,
        callback: CaptchaResultCallback
    ) {
        if (mainNavigationController == null) {
            callback.onCaptchaCancelled()
            return
        }
        mainNavigationController!!.showCaptchaDialog(challenge, callback)
    }

    fun showUserDialog(userName: String, onTagChanged: Runnable?) {
        if (mainNavigationController != null) {
            mainNavigationController!!.showUserDialog(userName, onTagChanged)
        }
    }

    private fun applyWelcomePresetToUi() {
        val coordinator: StoriesCoordinator? = this.storiesCoordinator

        if (coordinator != null) {
            coordinator.applyWelcomePresetSettings()
        }
    }

    private fun startSearchBackProgress(progress: Float) {
        val coordinator: StoriesCoordinator? = this.storiesCoordinator

        if (coordinator != null) {
            coordinator.startSearchBackProgress(progress)
        }
    }

    private fun updateSearchBackProgress(progress: Float) {
        val coordinator: StoriesCoordinator? = this.storiesCoordinator

        if (coordinator != null) {
            coordinator.updateSearchBackProgress(progress)
        }
    }

    private fun cancelSearchBackProgress() {
        val coordinator: StoriesCoordinator? = this.storiesCoordinator

        if (coordinator != null) {
            coordinator.cancelSearchBackProgress()
        }
    }

    private fun finishSearchBackProgress(): Boolean {
        val coordinator: StoriesCoordinator? = this.storiesCoordinator

        return coordinator != null && coordinator.finishSearchBackProgress()
    }

    private val storiesCoordinator: StoriesCoordinator?
        get() = if (mainNavigationController == null) null else mainNavigationController!!.getStoriesCoordinator()

    private fun initializeStoriesCoordinator(savedInstanceState: Bundle?) {
        val coordinator: StoriesCoordinator = StoriesCoordinator(this, savedInstanceState)
        mainNavigationController!!.attachStoriesCoordinator(coordinator)
        val composeController: StoriesComposeController? = coordinator.composeController
        if (composeController != null) {
            mainNavigationController!!.attachStoriesComposeController(composeController)
        }
    }

    interface SearchBackStateListener {
        fun onSearchBackStateChanged(enabled: Boolean)
    }

    override fun openStory(story: Story?, pos: Int, showWebsite: Boolean) {
        if (story == null) return
        if (switchOpenStoryViewIfMatching(story, showWebsite)) {
            lastPosition = pos
            return
        }

        val bundle = story.toBundle()

        bundle.putInt(CommentsContract.EXTRA_FORWARD, pos - lastPosition)
        bundle.putBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, showWebsite)

        lastPosition = pos
        mainNavigationController!!.openStory(bundle)
    }

    private fun switchOpenStoryViewIfMatching(story: Story?, showWebsite: Boolean): Boolean {
        if (story == null) {
            return false
        }
        return mainNavigationController!!.switchOpenStoryViewIfMatching(story.id, showWebsite)
    }

    public override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volumeNavigationMode =
            SettingsUtils.getCommentsVolumeNavigationMode(getApplicationContext())
        if (SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED != volumeNavigationMode) {
            val topLevelOnly =
                SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL == volumeNavigationMode
            val coordinator: CommentsCoordinator? = this.commentsCoordinator
            if (coordinator != null && coordinator.isAdded && coordinator.isBottomSheetFullyExpanded) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    coordinator.navigateToNextComment(topLevelOnly, true)
                    return true
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    coordinator.navigateToPreviousComment(topLevelOnly, true)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun onAccountStateChanged() {
        val coordinator: StoriesCoordinator? = this.storiesCoordinator
        if (coordinator != null) {
            coordinator.onAccountStateChanged()
        }
    }

    fun closeStory() {
        mainNavigationController!!.closeStory()
    }

    fun showCacheStoriesDialog() {
        if (mainNavigationController != null) {
            mainNavigationController!!.showCacheStoriesDialog()
        }
    }

    fun attachStoriesComposeController(controller: StoriesComposeController) {
        if (mainNavigationController != null) {
            mainNavigationController!!.attachStoriesComposeController(controller)
        }
    }

    fun detachStoriesComposeController(controller: StoriesComposeController) {
        if (mainNavigationController != null) {
            mainNavigationController!!.detachStoriesComposeController(controller)
        }
    }

    fun attachCommentsComposeController(controller: CommentsComposeController) {
        if (mainNavigationController != null) {
            mainNavigationController!!.attachCommentsComposeController(controller)
        }
    }

    fun detachCommentsComposeController(controller: CommentsComposeController) {
        if (mainNavigationController != null) {
            mainNavigationController!!.detachCommentsComposeController(controller)
        }
    }

    @JvmOverloads
    fun openCommentsItem(itemId: Int, scrollToCommentId: Int = -1): Boolean {
        if (itemId <= 0) {
            return false
        }
        val bundle = Bundle()
        bundle.putInt(CommentsContract.EXTRA_ID, itemId)
        bundle.putString(CommentsContract.EXTRA_TITLE, "")
        bundle.putBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, false)
        if (scrollToCommentId > 0) {
            bundle.putInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, scrollToCommentId)
        }
        mainNavigationController!!.openStory(bundle)
        return true
    }

    private fun openCommentsFromIntent(intent: Intent?): Boolean {
        if (intent == null || mainNavigationController == null) {
            return false
        }

        if (ACTION_OPEN_SETTINGS == intent.getAction()) {
            mainNavigationController!!.openSettings(
                intent.getStringExtra(EXTRA_SETTINGS_SECTION)
            )
            return true
        }

        if (ComposeEditorContract.ACTION_OPEN_EDITOR == intent.getAction()) {
            val editorArguments = if (intent.getExtras() == null)
                Bundle()
            else
                Bundle(intent.getExtras())
            val editorType = editorArguments.getInt(
                ComposeEditorContract.EXTRA_TYPE,
                ComposeEditorContract.TYPE_POST
            )
            if (editorType != ComposeEditorContract.TYPE_POST
                && editorArguments.getInt(ComposeEditorContract.EXTRA_ID, -1) <= 0
            ) {
                Toast.makeText(this, "Invalid comment id", Toast.LENGTH_SHORT).show()
                return false
            }
            mainNavigationController!!.openEditor(editorArguments)
            return true
        }

        if (SubmissionsContract.ACTION_OPEN_SUBMISSIONS == intent.getAction()) {
            val userName = intent.getStringExtra(SubmissionsContract.EXTRA_USER)
            if (TextUtils.isEmpty(userName)) {
                Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show()
                return false
            }
            mainNavigationController!!.openSubmissions(userName!!)
            return true
        }

        if (CoulombGasContract.ACTION_OPEN == intent.getAction()) {
            mainNavigationController!!.openCoulombGas()
            return true
        }

        val arguments = if (intent.getExtras() == null)
            Bundle()
        else
            Bundle(intent.getExtras())
        var hackerNewsUri: Uri? = null
        var commentsIntent = false

        if (Intent.ACTION_VIEW.equals(intent.getAction(), ignoreCase = true)) {
            commentsIntent = true
            hackerNewsUri = intent.getData()
        } else if (Intent.ACTION_SEND.equals(intent.getAction(), ignoreCase = true)) {
            commentsIntent = true
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            hackerNewsUri = Utils.getHackerNewsItemUriFromText(
                if (sharedText == null) null else sharedText.toString()
            )
        }

        var itemId = arguments.getInt(CommentsContract.EXTRA_ID, -1)
        if (hackerNewsUri != null && Utils.isHackerNewsItemUri(hackerNewsUri)) {
            try {
                itemId = hackerNewsUri.getQueryParameter("id")!!.toInt()
                val fragment = hackerNewsUri.getFragment()
                if (!TextUtils.isEmpty(fragment) && TextUtils.isDigitsOnly(fragment)) {
                    arguments.putInt(
                        CommentsContract.EXTRA_SCROLL_TO_COMMENT,
                        fragment!!.toInt()
                    )
                }
            } catch (ignored: RuntimeException) {
                itemId = -1
            }
        }

        if (itemId <= 0) {
            if (commentsIntent) {
                Toast.makeText(this, "Unable to parse story", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        arguments.putInt(CommentsContract.EXTRA_ID, itemId)
        if (!arguments.containsKey(CommentsContract.EXTRA_TITLE)) {
            arguments.putString(CommentsContract.EXTRA_TITLE, "")
        }
        arguments.putBoolean(
            CommentsContract.EXTRA_SHOW_WEBSITE,
            arguments.getBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, false)
        )
        mainNavigationController!!.openStory(arguments)
        return true
    }

    fun restartAfterSettingsChange() {
        val launchIntent: Intent? = getPackageManager().getLaunchIntentForPackage(getPackageName())
        if (launchIntent == null) {
            recreate()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
    }

    fun setImmersiveContentEnabled(enabled: Boolean) {
        val insetsController = WindowCompat.getInsetsController(
            getWindow(),
            getWindow().getDecorView()
        )
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            )
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val isAdaptiveTwoPaneNavigation: Boolean
        get() = mainNavigationController!!.isAdaptiveTwoPane()

    val isAdaptiveFoldableNavigation: Boolean
        get() = mainNavigationController!!.isAdaptiveFoldable()

    public override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val coordinator: CommentsCoordinator? = this.commentsCoordinator
        if (coordinator != null) coordinator.onConfigurationChanged(newConfig)
    }

    public override fun onSwitchView(isAtWebView: Boolean) {
        // Navigation 3 and the comments coordinator's back handler own the relevant state now.
    }

    private val commentsCoordinator: CommentsCoordinator?
        get() = if (mainNavigationController == null) null else mainNavigationController!!.getCommentsCoordinator()

    companion object {
        const val ACTION_OPEN_SETTINGS: String = "com.simon.harmonichackernews.action.OPEN_SETTINGS"
        const val EXTRA_SETTINGS_SECTION: String =
            "com.simon.harmonichackernews.extra.SETTINGS_SECTION"
        var commentsScrollProgresses: ArrayList<CommentsScrollProgress> = ArrayList()
        private val searchBackStateListeners: MutableSet<SearchBackStateListener> =
            Collections.newSetFromMap<SearchBackStateListener?>(
                WeakHashMap<SearchBackStateListener?, Boolean?>()
            )
        private var currentMainActivity = WeakReference<MainActivity?>(null)

        val isSearchBackActive: Boolean
            get() {
                val activity: MainActivity? = getCurrentMainActivity()
                return activity != null && activity.searchBackEnabled
            }

        fun addSearchBackStateListener(listener: SearchBackStateListener) {
            searchBackStateListeners.add(listener)
            listener.onSearchBackStateChanged(isSearchBackActive)
        }

        fun removeSearchBackStateListener(listener: SearchBackStateListener?) {
            searchBackStateListeners.remove(listener)
        }

        fun startActiveSearchBackProgress(progress: Float) {
            val activity: MainActivity? = getCurrentMainActivity()
            if (activity != null) {
                activity.startSearchBackProgress(progress)
            }
        }

        fun updateActiveSearchBackProgress(progress: Float) {
            val activity: MainActivity? = getCurrentMainActivity()
            if (activity != null) {
                activity.updateSearchBackProgress(progress)
            }
        }

        fun cancelActiveSearchBackProgress() {
            val activity: MainActivity? = getCurrentMainActivity()
            if (activity != null) {
                activity.cancelSearchBackProgress()
            }
        }

        fun finishActiveSearchBackProgress(): Boolean {
            val activity: MainActivity? = getCurrentMainActivity()
            return activity != null && activity.finishSearchBackProgress()
        }

        fun applyWelcomePresetToActiveUi() {
            val activity: MainActivity? = getCurrentMainActivity()
            if (activity != null) {
                activity.applyWelcomePresetToUi()
            }
        }

        fun showLoginPrompt(): Boolean {
            val activity: MainActivity? = getCurrentMainActivity()
            if (activity == null || activity.mainNavigationController == null) {
                return false
            }
            activity.mainNavigationController!!.showLoginDialog()
            return true
        }

        private fun getCurrentMainActivity(): MainActivity? {
            return currentMainActivity.get()
        }

        private fun notifySearchBackStateListeners(enabled: Boolean) {
            for (listener in searchBackStateListeners) {
                listener.onSearchBackStateChanged(enabled)
            }
        }
    }
}
