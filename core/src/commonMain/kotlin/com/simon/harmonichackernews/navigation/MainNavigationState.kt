package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.serialization.Serializable

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

data class MainWelcomeRequest(val serial: Int, val styleChooser: Boolean = false)

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
enum class MainDestination {
    STORIES,
    STORY,
    SETTINGS,
    SUBMISSIONS,
    EDITOR,
    IMMERSIVE,
    WELCOME,
}

sealed interface MainNavigationEntry {
    val destination: MainDestination

    data object Stories : MainNavigationEntry {
        override val destination = MainDestination.STORIES
    }

    data class Story(val request: MainStoryRequest) : MainNavigationEntry {
        override val destination = MainDestination.STORY
    }

    data class Settings(val request: MainSettingsRequest) : MainNavigationEntry {
        override val destination = MainDestination.SETTINGS
    }

    data class Submissions(val request: MainSubmissionsRequest) : MainNavigationEntry {
        override val destination = MainDestination.SUBMISSIONS
    }

    data class Editor(val request: MainEditorRequest) : MainNavigationEntry {
        override val destination = MainDestination.EDITOR
    }

    data object Immersive : MainNavigationEntry {
        override val destination = MainDestination.IMMERSIVE
    }

    data class Welcome(val request: MainWelcomeRequest) : MainNavigationEntry {
        override val destination = MainDestination.WELCOME
    }
}

@Serializable
data class MainNavigationEntryRestoration(
    val destination: MainDestination,
    val serial: Int = 0,
    val storyRoute: StoryRoute? = null,
    val settingsSectionRoute: String? = null,
    val submissionsUserName: String? = null,
    val editorDestination: EditorDestination? = null,
    val welcomeStyleChooser: Boolean = false,
)

