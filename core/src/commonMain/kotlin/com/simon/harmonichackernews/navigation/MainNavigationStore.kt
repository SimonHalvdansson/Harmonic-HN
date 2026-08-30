package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainNavigationSnapshot(
    val destinationStack: List<MainNavigationEntry>,
    val currentDestination: MainDestination,
    val storyRequest: MainStoryRequest?,
    val lastStoryRequest: MainStoryRequest?,
    val settingsRequest: MainSettingsRequest?,
    val lastSettingsRequest: MainSettingsRequest?,
    val welcomeDialogVisible: Boolean,
    val changelogDialogVisible: Boolean,
    val cacheStoriesDialogVisible: Boolean,
    val loginDialogVisible: Boolean,
    val captchaRequest: MainCaptchaRequest?,
    val userRequest: MainUserRequest?,
    val failureRequest: MainFailureRequest?,
    val editorRequest: MainEditorRequest?,
    val lastEditorRequest: MainEditorRequest?,
    val submissionsRequest: MainSubmissionsRequest?,
    val lastSubmissionsRequest: MainSubmissionsRequest?,
    val coulombGasVisible: Boolean,
    val closeRequest: Int,
    val settingsRequestSerial: Int,
    val currentSettingsSectionRoute: String?,
) {
    /** Consecutive story entries ending at the most recently opened story. */
    val storyBackStack: List<MainStoryRequest>
        get() {
            val lastStoryIndex = destinationStack.indexOfLast { it is MainNavigationEntry.Story }
            if (lastStoryIndex < 0) return emptyList()
            var firstStoryIndex = lastStoryIndex
            while (
                firstStoryIndex > 0 &&
                destinationStack[firstStoryIndex - 1] is MainNavigationEntry.Story
            ) {
                firstStoryIndex--
            }
            return destinationStack
                .subList(firstStoryIndex, lastStoryIndex + 1)
                .filterIsInstance<MainNavigationEntry.Story>()
                .map { it.request }
        }

    val storyParentDestination: MainDestination?
        get() = destinationStack
            .indexOfLast { it is MainNavigationEntry.Story }
            .let { storyIndex -> destinationStack.getOrNull(storyIndex - 1)?.destination }

    /** Destination beneath the entire consecutive run of stories containing the current story. */
    val storyStackParentDestination: MainDestination?
        get() {
            var firstStoryIndex = destinationStack.indexOfLast {
                it is MainNavigationEntry.Story
            }
            if (firstStoryIndex < 0) return null
            while (
                firstStoryIndex > 0 &&
                destinationStack[firstStoryIndex - 1] is MainNavigationEntry.Story
            ) {
                firstStoryIndex--
            }
            return destinationStack.getOrNull(firstStoryIndex - 1)?.destination
        }
}

/** Observable navigation bridge shared by Compose, SwiftUI and desktop hosts. */
class MainNavigationStore(restored: MainNavigationRestoration = MainNavigationRestoration()) {
    private var machine = MainNavigationState(restored)
    private val mutableState = MutableStateFlow(machine.snapshot())

    val state: StateFlow<MainNavigationSnapshot> = mutableState.asStateFlow()

