package com.simon.harmonichackernews.navigation

data class MainStoryRequest(
    val serial: Int,
    val destination: StoryDestination,
) {
    val storyId: Int = destination.storyId
}

data class MainSettingsRequest(
    val serial: Int,
    val initialSectionRoute: String?,
)

data class MainEditorRequest(
    val serial: Int,
    val destination: EditorDestination,
)

data class MainSubmissionsRequest(
    val serial: Int,
    val userName: String,
)

data class MainNavigationRestoration(
    val storyDestination: StoryDestination? = null,
    val storyRequestSerial: Int = 0,
    val settingsOpen: Boolean = false,
    val settingsRequestSerial: Int = 0,
    val settingsSectionRoute: String? = null,
    val settingsNeedsRestart: Boolean = false,
    val welcomeDialogVisible: Boolean = false,
    val changelogDialogVisible: Boolean = false,
    val cacheStoriesDialogVisible: Boolean = false,
    val loginDialogVisible: Boolean = false,
    val editorDestination: EditorDestination? = null,
    val editorRequestSerial: Int = 0,
    val submissionsUserName: String? = null,
    val submissionsRequestSerial: Int = 0,
    val storyOpenedFromSubmissions: Boolean = false,
    val storyOpenedFromSettings: Boolean = false,
    val coulombGasVisible: Boolean = false,
)

/** Pure navigation state and transition policy shared by every platform shell. */
class MainNavigationState(restored: MainNavigationRestoration = MainNavigationRestoration()) {
    var storyRequest: MainStoryRequest? = null
        private set
    var lastStoryRequest: MainStoryRequest? = null
        private set
    var settingsRequest: MainSettingsRequest? = null
        private set
    var lastSettingsRequest: MainSettingsRequest? = null
        private set
    var settingsThemeRevision: Int = 0
        private set
    var welcomeDialogVisible: Boolean = restored.welcomeDialogVisible
        private set
    var changelogDialogVisible: Boolean = restored.changelogDialogVisible
        private set
    var cacheStoriesDialogVisible: Boolean = restored.cacheStoriesDialogVisible
        private set
    var loginDialogVisible: Boolean = restored.loginDialogVisible
        private set
    var editorRequest: MainEditorRequest? = null
        private set
    var lastEditorRequest: MainEditorRequest? = null
        private set
    var submissionsRequest: MainSubmissionsRequest? = null
        private set
    var lastSubmissionsRequest: MainSubmissionsRequest? = null
        private set
    var storyOpenedFromSubmissions: Boolean = false
        private set
    var storyOpenedFromSettings: Boolean = false
        private set
    var coulombGasVisible: Boolean = restored.coulombGasVisible
        private set
    var closeRequest: Int = 0
        private set

    private var storyRequestSerial = 0
    var settingsRequestSerial: Int = 0
        private set
    private var editorRequestSerial = 0
    private var submissionsRequestSerial = 0
    var currentSettingsSectionRoute: String? = null
        private set
    private var settingsThemeChangedRequestSerial = -1
    var settingsNeedsRestart: Boolean = false
        private set

    init {
        restored.storyDestination?.let { destination ->
            storyRequestSerial = restored.storyRequestSerial.coerceAtLeast(1)
            MainStoryRequest(storyRequestSerial, destination).also { request ->
                storyRequest = request
                lastStoryRequest = request
            }
        }
        if (restored.settingsOpen) {
            settingsRequestSerial = restored.settingsRequestSerial.coerceAtLeast(1)
            currentSettingsSectionRoute = restored.settingsSectionRoute
            MainSettingsRequest(settingsRequestSerial, currentSettingsSectionRoute).also { request ->
                settingsRequest = request
                lastSettingsRequest = request
            }
            settingsNeedsRestart = restored.settingsNeedsRestart
        }
        restored.editorDestination?.let { destination ->
            editorRequestSerial = restored.editorRequestSerial.coerceAtLeast(1)
            MainEditorRequest(editorRequestSerial, destination).also { request ->
                editorRequest = request
                lastEditorRequest = request
            }
        }
        restored.submissionsUserName?.let { userName ->
            submissionsRequestSerial = restored.submissionsRequestSerial.coerceAtLeast(1)
            MainSubmissionsRequest(submissionsRequestSerial, userName).also { request ->
                submissionsRequest = request
                lastSubmissionsRequest = request
            }
        }
        storyOpenedFromSubmissions = restored.storyOpenedFromSubmissions &&
            storyRequest != null && submissionsRequest != null
        storyOpenedFromSettings = restored.storyOpenedFromSettings &&
            storyRequest != null && settingsRequest != null
    }