@Serializable
data class MainNavigationRestoration(
    val destinationStack: List<MainNavigationEntryRestoration> = emptyList(),
    val storyDestination: StoryDestination? = null,
    val storyRequestSerial: Int = 0,
    val settingsOpen: Boolean = false,
    val settingsRequestSerial: Int = 0,
    val settingsSectionRoute: String? = null,
    // Read only to migrate saved states from before Welcome became a navigation destination.
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

/** Pure navigation state and transition policy shared by every platform shell. */
class MainNavigationState(restored: MainNavigationRestoration = MainNavigationRestoration()) {
    private val backStack = mutableListOf<MainNavigationEntry>(MainNavigationEntry.Stories)

    val destinationStack: List<MainNavigationEntry>
        get() = backStack.toList()
    val currentDestination: MainDestination
        get() = backStack.last().destination
    val storyRequest: MainStoryRequest?
        get() = backStack.filterIsInstance<MainNavigationEntry.Story>().lastOrNull()?.request
    var lastStoryRequest: MainStoryRequest? = null
        private set
    val settingsRequest: MainSettingsRequest?
        get() = backStack.filterIsInstance<MainNavigationEntry.Settings>().lastOrNull()?.request
    var lastSettingsRequest: MainSettingsRequest? = null
        private set
    val welcomeRequest: MainWelcomeRequest?
        get() = backStack.filterIsInstance<MainNavigationEntry.Welcome>().lastOrNull()?.request
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
    val editorRequest: MainEditorRequest?
        get() = backStack.filterIsInstance<MainNavigationEntry.Editor>().lastOrNull()?.request
    var lastEditorRequest: MainEditorRequest? = null
        private set
    val submissionsRequest: MainSubmissionsRequest?
        get() = backStack.filterIsInstance<MainNavigationEntry.Submissions>().lastOrNull()?.request
    var lastSubmissionsRequest: MainSubmissionsRequest? = null
        private set
    val coulombGasVisible: Boolean
        get() = currentDestination == MainDestination.IMMERSIVE
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
    private var welcomeRequestSerial = 0
    var currentSettingsSectionRoute: String? = null
        private set

    init {
        currentSettingsSectionRoute = restored.settingsSectionRoute
        val restoredEntries = restored.destinationStack.mapNotNull(::restoreEntry)
        if (restoredEntries.isNotEmpty()) {
            backStack.clear()
            backStack += MainNavigationEntry.Stories
            backStack += restoredEntries.filterNot { it.destination == MainDestination.STORIES }
        } else {
            restoreLegacyStack(restored)
        }
        // Older saved states stored welcome separately from the destination history.
        if (restored.welcomeDialogVisible && welcomeRequest == null) openWelcome()
        lastStoryRequest = storyRequest
        lastSettingsRequest = settingsRequest
        lastEditorRequest = editorRequest
        lastSubmissionsRequest = submissionsRequest
        restored.userDialogUserName?.takeIf(String::isNotBlank)?.let { userName ->
            userRequestSerial = restored.userDialogSerial.coerceAtLeast(1)
            userRequest = MainUserRequest(userRequestSerial, userName)
        }
    }

    fun openStory(destination: StoryDestination) {
        MainStoryRequest(++storyRequestSerial, destination).also { request ->
            lastStoryRequest = request
            replaceTopOrPush(MainNavigationEntry.Story(request))
        }
    }

    fun openStory(route: StoryRoute) = openStory(route.toDestination())

    /** Opens a story reached from content while retaining the current story as its back target. */
    fun openLinkedStory(destination: StoryDestination) {
        MainStoryRequest(++storyRequestSerial, destination).also { request ->
            lastStoryRequest = request
            backStack += MainNavigationEntry.Story(request)
        }
    }

    fun requestCloseStory() {
        closeRequest++
    }

    fun returnToStories() {
        backStack.removeAll { it.destination != MainDestination.STORIES }
    }

    fun openSettings(sectionRoute: String?) {
        currentSettingsSectionRoute = sectionRoute
        MainSettingsRequest(++settingsRequestSerial, sectionRoute).also { request ->
            lastSettingsRequest = request
            replaceTopOrPush(MainNavigationEntry.Settings(request))
        }
    }

    fun closeSettings() {
        pop(MainDestination.SETTINGS)
    }

    fun updateSettingsSection(route: String) {
        currentSettingsSectionRoute = route
    }

    fun initialSettingsSectionRoute(request: MainSettingsRequest): String? =
        request.initialSectionRoute

    fun openWelcome(styleChooser: Boolean = false) {
        if (currentDestination == MainDestination.WELCOME && welcomeRequest?.styleChooser == styleChooser) return
        replaceTopOrPush(MainNavigationEntry.Welcome(MainWelcomeRequest(++welcomeRequestSerial, styleChooser)))
    }
    fun closeWelcome() { pop(MainDestination.WELCOME) }
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
        MainEditorRequest(++editorRequestSerial, destination).also { request ->
            lastEditorRequest = request
            replaceTopOrPush(MainNavigationEntry.Editor(request))
        }
    }

    fun closeEditor() { pop(MainDestination.EDITOR) }

    fun openSubmissions(userName: String) {
        MainSubmissionsRequest(++submissionsRequestSerial, userName).also { request ->
            lastSubmissionsRequest = request
            replaceTopOrPush(MainNavigationEntry.Submissions(request))
        }
    }

    fun closeSubmissions() {
        val index = backStack.indexOfLast { it is MainNavigationEntry.Submissions }
        if (index < 1) return
        // In a split layout the list's Back button remains visible beside its story.
        // Close that entire surface, including linked stories, but never a covering screen.
        if (backStack.drop(index + 1).all { it is MainNavigationEntry.Story }) {
            backStack.subList(index, backStack.size).clear()
        }
    }

    fun openCoulombGas() {
        if (currentDestination != MainDestination.IMMERSIVE) {
            backStack += MainNavigationEntry.Immersive
        }
    }

    fun closeCoulombGas() { pop(MainDestination.IMMERSIVE) }

    fun detailRemovedFromBackStack() {
        pop(MainDestination.STORY)
    }

    private fun replaceTopOrPush(entry: MainNavigationEntry) {
        if (currentDestination == entry.destination) {
            backStack[backStack.lastIndex] = entry
        } else {
            backStack += entry
        }
    }

    fun destinationRestoration(): List<MainNavigationEntryRestoration> = backStack.map { entry ->
        when (entry) {
            MainNavigationEntry.Stories -> MainNavigationEntryRestoration(MainDestination.STORIES)
            is MainNavigationEntry.Story -> MainNavigationEntryRestoration(
                destination = MainDestination.STORY,
                serial = entry.request.serial,
                storyRoute = entry.request.route,
            )
            is MainNavigationEntry.Settings -> MainNavigationEntryRestoration(
                destination = MainDestination.SETTINGS,
                serial = entry.request.serial,
                settingsSectionRoute = currentSettingsSectionRoute,
            )
            is MainNavigationEntry.Submissions -> MainNavigationEntryRestoration(
                destination = MainDestination.SUBMISSIONS,
                serial = entry.request.serial,
                submissionsUserName = entry.request.userName,
            )
            is MainNavigationEntry.Editor -> MainNavigationEntryRestoration(
                destination = MainDestination.EDITOR,
                serial = entry.request.serial,
                editorDestination = entry.request.destination,
            )
            MainNavigationEntry.Immersive ->
                MainNavigationEntryRestoration(MainDestination.IMMERSIVE)
            is MainNavigationEntry.Welcome -> MainNavigationEntryRestoration(
                destination = MainDestination.WELCOME,
                serial = entry.request.serial,
                welcomeStyleChooser = entry.request.styleChooser,
            )
        }
    }

    private fun pop(destination: MainDestination) {
        if (currentDestination == destination && backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun restoreEntry(
        restored: MainNavigationEntryRestoration,
    ): MainNavigationEntry? = when (restored.destination) {
        MainDestination.STORIES -> MainNavigationEntry.Stories
        MainDestination.STORY -> restored.storyRoute?.let { route ->
            val serial = restored.serial.coerceAtLeast(1)
            storyRequestSerial = maxOf(storyRequestSerial, serial)
            MainNavigationEntry.Story(MainStoryRequest(serial, route.toDestination()))
        }
        MainDestination.SETTINGS -> {
            val serial = restored.serial.coerceAtLeast(1)
            settingsRequestSerial = maxOf(settingsRequestSerial, serial)
            currentSettingsSectionRoute = restored.settingsSectionRoute
            MainNavigationEntry.Settings(
                MainSettingsRequest(serial, restored.settingsSectionRoute),
            )
        }
        MainDestination.SUBMISSIONS -> restored.submissionsUserName
            ?.takeIf(String::isNotBlank)
            ?.let { userName ->
                val serial = restored.serial.coerceAtLeast(1)
                submissionsRequestSerial = maxOf(submissionsRequestSerial, serial)
                MainNavigationEntry.Submissions(MainSubmissionsRequest(serial, userName))
            }
        MainDestination.EDITOR -> restored.editorDestination
            ?.takeIf { it.isValid }
            ?.let { destination ->
                val serial = restored.serial.coerceAtLeast(1)
                editorRequestSerial = maxOf(editorRequestSerial, serial)
                MainNavigationEntry.Editor(MainEditorRequest(serial, destination))
            }
        MainDestination.IMMERSIVE -> MainNavigationEntry.Immersive
        MainDestination.WELCOME -> {
            val serial = restored.serial.coerceAtLeast(1)
            welcomeRequestSerial = maxOf(welcomeRequestSerial, serial)
            MainNavigationEntry.Welcome(MainWelcomeRequest(serial, restored.welcomeStyleChooser))
        }
    }

    private fun restoreLegacyStack(restored: MainNavigationRestoration) {
        val story = (restored.storyDestination ?: restored.storyRoute?.toDestination())
            ?.let { destination ->
                storyRequestSerial = restored.storyRequestSerial.coerceAtLeast(1)
                MainNavigationEntry.Story(MainStoryRequest(storyRequestSerial, destination))
            }
        val settings = if (restored.settingsOpen) {
            settingsRequestSerial = restored.settingsRequestSerial.coerceAtLeast(1)
            MainNavigationEntry.Settings(
                MainSettingsRequest(settingsRequestSerial, restored.settingsSectionRoute),
            )
        } else {
            null
        }
        val submissions = restored.submissionsUserName
            ?.takeIf(String::isNotBlank)
            ?.let { userName ->
                submissionsRequestSerial = restored.submissionsRequestSerial.coerceAtLeast(1)
                MainNavigationEntry.Submissions(
                    MainSubmissionsRequest(submissionsRequestSerial, userName),
                )
            }

        when {
            story != null && restored.storyOpenedFromSettings && settings != null -> {
                backStack += settings
                backStack += story
            }
            story != null && restored.storyOpenedFromSubmissions && submissions != null -> {
                backStack += submissions
                backStack += story
            }
            else -> {
                story?.let(backStack::add)
                settings?.let(backStack::add)
                submissions?.let(backStack::add)
            }
        }
        restored.editorDestination?.takeIf { it.isValid }?.let { destination ->
            editorRequestSerial = restored.editorRequestSerial.coerceAtLeast(1)
            backStack += MainNavigationEntry.Editor(
                MainEditorRequest(editorRequestSerial, destination),
            )
        }
        if (restored.coulombGasVisible) backStack += MainNavigationEntry.Immersive
    }
}