    fun openStory(destination: StoryDestination) = mutate { openStory(destination) }
    fun openStory(route: StoryRoute) = mutate { openStory(route) }
    fun openLinkedStory(destination: StoryDestination) = mutate { openLinkedStory(destination) }
    fun requestCloseStory() = mutate { requestCloseStory() }
    fun openSettings(sectionRoute: String?) = mutate { openSettings(sectionRoute) }
    fun closeSettings() = mutate { closeSettings() }
    fun updateSettingsSection(route: String) = mutate { updateSettingsSection(route) }
    fun initialSettingsSectionRoute(request: MainSettingsRequest): String? =
        machine.initialSettingsSectionRoute(request)
    fun showWelcomeDialog() = mutate { showWelcomeDialog() }
    fun dismissWelcomeDialog() = mutate { dismissWelcomeDialog() }
    fun showChangelogDialog() = mutate { showChangelogDialog() }
    fun dismissChangelogDialog() = mutate { dismissChangelogDialog() }
    fun showCacheStoriesDialog() = mutate { showCacheStoriesDialog() }
    fun dismissCacheStoriesDialog() = mutate { dismissCacheStoriesDialog() }
    fun showLoginDialog() = mutate { showLoginDialog() }
    fun dismissLoginDialog() = mutate { dismissLoginDialog() }
    fun showCaptchaDialog(challenge: HackerNewsCaptchaChallenge): MainCaptchaRequest =
        machine.showCaptchaDialog(challenge).also { publish() }
    fun dismissCaptchaDialog(): MainCaptchaRequest? = machine.dismissCaptchaDialog().also {
        publish()
    }
    fun showUserDialog(userName: String): MainUserRequest? =
        machine.showUserDialog(userName).also { publish() }
    fun dismissUserDialog(): MainUserRequest? = machine.dismissUserDialog().also { publish() }
    fun showFailureDetailDialog(
        title: String?,
        message: String?,
        clipboardText: String?,
    ): MainFailureRequest = machine.showFailureDetailDialog(title, message, clipboardText).also {
        publish()
    }
    fun dismissFailureDetailDialog(): MainFailureRequest? =
        machine.dismissFailureDetailDialog().also { publish() }
    fun openEditor(destination: EditorDestination) = mutate { openEditor(destination) }
    fun closeEditor() = mutate { closeEditor() }
    fun openSubmissions(userName: String) = mutate { openSubmissions(userName) }
    fun closeSubmissions() = mutate { closeSubmissions() }
    fun openCoulombGas() = mutate { openCoulombGas() }
    fun closeCoulombGas() = mutate { closeCoulombGas() }
    fun detailRemovedFromBackStack() = mutate { detailRemovedFromBackStack() }

    /** Route-only lifecycle snapshot; transient story presentation data is deliberately omitted. */
    fun restoration(): MainNavigationRestoration = state.value.toRestoration(
        destinationStack = machine.destinationRestoration(),
    )

    /** Rehydrates the application-scoped store before a host starts rendering it. */
    fun restore(restored: MainNavigationRestoration) {
        machine = MainNavigationState(restored)
        publish()
    }

    private inline fun mutate(action: MainNavigationState.() -> Unit) {
        machine.action()
        publish()
    }

    private fun publish() {
        mutableState.value = machine.snapshot()
    }
}

private fun MainNavigationSnapshot.toRestoration(
    destinationStack: List<MainNavigationEntryRestoration>,
): MainNavigationRestoration = MainNavigationRestoration(
    destinationStack = destinationStack,
    storyRoute = storyRequest?.route,
    storyRequestSerial = storyRequest?.serial ?: 0,
    settingsOpen = settingsRequest != null,
    settingsRequestSerial = settingsRequestSerial,
    settingsSectionRoute = currentSettingsSectionRoute,
    welcomeDialogVisible = welcomeDialogVisible,
    changelogDialogVisible = changelogDialogVisible,
    cacheStoriesDialogVisible = cacheStoriesDialogVisible,
    loginDialogVisible = loginDialogVisible,
    userDialogUserName = userRequest?.userName,
    userDialogSerial = userRequest?.serial ?: 0,
    editorDestination = editorRequest?.destination,
    editorRequestSerial = editorRequest?.serial ?: 0,
    submissionsUserName = submissionsRequest?.userName,
    submissionsRequestSerial = submissionsRequest?.serial ?: 0,
    coulombGasVisible = coulombGasVisible,
)

private fun MainNavigationState.snapshot() = MainNavigationSnapshot(
    destinationStack = destinationStack,
    currentDestination = currentDestination,
    storyRequest = storyRequest,
    lastStoryRequest = lastStoryRequest,
    settingsRequest = settingsRequest,
    lastSettingsRequest = lastSettingsRequest,
    welcomeDialogVisible = welcomeDialogVisible,
    changelogDialogVisible = changelogDialogVisible,
    cacheStoriesDialogVisible = cacheStoriesDialogVisible,
    loginDialogVisible = loginDialogVisible,
    captchaRequest = captchaRequest,
    userRequest = userRequest,
    failureRequest = failureRequest,
    editorRequest = editorRequest,
    lastEditorRequest = lastEditorRequest,
    submissionsRequest = submissionsRequest,
    lastSubmissionsRequest = lastSubmissionsRequest,
    coulombGasVisible = coulombGasVisible,
    closeRequest = closeRequest,
    settingsRequestSerial = settingsRequestSerial,
    currentSettingsSectionRoute = currentSettingsSectionRoute,
)