    fun openStory(destination: StoryDestination) {
        if (settingsRequest != null) {
            storyOpenedFromSettings = true
            settingsRequest = null
        }
        if (!storyOpenedFromSubmissions) submissionsRequest = null
        editorRequest = null
        coulombGasVisible = false
        MainStoryRequest(++storyRequestSerial, destination).also { request ->
            lastStoryRequest = request
            storyRequest = request
        }
    }

    fun requestCloseStory() {
        closeRequest++
    }

    fun openSettings(sectionRoute: String?) {
        submissionsRequest = null
        storyOpenedFromSubmissions = false
        storyOpenedFromSettings = false
        editorRequest = null
        coulombGasVisible = false
        currentSettingsSectionRoute = sectionRoute
        MainSettingsRequest(++settingsRequestSerial, sectionRoute).also { request ->
            settingsRequest = request
            lastSettingsRequest = request
        }
    }

    fun closeSettings() {
        settingsRequest = null
    }

    fun updateSettingsSection(route: String) {
        currentSettingsSectionRoute = route
    }

    fun initialSettingsSectionRoute(request: MainSettingsRequest): String? =
        if (settingsThemeChangedRequestSerial == request.serial) {
            currentSettingsSectionRoute
        } else {
            request.initialSectionRoute
        }

    fun onSettingsThemeChanged() {
        settingsThemeChangedRequestSerial = settingsRequest?.serial ?: -1
        settingsNeedsRestart = true
        settingsThemeRevision++
    }

    fun requestSettingsRestart() {
        settingsNeedsRestart = true
    }

    fun consumeSettingsRestartRequest(): Boolean = settingsNeedsRestart.also {
        settingsNeedsRestart = false
    }

    fun showWelcomeDialog() { welcomeDialogVisible = true }
    fun dismissWelcomeDialog() { welcomeDialogVisible = false }
    fun showChangelogDialog() { changelogDialogVisible = true }
    fun dismissChangelogDialog() { changelogDialogVisible = false }
    fun showCacheStoriesDialog() { cacheStoriesDialogVisible = true }
    fun dismissCacheStoriesDialog() { cacheStoriesDialogVisible = false }
    fun showLoginDialog() { loginDialogVisible = true }
    fun dismissLoginDialog() { loginDialogVisible = false }

    fun openEditor(destination: EditorDestination) {
        settingsRequest = null
        submissionsRequest = null
        storyOpenedFromSubmissions = false
        storyOpenedFromSettings = false
        coulombGasVisible = false
        MainEditorRequest(++editorRequestSerial, destination).also { request ->
            editorRequest = request
            lastEditorRequest = request
        }
    }

    fun closeEditor() { editorRequest = null }

    fun openSubmissions(userName: String) {
        editorRequest = null
        coulombGasVisible = false
        MainSubmissionsRequest(++submissionsRequestSerial, userName).also { request ->
            submissionsRequest = request
            lastSubmissionsRequest = request
        }
    }

    fun closeSubmissions() {
        submissionsRequest = null
        storyOpenedFromSubmissions = false
    }

    fun prepareToOpenStoryFromSubmissions() {
        if (submissionsRequest != null) storyOpenedFromSubmissions = true
    }

    fun openCoulombGas() {
        submissionsRequest = null
        storyOpenedFromSubmissions = false
        editorRequest = null
        coulombGasVisible = true
    }

    fun closeCoulombGas() { coulombGasVisible = false }

    fun detailRemovedFromBackStack() {
        val restoreSettings = storyOpenedFromSettings
        val restoreSettingsSection = currentSettingsSectionRoute
        storyRequest = null
        storyOpenedFromSubmissions = false
        storyOpenedFromSettings = false
        if (restoreSettings) openSettings(restoreSettingsSection)
    }
}
