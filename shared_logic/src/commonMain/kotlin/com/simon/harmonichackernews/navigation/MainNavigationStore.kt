package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainNavigationSnapshot(
    val storyRequest: MainStoryRequest?,
    val lastStoryRequest: MainStoryRequest?,
    val settingsRequest: MainSettingsRequest?,
    val lastSettingsRequest: MainSettingsRequest?,
    val settingsThemeRevision: Int,
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
    val storyOpenedFromSubmissions: Boolean,
    val storyOpenedFromSettings: Boolean,
    val coulombGasVisible: Boolean,
    val closeRequest: Int,
    val settingsRequestSerial: Int,
    val currentSettingsSectionRoute: String?,
    val settingsNeedsRestart: Boolean,
)

/** Observable navigation bridge shared by Compose, SwiftUI and desktop hosts. */
class MainNavigationStore(restored: MainNavigationRestoration = MainNavigationRestoration()) {
    private var machine = MainNavigationState(restored)
    private val mutableState = MutableStateFlow(machine.snapshot())

    val state: StateFlow<MainNavigationSnapshot> = mutableState.asStateFlow()

    val storyRequest get() = state.value.storyRequest
    val lastStoryRequest get() = state.value.lastStoryRequest
    val settingsRequest get() = state.value.settingsRequest
    val lastSettingsRequest get() = state.value.lastSettingsRequest
    val settingsThemeRevision get() = state.value.settingsThemeRevision
    val welcomeDialogVisible get() = state.value.welcomeDialogVisible
    val changelogDialogVisible get() = state.value.changelogDialogVisible
    val cacheStoriesDialogVisible get() = state.value.cacheStoriesDialogVisible
    val loginDialogVisible get() = state.value.loginDialogVisible
    val captchaRequest get() = state.value.captchaRequest
    val userRequest get() = state.value.userRequest
    val failureRequest get() = state.value.failureRequest
    val editorRequest get() = state.value.editorRequest
    val lastEditorRequest get() = state.value.lastEditorRequest
    val submissionsRequest get() = state.value.submissionsRequest
    val lastSubmissionsRequest get() = state.value.lastSubmissionsRequest
    val storyOpenedFromSubmissions get() = state.value.storyOpenedFromSubmissions
    val storyOpenedFromSettings get() = state.value.storyOpenedFromSettings
    val coulombGasVisible get() = state.value.coulombGasVisible
    val closeRequest get() = state.value.closeRequest
    val settingsRequestSerial get() = state.value.settingsRequestSerial
    val currentSettingsSectionRoute get() = state.value.currentSettingsSectionRoute
    val settingsNeedsRestart get() = state.value.settingsNeedsRestart

    fun openStory(destination: StoryDestination) = mutate { openStory(destination) }
    fun openStory(route: StoryRoute) = mutate { openStory(route) }
    fun requestCloseStory() = mutate { requestCloseStory() }
    fun openSettings(sectionRoute: String?) = mutate { openSettings(sectionRoute) }
    fun closeSettings() = mutate { closeSettings() }
    fun updateSettingsSection(route: String) = mutate { updateSettingsSection(route) }
    fun initialSettingsSectionRoute(request: MainSettingsRequest): String? =
        machine.initialSettingsSectionRoute(request)
    fun onSettingsThemeChanged() = mutate { onSettingsThemeChanged() }
    fun requestSettingsRestart() = mutate { requestSettingsRestart() }
    fun consumeSettingsRestartRequest(): Boolean = machine.consumeSettingsRestartRequest().also {
        publish()
    }
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
    fun prepareToOpenStoryFromSubmissions() = mutate { prepareToOpenStoryFromSubmissions() }
    fun openCoulombGas() = mutate { openCoulombGas() }
    fun closeCoulombGas() = mutate { closeCoulombGas() }
    fun detailRemovedFromBackStack() = mutate { detailRemovedFromBackStack() }

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

private fun MainNavigationState.snapshot() = MainNavigationSnapshot(
    storyRequest = storyRequest,
    lastStoryRequest = lastStoryRequest,
    settingsRequest = settingsRequest,
    lastSettingsRequest = lastSettingsRequest,
    settingsThemeRevision = settingsThemeRevision,
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
    storyOpenedFromSubmissions = storyOpenedFromSubmissions,
    storyOpenedFromSettings = storyOpenedFromSettings,
    coulombGasVisible = coulombGasVisible,
    closeRequest = closeRequest,
    settingsRequestSerial = settingsRequestSerial,
    currentSettingsSectionRoute = currentSettingsSectionRoute,
    settingsNeedsRestart = settingsNeedsRestart,
)
