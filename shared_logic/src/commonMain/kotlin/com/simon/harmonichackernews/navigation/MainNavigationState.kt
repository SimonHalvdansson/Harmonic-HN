package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class MainStoryRequest(
    val serial: Int,
    val destination: StoryDestination,
) {
    val storyId: Int = destination.storyId
    val route: StoryRoute = destination.route
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

/** Portable CAPTCHA dialog state. Platform callbacks deliberately stay in the host shell. */
data class MainCaptchaRequest(
    val serial: Int,
    val challenge: HackerNewsCaptchaChallenge,
)

/** Portable user-dialog state. Platform tag-change callbacks deliberately stay in the host. */
data class MainUserRequest(
    val serial: Int,
    val userName: String,
)

data class MainFailureRequest(
    val serial: Int,
    val title: String?,
    val message: String?,
    val clipboardText: String?,
)

@Serializable
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
    val userDialogUserName: String? = null,
    val userDialogSerial: Int = 0,
    val editorDestination: EditorDestination? = null,
    val editorRequestSerial: Int = 0,
    val submissionsUserName: String? = null,
    val submissionsRequestSerial: Int = 0,
    val storyOpenedFromSubmissions: Boolean = false,
    val storyOpenedFromSettings: Boolean = false,
    val coulombGasVisible: Boolean = false,
    val storyRoute: StoryRoute? = storyDestination?.route,
)

/** Stable, platform-independent encoding stored by Android, iOS, and desktop lifecycle hosts. */
object MainNavigationRestorationCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(restoration: MainNavigationRestoration): String =
        json.encodeToString(MainNavigationRestoration.serializer(), restoration)

    fun decode(value: String?): MainNavigationRestoration? = value
        ?.takeIf(String::isNotBlank)
        ?.let { encoded ->
            runCatching {
                json.decodeFromString(MainNavigationRestoration.serializer(), encoded)
            }.getOrNull()
        }
}

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
    var captchaRequest: MainCaptchaRequest? = null
        private set
    var userRequest: MainUserRequest? = null
        private set
    var failureRequest: MainFailureRequest? = null
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
    private var captchaRequestSerial = 0
    private var userRequestSerial = 0
    private var failureRequestSerial = 0
    var currentSettingsSectionRoute: String? = null
        private set
    private var settingsThemeChangedRequestSerial = -1
    var settingsNeedsRestart: Boolean = false
        private set

    init {
        (restored.storyDestination ?: restored.storyRoute?.toDestination())?.let { destination ->
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
        restored.userDialogUserName?.takeIf(String::isNotBlank)?.let { userName ->
            userRequestSerial = restored.userDialogSerial.coerceAtLeast(1)
            userRequest = MainUserRequest(userRequestSerial, userName)
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

    fun openStory(route: StoryRoute) = openStory(route.toDestination())

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

    fun showCaptchaDialog(challenge: HackerNewsCaptchaChallenge): MainCaptchaRequest =
        MainCaptchaRequest(++captchaRequestSerial, challenge).also { captchaRequest = it }

    fun dismissCaptchaDialog(): MainCaptchaRequest? = captchaRequest.also {
        captchaRequest = null
    }

    fun showUserDialog(userName: String): MainUserRequest? {
        if (userName.isBlank()) return null
        return MainUserRequest(++userRequestSerial, userName).also { userRequest = it }
    }

    fun dismissUserDialog(): MainUserRequest? = userRequest.also { userRequest = null }

    fun showFailureDetailDialog(
        title: String?,
        message: String?,
        clipboardText: String?,
    ): MainFailureRequest = MainFailureRequest(
        serial = ++failureRequestSerial,
        title = title,
        message = message,
        clipboardText = clipboardText,
    ).also { failureRequest = it }

    fun dismissFailureDetailDialog(): MainFailureRequest? = failureRequest.also {
        failureRequest = null
    }

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
