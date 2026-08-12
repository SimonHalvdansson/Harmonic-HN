package com.simon.harmonichackernews

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.simon.harmonichackernews.data.toEditorDestination
import com.simon.harmonichackernews.data.toStoryDestinationOrNull
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.ui.navigation.MainNavigationHost.install
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.CommentNavigationPreferences
import com.simon.harmonichackernews.utils.HackerNewsItemLink
import com.simon.harmonichackernews.utils.HackerNewsLinks
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

class MainActivity : BaseActivity() {
    private lateinit var backPressedCallback: OnBackPressedCallback
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
        consumeLaunchIntent(getIntent())

        val shouldShowWelcomeDialog = Utils.shouldShowWelcomeDialog(this)
        val justUpdated = Utils.justUpdated(this)
        if (shouldShowWelcomeDialog) {
            mainNavigationController?.showWelcomeDialog()
        } else if (justUpdated && AndroidUserSettings.get(this).general.showChangelog) {
            mainNavigationController?.showChangelogDialog()
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

        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        setSearchBackEnabled(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        if (openCommentsFromIntent(intent)) {
            // Navigation now owns the destination. Keeping the one-shot action as the Activity's
            // current intent would apply it again during a configuration-driven recreation.
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    protected override fun onSaveInstanceState(outState: Bundle) {
        storiesCoordinator?.onSaveInstanceState(outState)
        mainNavigationController?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    protected override fun onDestroy() {
        storiesCoordinator?.onDestroy()
        if (getCurrentMainActivity() === this) {
            currentMainActivity.clear()
            searchBackEnabled = false
            notifySearchBackStateListeners(false)
        }
        super.onDestroy()
    }

    protected override fun onStart() {
        super.onStart()
        storiesCoordinator?.onStart()
    }

    protected override fun onResume() {
        super.onResume()
        storiesCoordinator?.onResume()
    }

    protected override fun onStop() {
        storiesCoordinator?.onStop()
        super.onStop()
    }

    fun setSearchBackEnabled(enabled: Boolean) {
        searchBackEnabled = enabled
        backPressedCallback.isEnabled = enabled
        notifySearchBackStateListeners(enabled)
    }

    fun showCaptchaDialog(
        challenge: HackerNewsCaptchaChallenge,
        callback: CaptchaResultCallback
    ) {
        val controller = mainNavigationController
        if (controller == null) {
            callback.onCaptchaCancelled()
            return
        }
        controller.showCaptchaDialog(challenge, callback)
    }

    fun showUserDialog(userName: String, onTagChanged: Runnable?) {
        mainNavigationController?.showUserDialog(userName, onTagChanged)
    }

    fun showLoginPrompt() {
        mainNavigationController?.showLoginDialog()
    }

    fun showFailureDetailDialog(
        title: String?,
        message: String?,
        clipboardText: String? = null,
    ) {
        mainNavigationController?.showFailureDetailDialog(title, message, clipboardText)
    }

    private fun startSearchBackProgress(progress: Float) {
        storiesComposeController?.startSearchBack(progress)
    }

    private fun updateSearchBackProgress(progress: Float) {
        storiesComposeController?.updateSearchBack(progress)
    }

    private fun cancelSearchBackProgress() {
        storiesComposeController?.cancelSearchBack()
    }

    private fun finishSearchBackProgress(): Boolean {
        return storiesComposeController?.finishSearchBack() == true
    }

    private val storiesComposeController: StoriesComposeController?
        get() = mainNavigationController?.storiesComposeController

    private val storiesCoordinator: StoriesCoordinator?
        get() = mainNavigationController?.getStoriesCoordinator()

    private fun initializeStoriesCoordinator(savedInstanceState: Bundle?) {
        val controller = checkNotNull(mainNavigationController)
        val coordinator = StoriesCoordinator(this, savedInstanceState)
        controller.attachStoriesCoordinator(coordinator)
        coordinator.composeController?.let(controller::attachStoriesComposeController)
    }

    interface SearchBackStateListener {
        fun onSearchBackStateChanged(enabled: Boolean)
    }

    internal fun openStory(destination: StoryDestination) {
        if (mainNavigationController!!.switchOpenStoryViewIfMatching(
                destination.storyId,
                destination.showWebsite,
            )
        ) return
        mainNavigationController!!.openStory(destination)
    }

    public override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volumeNavigationMode = AndroidUserSettings.get(this).comments.volumeNavigationMode
        if (CommentNavigationPreferences.DISABLED != volumeNavigationMode) {
            val topLevelOnly =
                CommentNavigationPreferences.TOP_LEVEL == volumeNavigationMode
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

    fun closeStory() {
        mainNavigationController!!.closeStory()
    }

    fun showCacheStoriesDialog() {
        mainNavigationController?.showCacheStoriesDialog()
    }

    fun attachStoriesComposeController(controller: StoriesComposeController) {
        mainNavigationController?.attachStoriesComposeController(controller)
    }

    fun detachStoriesComposeController(controller: StoriesComposeController) {
        mainNavigationController?.detachStoriesComposeController(controller)
    }

    fun attachCommentsComposeController(controller: CommentsComposeController) {
        mainNavigationController?.attachCommentsComposeController(controller)
    }

    fun detachCommentsComposeController(controller: CommentsComposeController) {
        mainNavigationController?.detachCommentsComposeController(controller)
    }

    @JvmOverloads
    fun openCommentsItem(itemId: Int, scrollToCommentId: Int = -1): Boolean {
        if (itemId <= 0) {
            return false
        }
        mainNavigationController!!.openStory(
            StoryDestination(
                storyId = itemId,
                scrollToCommentId = scrollToCommentId,
            ),
        )
        return true
    }

    private fun openCommentsFromIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val navigationController = mainNavigationController ?: return false

        if (ACTION_OPEN_SETTINGS == intent.action) {
            navigationController.openSettings(
                intent.getStringExtra(EXTRA_SETTINGS_SECTION)
            )
            return true
        }

        if (ComposeEditorContract.ACTION_OPEN_EDITOR == intent.action) {
            val editorArguments = intent.extras?.let(::Bundle) ?: Bundle()
            val destination = editorArguments.toEditorDestination()
            if (!destination.isValid) {
                Toast.makeText(this, "Invalid comment id", Toast.LENGTH_SHORT).show()
                return false
            }
            navigationController.openEditor(destination)
            return true
        }

        if (SubmissionsContract.ACTION_OPEN_SUBMISSIONS == intent.action) {
            val userName = intent.getStringExtra(SubmissionsContract.EXTRA_USER)
            if (userName.isNullOrEmpty()) {
                Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show()
                return false
            }
            navigationController.openSubmissions(userName)
            return true
        }

        if (CoulombGasContract.ACTION_OPEN == intent.action) {
            navigationController.openCoulombGas()
            return true
        }

        val arguments = intent.extras?.let(::Bundle) ?: Bundle()
        var hackerNewsLink: HackerNewsItemLink? = null
        var commentsIntent = false

        if (Intent.ACTION_VIEW.equals(intent.action, ignoreCase = true)) {
            commentsIntent = true
            hackerNewsLink = HackerNewsLinks.parseItemLink(intent.data?.toString())
        } else if (Intent.ACTION_SEND.equals(intent.action, ignoreCase = true)) {
            commentsIntent = true
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            hackerNewsLink = HackerNewsLinks.findItemLink(sharedText?.toString())
        }

        var itemId = arguments.getInt(CommentsContract.EXTRA_ID, -1)
        hackerNewsLink?.let { link ->
            itemId = link.itemId
            if (link.scrollToCommentId > 0) {
                arguments.putInt(
                    CommentsContract.EXTRA_SCROLL_TO_COMMENT,
                    link.scrollToCommentId,
                )
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
        val destination = arguments.toStoryDestinationOrNull() ?: return false
        navigationController.openStory(destination)
        return true
    }

    fun restartAfterSettingsChange() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            recreate()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
    }

    /** Apply preference changes when the in-app settings overlay closes.
     *
     * Settings used to be a separate Activity, so returning to the story list naturally called
     * [onResume]. The Compose overlay keeps this Activity resumed and must explicitly run the same
     * refresh path.
     */
    fun applySettingsChanges() {
        storiesCoordinator?.onResume()
    }

    fun setImmersiveContentEnabled(enabled: Boolean) {
        val insetsController = WindowCompat.getInsetsController(
            window,
            window.decorView
        )
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val isAdaptiveTwoPaneNavigation: Boolean
        get() = mainNavigationController!!.isAdaptiveTwoPane()

    val isAdaptiveFoldableNavigation: Boolean
        get() = mainNavigationController!!.isAdaptiveFoldable()

    public override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        commentsCoordinator?.onConfigurationChanged(newConfig)
    }

    private val commentsCoordinator: CommentsCoordinator?
        get() = mainNavigationController?.getCommentsCoordinator()

    companion object {
        const val ACTION_OPEN_SETTINGS: String = "com.simon.harmonichackernews.action.OPEN_SETTINGS"
        const val EXTRA_SETTINGS_SECTION: String =
            "com.simon.harmonichackernews.extra.SETTINGS_SECTION"
        private val searchBackStateListeners: MutableSet<SearchBackStateListener> =
            Collections.newSetFromMap(WeakHashMap())
        private var currentMainActivity = WeakReference<MainActivity?>(null)

        val isSearchBackActive: Boolean
            get() {
                return getCurrentMainActivity()?.searchBackEnabled == true
            }

        fun addSearchBackStateListener(listener: SearchBackStateListener) {
            searchBackStateListeners.add(listener)
            listener.onSearchBackStateChanged(isSearchBackActive)
        }

        fun removeSearchBackStateListener(listener: SearchBackStateListener?) {
            searchBackStateListeners.remove(listener)
        }

        fun startActiveSearchBackProgress(progress: Float) {
            getCurrentMainActivity()?.startSearchBackProgress(progress)
        }

        fun updateActiveSearchBackProgress(progress: Float) {
            getCurrentMainActivity()?.updateSearchBackProgress(progress)
        }

        fun cancelActiveSearchBackProgress() {
            getCurrentMainActivity()?.cancelSearchBackProgress()
        }

        fun finishActiveSearchBackProgress(): Boolean {
            return getCurrentMainActivity()?.finishSearchBackProgress() == true
        }

        fun showFailureDetailForActiveUi(
            title: String?,
            message: String?,
            clipboardText: String? = null,
        ): Boolean {
            val activity = getCurrentMainActivity() ?: return false
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.showFailureDetailDialog(title, message, clipboardText)
                }
            }
            return true
        }

        private fun getCurrentMainActivity(): MainActivity? = currentMainActivity.get()

        private fun notifySearchBackStateListeners(enabled: Boolean) {
            searchBackStateListeners.forEach { it.onSearchBackStateChanged(enabled) }
        }
    }
}
