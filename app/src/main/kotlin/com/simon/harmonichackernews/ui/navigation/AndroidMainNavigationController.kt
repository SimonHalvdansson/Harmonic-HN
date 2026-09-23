package com.simon.harmonichackernews.ui.navigation

import android.content.res.Configuration
import android.os.Bundle
import android.os.Trace
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.AndroidCommentsCoordinator
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.AndroidStoriesCoordinator
import com.simon.harmonichackernews.ui.comments.CommentsScreenController
import com.simon.harmonichackernews.presentation.CaptchaResultHandler
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.data.toEditorDestination
import com.simon.harmonichackernews.data.toStoryDestinationOrNull
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.navigation.MainNavigationRestoration
import com.simon.harmonichackernews.navigation.MainNavigationRestorationCodec
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainSettingsRequest
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.UserMessageDuration

/**
 * Imperative bridge used by the existing story-loading controller while Navigation 3 owns the
 * actual list/detail history and adaptive layout.
 */
@Stable
class AndroidMainNavigationController internal constructor(
    internal val scene: HarmonicSceneComposition,
    savedState: Bundle? = null,
) {
    internal val navigationState: MainNavigationStore = scene.navigation
    private val userMessages = scene.userMessages

    private var captchaCallback: CaptchaResultHandler? = null
    private var userTagChangedCallback: Runnable? = null
    private val restoredStoryRoute = savedState
        ?.getInt(STATE_STORY_ID, 0)
        ?.takeIf { it > 0 }
        ?.let { storyId ->
            StoryRoute(
                storyId = storyId,
                showWebsite = savedState.getBoolean(STATE_STORY_SHOW_WEBSITE, false),
                scrollToCommentId =
                    savedState.getInt(STATE_STORY_SCROLL_TO_COMMENT_ID, -1),
            )
        }
    private val restoredNavigation = MainNavigationRestorationCodec.decode(
        savedState?.getString(STATE_NAVIGATION_RESTORATION),
    ) ?: MainNavigationRestoration(
            storyDestination = if (restoredStoryRoute == null) {
                savedState?.getBundle(STATE_STORY_ARGUMENTS)?.toStoryDestinationOrNull()
            } else {
                null
            },
            storyRoute = restoredStoryRoute,
            storyRequestSerial = savedState?.getInt(STATE_REQUEST_SERIAL, 0) ?: 0,
            settingsOpen = savedState?.getBoolean(STATE_SETTINGS_OPEN, false) == true,
            settingsRequestSerial = savedState?.getInt(STATE_SETTINGS_REQUEST_SERIAL, 0) ?: 0,
            settingsSectionRoute = savedState?.getString(STATE_SETTINGS_SECTION),
            welcomeDialogVisible =
                savedState?.getBoolean(STATE_WELCOME_DIALOG_VISIBLE, false) == true,
            changelogDialogVisible =
                savedState?.getBoolean(STATE_CHANGELOG_DIALOG_VISIBLE, false) == true,
            cacheStoriesDialogVisible =
                savedState?.getBoolean(STATE_CACHE_STORIES_DIALOG_VISIBLE, false) == true,
            loginDialogVisible =
                savedState?.getBoolean(STATE_LOGIN_DIALOG_VISIBLE, false) == true,
            userDialogUserName = savedState?.getString(STATE_USER_DIALOG_NAME),
            userDialogSerial = savedState?.getInt(STATE_USER_DIALOG_SERIAL, 0) ?: 0,
            editorDestination = savedState?.getBundle(STATE_EDITOR_ARGUMENTS)
                ?.toEditorDestination(),
            editorRequestSerial = savedState?.getInt(STATE_EDITOR_REQUEST_SERIAL, 0) ?: 0,
            submissionsUserName = savedState?.getString(STATE_SUBMISSIONS_USER),
            submissionsRequestSerial =
                savedState?.getInt(STATE_SUBMISSIONS_REQUEST_SERIAL, 0) ?: 0,
            storyOpenedFromSubmissions =
                savedState?.getBoolean(STATE_STORY_OPENED_FROM_SUBMISSIONS, false) == true,
            storyOpenedFromSettings =
                savedState?.getBoolean(STATE_STORY_OPENED_FROM_SETTINGS, false) == true,
            coulombGasVisible =
                savedState?.getBoolean(STATE_COULOMB_GAS_VISIBLE, false) == true,
        )
    private var storiesCoordinator: AndroidStoriesCoordinator? = null
    private var commentsCoordinator: AndroidCommentsCoordinator? = null
    private val commentsCoordinatorCache = mutableMapOf<Int, AndroidCommentsCoordinator>()
    private val commentsCoordinatorReferences = mutableMapOf<Int, Int>()
    private var restoredCommentsState: Bundle? = savedState?.getBundle(STATE_COMMENTS_STATE)
    private var restoredCommentsRequestSerial: Int =
        savedState?.getInt(STATE_COMMENTS_REQUEST_SERIAL, -1) ?: -1
    internal var storiesComposeController by mutableStateOf<StoriesScreenController?>(null)
        private set
    internal var commentsComposeController by mutableStateOf<CommentsScreenController?>(null)
        private set
    private var adaptiveTwoPane = false
    private var adaptiveFoldable = false
    private var externalStorySerial by mutableIntStateOf(
        savedState?.getInt(STATE_EXTERNAL_STORY_SERIAL, -1) ?: -1,
    )

    internal fun markExternalStoryEntry() {
        externalStorySerial = navigationState.state.value.storyRequest?.serial ?: -1
    }

    internal val isExternalStoryEntry: Boolean
        get() = navigationState.state.value.currentDestination == MainDestination.STORY &&
            navigationState.state.value.storyRequest?.serial == externalStorySerial

    init {
        navigationState.restore(restoredNavigation)
    }

    fun openStory(destination: StoryDestination) {
        if (
            navigationState.state.value.currentDestination == MainDestination.STORY &&
            commentsCoordinator?.switchStoryViewIfMatching(
                destination.storyId,
                destination.showWebsite,
            ) == true
        ) return
        restoredCommentsState = null
        restoredCommentsRequestSerial = -1
        navigationState.openStory(destination)
    }

    fun openLinkedStory(destination: StoryDestination) {
        if (
            navigationState.state.value.currentDestination == MainDestination.STORY &&
            commentsCoordinator?.switchStoryViewIfMatching(
                destination.storyId,
                destination.showWebsite,
            ) == true
        ) return
        restoredCommentsState = null
        restoredCommentsRequestSerial = -1
        navigationState.openLinkedStory(destination)
    }

    fun closeStory() {
        navigationState.requestCloseStory()
    }

    fun openSettings(sectionRoute: String?) {
        navigationState.openSettings(sectionRoute)
    }

    internal fun closeSettings() {
        navigationState.closeSettings()
    }

    internal fun updateSettingsSection(section: SettingsSection) {
        navigationState.updateSettingsSection(section.route)
    }

    internal fun getInitialSettingsSectionRoute(request: MainSettingsRequest): String? =
        navigationState.initialSettingsSectionRoute(request)

    fun showWelcomeDialog() {
        navigationState.showWelcomeDialog()
    }

    internal fun dismissWelcomeDialog() {
        navigationState.dismissWelcomeDialog()
    }

    fun showChangelogDialog() {
        navigationState.showChangelogDialog()
    }

    internal fun dismissChangelogDialog() {
        navigationState.dismissChangelogDialog()
    }

    fun showCacheStoriesDialog() {
        navigationState.showCacheStoriesDialog()
    }

    internal fun dismissCacheStoriesDialog() {
        navigationState.dismissCacheStoriesDialog()
    }

    internal fun confirmCacheStories(storyCount: Int, downloadWebViewContents: Boolean) {
        navigationState.dismissCacheStoriesDialog()
        storiesComposeController?.cacheStories(storyCount, downloadWebViewContents)
    }

    fun showLoginDialog() {
        navigationState.showLoginDialog()
    }

    internal fun dismissLoginDialog() {
        navigationState.dismissLoginDialog()
    }

    fun showCaptchaDialog(
        challenge: HackerNewsCaptchaChallenge,
        callback: CaptchaResultHandler,
    ) {
        captchaCallback?.onCaptchaCancelled()
        navigationState.showCaptchaDialog(challenge)
        captchaCallback = callback
    }

    internal fun dismissCaptchaDialog() {
        if (navigationState.dismissCaptchaDialog() == null) return
        val callback = captchaCallback
        captchaCallback = null
        callback?.onCaptchaCancelled()
    }

    internal fun completeCaptchaDialog(response: String) {
        val request = navigationState.dismissCaptchaDialog() ?: return
        val callback = captchaCallback
        captchaCallback = null
        callback?.onCaptchaResponse(request.challenge, response)
    }

    fun showUserDialog(userName: String, onTagChanged: Runnable?) {
        if (navigationState.showUserDialog(userName) == null) return
        userTagChangedCallback = onTagChanged
    }

    internal fun dismissUserDialog() {
        navigationState.dismissUserDialog()
        userTagChangedCallback = null
    }

    internal fun notifyUserTagChanged() {
        userTagChangedCallback?.run()
        commentsComposeController?.refreshContent()
    }

    fun showFailureDetailDialog(
        title: String?,
        message: String?,
        clipboardText: String?,
    ) {
        navigationState.showFailureDetailDialog(title, message, clipboardText)
    }

    fun showMessage(
        message: String?,
        duration: UserMessageDuration = UserMessageDuration.SHORT,
    ) {
        userMessages.show(message, duration)
    }

    internal fun dismissFailureDetailDialog() {
        navigationState.dismissFailureDetailDialog()
    }

    fun openEditor(destination: EditorDestination) {
        navigationState.openEditor(destination)
    }

    internal fun closeEditor() {
        navigationState.closeEditor()
    }

    fun openSubmissions(userName: String) {
        navigationState.openSubmissions(userName)
    }

    internal fun closeSubmissions() {
        if (adaptiveTwoPane) {
            while (
                navigationState.state.value.currentDestination == MainDestination.STORY &&
                navigationState.state.value.storyStackParentDestination == MainDestination.SUBMISSIONS
            ) {
                navigationState.detailRemovedFromBackStack()
            }
        }
        navigationState.closeSubmissions()
    }

    internal fun openSubmissionStory(destination: StoryDestination) {
        if (!adaptiveTwoPane) {
            openStory(destination)
            return
        }
        // A new list selection replaces the whole detail history and starts at the top.
        while (
            navigationState.state.value.currentDestination == MainDestination.STORY &&
            navigationState.state.value.storyStackParentDestination == MainDestination.SUBMISSIONS
        ) {
            navigationState.detailRemovedFromBackStack()
        }
        openStory(destination)
    }

    fun openCoulombGas() {
        navigationState.openCoulombGas()
    }

    internal fun closeCoulombGas() {
        navigationState.closeCoulombGas()
    }

    fun getCommentsCoordinator(): AndroidCommentsCoordinator? = commentsCoordinator

    fun setStoriesExtraSidePadding(paddingPx: Int) {
        storiesCoordinator?.setExtraSidePadding(paddingPx)
    }

    fun isAdaptiveTwoPane(): Boolean = adaptiveTwoPane

    fun isAdaptiveFoldable(): Boolean = adaptiveFoldable

    fun attachStoriesCoordinator(coordinator: AndroidStoriesCoordinator) {
        storiesCoordinator = coordinator
        coordinator.setHostActive(
            navigationState.state.value.currentDestination == MainDestination.STORIES,
        )
    }

    fun onStart() = storiesCoordinator?.onStart()

    fun onResume() = storiesCoordinator?.onResume()

    fun onStop() = storiesCoordinator?.onStop()

    fun onDestroy() {
        storiesCoordinator?.onDestroy()
        commentsCoordinatorCache.values.toSet().forEach(AndroidCommentsCoordinator::onDestroy)
        commentsCoordinatorCache.clear()
        commentsCoordinatorReferences.clear()
        commentsCoordinator = null
        commentsComposeController = null
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        commentsCoordinator?.onConfigurationChanged(newConfig)
    }

    fun applySettingsChanges() = storiesCoordinator?.onResume()

    internal fun updateVisibleStories(stories: List<StoryListItemSnapshot>) {
        storiesCoordinator?.updateVisibleStories(stories)
    }

    fun attachStoriesScreenController(controller: StoriesScreenController) {
        storiesComposeController = controller
    }

    fun detachStoriesScreenController(controller: StoriesScreenController) {
        if (storiesComposeController === controller) storiesComposeController = null
    }

    internal fun retainCommentsCoordinator(
        activity: MainActivity,
        request: MainStoryRequest,
    ): AndroidCommentsCoordinator {
        val coordinator = commentsCoordinatorCache.getOrPut(request.serial) {
            val stack = navigationState.state.value.destinationStack
            val storyIndex = stack.indexOfFirst {
                it is MainNavigationEntry.Story && it.request.serial == request.serial
            }
            val openedFromSubmissions =
                stack.getOrNull(storyIndex - 1)?.destination == MainDestination.SUBMISSIONS
            Trace.beginSection("CommentsOpen.createCoordinator")
            try {
                AndroidCommentsCoordinator(
                    activity,
                    request.destination,
                    request.serial,
                    consumeCommentsSavedState(request.serial),
                    navigation = this,
                    restorePreviousScrollProgress = !openedFromSubmissions,
                )
            } finally {
                Trace.endSection()
            }
        }
        commentsCoordinatorReferences[request.serial] =
            (commentsCoordinatorReferences[request.serial] ?: 0) + 1
        return coordinator
    }

    internal fun attachCommentsCoordinator(coordinator: AndroidCommentsCoordinator) {
        val navigation = navigationState.state.value
        if (navigation.storyRequest?.serial != coordinator.sessionKey) {
            coordinator.setHostActive(false)
            return
        }
        commentsCoordinator?.takeIf { it !== coordinator }?.setHostActive(false)
        commentsCoordinator = coordinator
        commentsComposeController = coordinator.composeUiController
        coordinator.setHostActive(navigation.currentDestination == MainDestination.STORY)
    }

    internal fun releaseCommentsCoordinator(coordinator: AndroidCommentsCoordinator) {
        val remainingReferences =
            ((commentsCoordinatorReferences[coordinator.sessionKey] ?: 1) - 1).coerceAtLeast(0)
        if (remainingReferences > 0) {
            commentsCoordinatorReferences[coordinator.sessionKey] = remainingReferences
            return
        }
        commentsCoordinatorReferences.remove(coordinator.sessionKey)
        coordinator.onStop()
        coordinator.setHostActive(false)
        if (navigationState.state.value.storyBackStack.any { it.serial == coordinator.sessionKey }) {
            return
        }
        commentsCoordinatorCache.remove(coordinator.sessionKey, coordinator)
        if (commentsCoordinator === coordinator) {
            commentsCoordinator = null
            commentsComposeController = null
        }
        coordinator.onDestroy()
    }

    internal fun updateCommentsHostDestination(destination: MainDestination) {
        // A retained pane may be revealed without recomposing CommentsPane. Resolve the host
        // from the stack here instead of relying on that pane's attachment SideEffect to run.
        val active = navigationState.state.value.storyRequest?.serial
            ?.let(commentsCoordinatorCache::get)
        commentsCoordinator?.takeIf { it !== active }?.setHostActive(false)
        commentsCoordinator = active
        commentsComposeController = active?.composeUiController
        active?.setHostActive(destination == MainDestination.STORY)
    }

    internal fun updateStoriesHostDestination(destination: MainDestination) {
        storiesCoordinator?.setHostActive(destination == MainDestination.STORIES)
    }

    internal fun consumeCommentsSavedState(requestSerial: Int): Bundle? {
        if (restoredCommentsRequestSerial != requestSerial) return null
        return restoredCommentsState?.also {
            restoredCommentsState = null
            restoredCommentsRequestSerial = -1
        }
    }

    fun attachCommentsScreenController(
        coordinator: AndroidCommentsCoordinator,
        controller: CommentsScreenController,
    ) {
        if (navigationState.state.value.storyRequest?.serial == coordinator.sessionKey) {
            commentsComposeController = controller
        }
    }

    fun detachCommentsScreenController(
        coordinator: AndroidCommentsCoordinator,
        controller: CommentsScreenController,
    ) {
        if (commentsCoordinator === coordinator && commentsComposeController === controller) {
            commentsComposeController = null
        }
    }

    internal fun detailRemovedFromBackStack() {
        navigationState.detailRemovedFromBackStack()
        // Keep the outgoing detail content alive until AnimatedVisibility has finished its
        // exit. Clearing the Compose controller here leaves the WebView's white surface as
        // the only outgoing layer for a frame, which flashes over Stories during back.
        // CommentsPane's onDispose owns tearing down and detaching both controllers.
    }

    internal fun updateAdaptiveState(twoPane: Boolean, foldable: Boolean) {
        adaptiveTwoPane = twoPane
        adaptiveFoldable = foldable
        commentsCoordinator?.onAdaptiveLayoutChanged()
    }

    fun saveState(outState: Bundle) {
        storiesCoordinator?.onSaveInstanceState(outState)
        outState.putInt(STATE_EXTERNAL_STORY_SERIAL, externalStorySerial)
        outState.putString(
            STATE_NAVIGATION_RESTORATION,
            MainNavigationRestorationCodec.encode(navigationState.restoration()),
        )
        navigationState.state.value.storyRequest?.let { request ->
            commentsCoordinator?.let { coordinator ->
                val commentsState = Bundle()
                coordinator.onSaveInstanceState(commentsState)
                outState.putInt(STATE_COMMENTS_REQUEST_SERIAL, request.serial)
                outState.putBundle(STATE_COMMENTS_STATE, commentsState)
            }
        }
    }

    private companion object {
        const val STATE_EXTERNAL_STORY_SERIAL = "main_navigation_external_story_serial"
        const val STATE_NAVIGATION_RESTORATION = "main_navigation_restoration_v2"
        const val STATE_REQUEST_SERIAL = "main_navigation_request_serial"
        const val STATE_STORY_ID = "main_navigation_story_id"
        const val STATE_STORY_SHOW_WEBSITE = "main_navigation_story_show_website"
        const val STATE_STORY_SCROLL_TO_COMMENT_ID =
            "main_navigation_story_scroll_to_comment_id"
        // Read-only migration key for state saved by builds before route-only restoration.
        const val STATE_STORY_ARGUMENTS = "main_navigation_story_arguments"
        const val STATE_COMMENTS_REQUEST_SERIAL = "main_navigation_comments_request_serial"
        const val STATE_COMMENTS_STATE = "main_navigation_comments_state"
        const val STATE_SETTINGS_OPEN = "main_navigation_settings_open"
        const val STATE_SETTINGS_REQUEST_SERIAL = "main_navigation_settings_request_serial"
        const val STATE_SETTINGS_SECTION = "main_navigation_settings_section"
        const val STATE_WELCOME_DIALOG_VISIBLE = "main_navigation_welcome_dialog_visible"
        const val STATE_CHANGELOG_DIALOG_VISIBLE = "main_navigation_changelog_dialog_visible"
        const val STATE_CACHE_STORIES_DIALOG_VISIBLE =
            "main_navigation_cache_stories_dialog_visible"
        const val STATE_LOGIN_DIALOG_VISIBLE = "main_navigation_login_dialog_visible"
        const val STATE_USER_DIALOG_SERIAL = "main_navigation_user_dialog_serial"
        const val STATE_USER_DIALOG_NAME = "main_navigation_user_dialog_name"
        const val STATE_EDITOR_REQUEST_SERIAL = "main_navigation_editor_request_serial"
        const val STATE_EDITOR_ARGUMENTS = "main_navigation_editor_arguments"
        const val STATE_SUBMISSIONS_REQUEST_SERIAL = "main_navigation_submissions_request_serial"
        const val STATE_SUBMISSIONS_USER = "main_navigation_submissions_user"
        const val STATE_STORY_OPENED_FROM_SUBMISSIONS =
            "main_navigation_story_opened_from_submissions"
        const val STATE_STORY_OPENED_FROM_SETTINGS = "main_navigation_story_opened_from_settings"
        const val STATE_COULOMB_GAS_VISIBLE = "main_navigation_coulomb_gas_visible"
    }
}
