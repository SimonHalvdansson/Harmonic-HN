package com.simon.harmonichackernews.ui.navigation

import android.content.res.Configuration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
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
import com.simon.harmonichackernews.presentation.CommentsSessionState
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationEntry
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
    private var storiesCoordinator: AndroidStoriesCoordinator? = null
    private var commentsCoordinator: AndroidCommentsCoordinator? = null
    private val commentsCoordinatorCache = mutableMapOf<Int, AndroidCommentsCoordinator>()
    private val commentsCoordinatorReferences = mutableMapOf<Int, Int>()
    internal var storiesComposeController by mutableStateOf<StoriesScreenController?>(null)
        private set
    internal var commentsComposeController by mutableStateOf<CommentsScreenController?>(null)
        private set
    private var adaptiveTwoPane = false
    private var adaptiveFoldable = false
    private var externalStorySerial by mutableIntStateOf(
        savedState?.getBundle(STATE_EXTERNAL_STORY_SERIAL)?.let { decodeFromSavedState<Int>(it) } ?: -1,
    )

    internal fun markExternalStoryEntry() {
        externalStorySerial = navigationState.state.value.storyRequest?.serial ?: -1
    }

    internal val externalStoryEntrySerial: Int? get() = externalStorySerial.takeIf { it >= 0 }

    internal val isExternalStoryEntry: Boolean
        get() = navigationState.state.value.currentDestination == MainDestination.STORY &&
            navigationState.state.value.storyRequest?.serial == externalStorySerial

    fun openStory(destination: StoryDestination) {
        if (
            navigationState.state.value.currentDestination == MainDestination.STORY &&
            commentsCoordinator?.switchStoryViewIfMatching(
                destination.storyId,
                destination.showWebsite,
            ) == true
        ) return
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
        sessionState: CommentsSessionState,
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
                    sessionState,
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
        val state = navigationState.state.value
        val retainedSerials = state.storyBackStack.mapTo(mutableSetOf()) { it.serial }
        // A hidden tablet detail can leave composition while still in the back stack. If a
        // later selection removes that history, it will never receive another onDispose.
        // Keep composed exits alive until their animation releases the last UI reference.
        val discarded = commentsCoordinatorCache.values.filter {
            it.sessionKey !in retainedSerials &&
                (commentsCoordinatorReferences[it.sessionKey] ?: 0) == 0
        }
        discarded.forEach {
            commentsCoordinatorCache.remove(it.sessionKey)
            it.onDestroy()
        }
        // A retained pane may be revealed without recomposing CommentsPane. Resolve the host
        // from the stack here instead of relying on that pane's attachment SideEffect to run.
        val active = state.storyRequest?.serial
            ?.let(commentsCoordinatorCache::get)
        commentsCoordinator?.takeIf { it !== active }?.setHostActive(false)
        commentsCoordinator = active
        commentsComposeController = active?.composeUiController
        active?.setHostActive(destination == MainDestination.STORY)
    }

    internal fun updateStoriesHostDestination(destination: MainDestination) {
        storiesCoordinator?.setHostActive(destination == MainDestination.STORIES)
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
        outState.putBundle(STATE_EXTERNAL_STORY_SERIAL, encodeToSavedState(externalStorySerial))
        commentsCoordinatorCache.values.forEach { it.captureSavedState() }
    }

    private companion object {
        const val STATE_EXTERNAL_STORY_SERIAL = "main_navigation_external_story_serial"
    }
}
