package com.simon.harmonichackernews.ui.navigation

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.CommentsCoordinator
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.StoriesCoordinator
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.comments.CommentLinkPreviewOverlay
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsScaffold
import com.simon.harmonichackernews.ui.common.CaptchaDialog
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.ui.common.FailureDetailDialog
import com.simon.harmonichackernews.ui.common.LoginDialog
import com.simon.harmonichackernews.ui.debug.CoulombGasScreen
import com.simon.harmonichackernews.ui.editor.ComposeEditorController
import com.simon.harmonichackernews.ui.editor.ComposeEditorCoordinator
import com.simon.harmonichackernews.ui.editor.ComposeEditorScreen
import com.simon.harmonichackernews.ui.settings.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.settings.SettingsChangelogDialog
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SettingsShell
import com.simon.harmonichackernews.ui.settings.UserSettingsDialog
import com.simon.harmonichackernews.ui.settings.WelcomeSettingsDialog
import com.simon.harmonichackernews.ui.submissions.SubmissionsCoordinator
import com.simon.harmonichackernews.ui.submissions.SubmissionsScreen
import com.simon.harmonichackernews.ui.stories.CacheStoriesDialog
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesScreen
import com.simon.harmonichackernews.ui.stories.StoryPreviewOverlay
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.data.toBundle
import com.simon.harmonichackernews.data.toEditorDestination
import com.simon.harmonichackernews.data.toStoryDestinationOrNull
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data object StoriesDestination : NavKey

private data class CommentsDestination(
    val request: MainStoryRequest,
) : NavKey

internal data class MainStoryRequest(
    val serial: Int,
    val destination: StoryDestination,
) {
    val storyId: Int = destination.storyId
}

internal data class MainSettingsRequest(
    val serial: Int,
    val initialSectionRoute: String?,
)

internal data class MainEditorRequest(
    val serial: Int,
    val destination: EditorDestination,
)

internal data class MainSubmissionsRequest(
    val serial: Int,
    val userName: String,
)

internal data class MainCaptchaRequest(
    val serial: Int,
    val challenge: HackerNewsCaptchaChallenge,
    val callback: CaptchaResultCallback,
)

internal data class MainUserRequest(
    val serial: Int,
    val userName: String,
    val onTagChanged: Runnable?,
)

internal data class MainFailureRequest(
    val serial: Int,
    val title: String?,
    val message: String?,
    val clipboardText: String?,
)

/**
 * Imperative bridge used by the existing story-loading controller while Navigation 3 owns the
 * actual list/detail history and adaptive layout.
 */
@Stable
class MainNavigationController internal constructor(savedState: Bundle? = null) {
    internal var storyRequest by mutableStateOf<MainStoryRequest?>(null)
        private set
    internal var lastStoryRequest: MainStoryRequest? = null
        private set
    internal var settingsRequest by mutableStateOf<MainSettingsRequest?>(null)
        private set
    internal var lastSettingsRequest: MainSettingsRequest? = null
        private set
    internal var settingsThemeRevision by mutableIntStateOf(0)
        private set
    internal var welcomeDialogVisible by mutableStateOf(false)
        private set
    internal var changelogDialogVisible by mutableStateOf(false)
        private set
    internal var cacheStoriesDialogVisible by mutableStateOf(false)
        private set
    internal var loginDialogVisible by mutableStateOf(false)
        private set
    internal var captchaRequest by mutableStateOf<MainCaptchaRequest?>(null)
        private set
    internal var userRequest by mutableStateOf<MainUserRequest?>(null)
        private set
    internal var failureRequest by mutableStateOf<MainFailureRequest?>(null)
        private set
    internal var editorRequest by mutableStateOf<MainEditorRequest?>(null)
        private set
    internal var lastEditorRequest: MainEditorRequest? = null
        private set
    internal var submissionsRequest by mutableStateOf<MainSubmissionsRequest?>(null)
        private set
    internal var lastSubmissionsRequest: MainSubmissionsRequest? = null
        private set
    internal var storyOpenedFromSubmissions by mutableStateOf(false)
        private set
    internal var storyOpenedFromSettings by mutableStateOf(false)
        private set
    internal var coulombGasVisible by mutableStateOf(false)
        private set
    internal var closeRequest by mutableIntStateOf(0)
        private set

    private var requestSerial = 0
    private var settingsRequestSerial = 0
    private var editorRequestSerial = 0
    private var submissionsRequestSerial = 0
    private var captchaRequestSerial = 0
    private var userRequestSerial = 0
    private var failureRequestSerial = 0
    private var currentSettingsSectionRoute: String? = null
    private var settingsThemeChangedRequestSerial = -1
    private var settingsNeedsRestart = false
    private var storiesCoordinator: StoriesCoordinator? = null
    private var commentsCoordinator: CommentsCoordinator? = null
    private var restoredCommentsState: Bundle? = savedState?.getBundle(STATE_COMMENTS_STATE)
    private var restoredCommentsRequestSerial: Int =
        savedState?.getInt(STATE_COMMENTS_REQUEST_SERIAL, -1) ?: -1
    internal var storiesComposeController by mutableStateOf<StoriesComposeController?>(null)
        private set
    internal var commentsComposeController by mutableStateOf<CommentsComposeController?>(null)
        private set
    private var adaptiveTwoPane = false
    private var adaptiveFoldable = false

    init {
        val restoredDestination = savedState?.getBundle(STATE_STORY_ARGUMENTS)
            ?.toStoryDestinationOrNull()
        if (restoredDestination != null) {
            requestSerial = savedState.getInt(STATE_REQUEST_SERIAL, 1).coerceAtLeast(1)
            MainStoryRequest(requestSerial, restoredDestination).also {
                storyRequest = it
                lastStoryRequest = it
            }
        }
        if (savedState?.getBoolean(STATE_SETTINGS_OPEN, false) == true) {
            settingsRequestSerial = savedState.getInt(STATE_SETTINGS_REQUEST_SERIAL, 1)
                .coerceAtLeast(1)
            currentSettingsSectionRoute = savedState.getString(STATE_SETTINGS_SECTION)
            MainSettingsRequest(
                serial = settingsRequestSerial,
                initialSectionRoute = currentSettingsSectionRoute,
            ).also {
                settingsRequest = it
                lastSettingsRequest = it
            }
            settingsNeedsRestart = savedState.getBoolean(STATE_SETTINGS_NEEDS_RESTART, false)
        }
        welcomeDialogVisible = savedState?.getBoolean(STATE_WELCOME_DIALOG_VISIBLE, false) == true
        changelogDialogVisible = savedState?.getBoolean(STATE_CHANGELOG_DIALOG_VISIBLE, false) == true
        cacheStoriesDialogVisible =
            savedState?.getBoolean(STATE_CACHE_STORIES_DIALOG_VISIBLE, false) == true
        loginDialogVisible = savedState?.getBoolean(STATE_LOGIN_DIALOG_VISIBLE, false) == true
        savedState?.getString(STATE_USER_DIALOG_NAME)?.let { userName ->
            userRequestSerial = savedState.getInt(STATE_USER_DIALOG_SERIAL, 1).coerceAtLeast(1)
            userRequest = MainUserRequest(userRequestSerial, userName, null)
        }
        savedState?.getBundle(STATE_EDITOR_ARGUMENTS)?.let { arguments ->
            editorRequestSerial = savedState.getInt(STATE_EDITOR_REQUEST_SERIAL, 1)
                .coerceAtLeast(1)
            MainEditorRequest(editorRequestSerial, arguments.toEditorDestination()).also {
                editorRequest = it
                lastEditorRequest = it
            }
        }
        savedState?.getString(STATE_SUBMISSIONS_USER)?.let { userName ->
            submissionsRequestSerial = savedState.getInt(STATE_SUBMISSIONS_REQUEST_SERIAL, 1)
                .coerceAtLeast(1)
            MainSubmissionsRequest(submissionsRequestSerial, userName).also {
                submissionsRequest = it
                lastSubmissionsRequest = it
            }
        }
        storyOpenedFromSubmissions =
            savedState?.getBoolean(STATE_STORY_OPENED_FROM_SUBMISSIONS, false) == true &&
                storyRequest != null && submissionsRequest != null
        storyOpenedFromSettings =
            savedState?.getBoolean(STATE_STORY_OPENED_FROM_SETTINGS, false) == true &&
                storyRequest != null && settingsRequest != null
        coulombGasVisible = savedState?.getBoolean(STATE_COULOMB_GAS_VISIBLE, false) == true
    }

    fun openStory(destination: StoryDestination) {
        restoredCommentsState = null
        restoredCommentsRequestSerial = -1
        if (settingsRequest != null) {
            storyOpenedFromSettings = true
            settingsRequest = null
        }
        if (!storyOpenedFromSubmissions) {
            submissionsRequest = null
        }
        editorRequest = null
        coulombGasVisible = false
        MainStoryRequest(++requestSerial, destination).also {
            lastStoryRequest = it
            storyRequest = it
        }
    }

    fun closeStory() {
        closeRequest++
    }

    fun openSettings(sectionRoute: String?) {
        submissionsRequest = null
        storyOpenedFromSubmissions = false
        storyOpenedFromSettings = false
        editorRequest = null
        coulombGasVisible = false
        currentSettingsSectionRoute = sectionRoute
        MainSettingsRequest(++settingsRequestSerial, sectionRoute).also {
            settingsRequest = it
            lastSettingsRequest = it
        }
    }

    internal fun closeSettings() {
        settingsRequest = null
    }

    internal fun updateSettingsSection(section: SettingsSection) {
        currentSettingsSectionRoute = section.route
    }

    internal fun getInitialSettingsSectionRoute(request: MainSettingsRequest): String? =
        if (settingsThemeChangedRequestSerial == request.serial) {
            currentSettingsSectionRoute
        } else {
            request.initialSectionRoute
        }

    internal fun onSettingsThemeChanged() {
        settingsThemeChangedRequestSerial = settingsRequest?.serial ?: -1
        settingsNeedsRestart = true
        settingsThemeRevision++
    }

    internal fun requestSettingsRestart() {
        settingsNeedsRestart = true
    }

    internal fun consumeSettingsRestartRequest(): Boolean = settingsNeedsRestart.also {
        settingsNeedsRestart = false
    }

    fun showWelcomeDialog() {
        welcomeDialogVisible = true
    }

    internal fun dismissWelcomeDialog() {
        welcomeDialogVisible = false
    }

    fun showChangelogDialog() {
        changelogDialogVisible = true
    }

    internal fun dismissChangelogDialog() {
        changelogDialogVisible = false
    }

    fun showCacheStoriesDialog() {
        cacheStoriesDialogVisible = true
    }

    internal fun dismissCacheStoriesDialog() {
        cacheStoriesDialogVisible = false
    }

    internal fun confirmCacheStories(storyCount: Int) {
        cacheStoriesDialogVisible = false
        storiesComposeController?.cacheStories(storyCount)
    }

    fun showLoginDialog() {
        loginDialogVisible = true
    }

    internal fun dismissLoginDialog() {
        loginDialogVisible = false
    }

    fun showCaptchaDialog(
        challenge: HackerNewsCaptchaChallenge,
        callback: CaptchaResultCallback,
    ) {
        captchaRequest?.callback?.onCaptchaCancelled()
        captchaRequest = MainCaptchaRequest(++captchaRequestSerial, challenge, callback)
    }

    internal fun dismissCaptchaDialog() {
        val request = captchaRequest ?: return
        captchaRequest = null
        request.callback.onCaptchaCancelled()
    }

    internal fun completeCaptchaDialog(response: String) {
        val request = captchaRequest ?: return
        captchaRequest = null
        request.callback.onCaptchaResponse(request.challenge, response)
    }

    fun showUserDialog(userName: String, onTagChanged: Runnable?) {
        if (userName.isBlank()) return
        userRequest = MainUserRequest(++userRequestSerial, userName, onTagChanged)
    }

    internal fun dismissUserDialog() {
        userRequest = null
    }

    internal fun notifyUserTagChanged() {
        userRequest?.onTagChanged?.run()
    }

    fun showFailureDetailDialog(
        title: String?,
        message: String?,
        clipboardText: String?,
    ) {
        failureRequest = MainFailureRequest(
            serial = ++failureRequestSerial,
            title = title,
            message = message,
            clipboardText = clipboardText,
        )
    }

    internal fun dismissFailureDetailDialog() {
        failureRequest = null
    }

    fun openEditor(destination: EditorDestination) {
        settingsRequest = null
        submissionsRequest = null
        storyOpenedFromSubmissions = false
        storyOpenedFromSettings = false
        coulombGasVisible = false
        MainEditorRequest(++editorRequestSerial, destination).also {
            editorRequest = it
            lastEditorRequest = it
        }
    }

    internal fun closeEditor() {
        editorRequest = null
    }

    fun openSubmissions(userName: String) {
        editorRequest = null
        coulombGasVisible = false
        MainSubmissionsRequest(++submissionsRequestSerial, userName).also {
            submissionsRequest = it
            lastSubmissionsRequest = it
        }
    }

    internal fun closeSubmissions() {
        submissionsRequest = null
        storyOpenedFromSubmissions = false
    }

    internal fun prepareToOpenStoryFromSubmissions() {
        if (submissionsRequest != null) {
            storyOpenedFromSubmissions = true
        }
    }

    fun openCoulombGas() {
        submissionsRequest = null
        storyOpenedFromSubmissions = false
        editorRequest = null
        coulombGasVisible = true
    }

    internal fun closeCoulombGas() {
        coulombGasVisible = false
    }

    fun switchOpenStoryViewIfMatching(storyId: Int, showWebsite: Boolean): Boolean =
        commentsCoordinator?.switchStoryViewIfMatching(storyId, showWebsite) == true

    fun getStoriesCoordinator(): StoriesCoordinator? = storiesCoordinator

    fun getCommentsCoordinator(): CommentsCoordinator? = commentsCoordinator

    fun isAdaptiveTwoPane(): Boolean = adaptiveTwoPane

    fun isAdaptiveFoldable(): Boolean = adaptiveFoldable

    fun attachStoriesCoordinator(coordinator: StoriesCoordinator) {
        storiesCoordinator = coordinator
    }

    internal fun detachStoriesCoordinator(coordinator: StoriesCoordinator) {
        if (storiesCoordinator === coordinator) storiesCoordinator = null
    }

    fun attachStoriesComposeController(controller: StoriesComposeController) {
        storiesComposeController = controller
    }

    fun detachStoriesComposeController(controller: StoriesComposeController) {
        if (storiesComposeController === controller) storiesComposeController = null
    }

    internal fun attachCommentsCoordinator(coordinator: CommentsCoordinator) {
        commentsCoordinator = coordinator
    }

    internal fun detachCommentsCoordinator(coordinator: CommentsCoordinator) {
        if (commentsCoordinator === coordinator) commentsCoordinator = null
    }

    internal fun consumeCommentsSavedState(requestSerial: Int): Bundle? {
        if (restoredCommentsRequestSerial != requestSerial) return null
        return restoredCommentsState?.also {
            restoredCommentsState = null
            restoredCommentsRequestSerial = -1
        }
    }

    fun attachCommentsComposeController(controller: CommentsComposeController) {
        commentsComposeController = controller
    }

    fun detachCommentsComposeController(controller: CommentsComposeController) {
        if (commentsComposeController === controller) commentsComposeController = null
    }

    internal fun detailRemovedFromBackStack() {
        val restoreSettings = storyOpenedFromSettings
        val restoreSettingsSection = currentSettingsSectionRoute
        storyRequest = null
        storyOpenedFromSubmissions = false
        storyOpenedFromSettings = false
        if (restoreSettings) {
            openSettings(restoreSettingsSection)
        }
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
        storyRequest?.let { request ->
            outState.putInt(STATE_REQUEST_SERIAL, request.serial)
            outState.putBundle(STATE_STORY_ARGUMENTS, request.destination.toBundle())
            commentsCoordinator?.let { coordinator ->
                val commentsState = Bundle()
                coordinator.onSaveInstanceState(commentsState)
                outState.putInt(STATE_COMMENTS_REQUEST_SERIAL, request.serial)
                outState.putBundle(STATE_COMMENTS_STATE, commentsState)
            }
        }
        if (settingsRequest != null || storyOpenedFromSettings) {
            outState.putBoolean(STATE_SETTINGS_OPEN, true)
            outState.putInt(STATE_SETTINGS_REQUEST_SERIAL, settingsRequestSerial)
            outState.putString(STATE_SETTINGS_SECTION, currentSettingsSectionRoute)
            outState.putBoolean(STATE_SETTINGS_NEEDS_RESTART, settingsNeedsRestart)
        }
        outState.putBoolean(STATE_WELCOME_DIALOG_VISIBLE, welcomeDialogVisible)
        outState.putBoolean(STATE_CHANGELOG_DIALOG_VISIBLE, changelogDialogVisible)
        outState.putBoolean(STATE_CACHE_STORIES_DIALOG_VISIBLE, cacheStoriesDialogVisible)
        outState.putBoolean(STATE_LOGIN_DIALOG_VISIBLE, loginDialogVisible)
        userRequest?.let { request ->
            outState.putInt(STATE_USER_DIALOG_SERIAL, request.serial)
            outState.putString(STATE_USER_DIALOG_NAME, request.userName)
        }
        editorRequest?.let { request ->
            outState.putInt(STATE_EDITOR_REQUEST_SERIAL, request.serial)
            outState.putBundle(STATE_EDITOR_ARGUMENTS, request.destination.toBundle())
        }
        submissionsRequest?.let { request ->
            outState.putInt(STATE_SUBMISSIONS_REQUEST_SERIAL, request.serial)
            outState.putString(STATE_SUBMISSIONS_USER, request.userName)
        }
        outState.putBoolean(
            STATE_STORY_OPENED_FROM_SUBMISSIONS,
            storyOpenedFromSubmissions,
        )
        outState.putBoolean(
            STATE_STORY_OPENED_FROM_SETTINGS,
            storyOpenedFromSettings,
        )
        outState.putBoolean(STATE_COULOMB_GAS_VISIBLE, coulombGasVisible)
    }

    private companion object {
        const val STATE_REQUEST_SERIAL = "main_navigation_request_serial"
        const val STATE_STORY_ARGUMENTS = "main_navigation_story_arguments"
        const val STATE_COMMENTS_REQUEST_SERIAL = "main_navigation_comments_request_serial"
        const val STATE_COMMENTS_STATE = "main_navigation_comments_state"
        const val STATE_SETTINGS_OPEN = "main_navigation_settings_open"
        const val STATE_SETTINGS_REQUEST_SERIAL = "main_navigation_settings_request_serial"
        const val STATE_SETTINGS_SECTION = "main_navigation_settings_section"
        const val STATE_SETTINGS_NEEDS_RESTART = "main_navigation_settings_needs_restart"
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

object MainNavigationHost {
    @JvmStatic
    fun install(activity: MainActivity, savedState: Bundle?): MainNavigationController {
        val controller = MainNavigationController(savedState)
        val composeView = ComposeView(activity).apply {
            id = R.id.main_navigation_compose
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HarmonicTheme {
                    MainNavigation(
                        activity = activity,
                        controller = controller,
                    )
                }
            }
        }
        activity.setContentView(composeView)
        return controller
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainNavigation(
    activity: MainActivity,
    controller: MainNavigationController,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val context = activity
    val supportsTwoPane = context.resources.configuration.smallestScreenWidthDp >= 600
    val hasHingeAngleSensor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    val isFoldable = adaptiveInfo.windowPosture.hingeList.isNotEmpty() || hasHingeAngleSensor
    val density = LocalDensity.current
    val tabletPaneSpacer = with(density) {
        context.resources.getDimensionPixelSize(R.dimen.margin_between_panes).toDp()
    }
    val tabletStoriesWeight = context.resources.getInteger(R.integer.stories_pane_weight)
    val directive = remember(adaptiveInfo, supportsTwoPane, isFoldable, tabletPaneSpacer) {
        val adaptiveDirective = calculatePaneScaffoldDirective(adaptiveInfo)
        adaptiveDirective.copy(
            maxHorizontalPartitions = if (supportsTwoPane) {
                adaptiveDirective.maxHorizontalPartitions
            } else {
                1
            },
            horizontalPartitionSpacerSize = if (isFoldable) 12.dp else tabletPaneSpacer,
        )
    }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    SideEffect {
        controller.updateAdaptiveState(
            twoPane = isTwoPane,
            foldable = isTwoPane && isFoldable,
        )
    }
    val paneProportion = if (isFoldable) {
        0.5f
    } else {
        tabletStoriesWeight / (tabletStoriesWeight + LEGACY_COMMENTS_PANE_WEIGHT)
    }
    val paneExpansionState = rememberPaneExpansionState(
        anchors = remember(paneProportion) {
            listOf(PaneExpansionAnchor.Proportion(paneProportion))
        },
        initialAnchoredIndex = 0,
    )
    val sceneStrategy = rememberListDetailSceneStrategy<NavKey>(
        directive = directive,
        paneExpansionState = paneExpansionState.takeIf { isTwoPane },
    )
    val backStack = remember(controller) {
        mutableStateListOf<NavKey>(StoriesDestination).apply {
            controller.storyRequest?.let { add(CommentsDestination(it)) }
        }
    }
    val backAnimationScope = rememberCoroutineScope()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedPredictivePop by remember { mutableStateOf(false) }

    fun popMainBackStack() {
        if (backStack.lastOrNull() is CommentsDestination) {
            backStack.removeLastOrNull()
            controller.detailRemovedFromBackStack()
        } else {
            activity.finish()
        }
    }

    val storyRequest = controller.storyRequest
    val storyOpenedFromSubmissions = controller.storyOpenedFromSubmissions
    val storyOpenedFromSettings = controller.storyOpenedFromSettings
    val paneStatusBarColor = HarmonicTheme.colors.background
    val commentsController = controller.commentsComposeController
    val targetStatusBarColor = if (storyRequest != null && commentsController != null) {
        lerp(
            paneStatusBarColor,
            commentsController.statusBarHeaderColor ?: paneStatusBarColor,
            commentsController.statusBarHeaderCoverage,
        )
    } else {
        paneStatusBarColor
    }
    val statusBarColor by animateColorAsState(
        targetValue = targetStatusBarColor,
        animationSpec = if (completedPredictivePop) {
            snap()
        } else {
            tween(durationMillis = 90, easing = LinearEasing)
        },
        label = "main status bar protection",
    )
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(storyRequest?.serial) {
        storyRequest ?: return@LaunchedEffect
        // A completed predictive pop keeps its snap exit policy while the detail is hidden.
        // Resetting it only when opening the next story avoids changing AnimatedVisibility's
        // exit transition for one hidden frame, which could briefly resurrect Comments.
        completedPredictivePop = false
        if (backStack.lastOrNull() is CommentsDestination) {
            backStack[backStack.lastIndex] = CommentsDestination(storyRequest)
        } else {
            backStack.add(CommentsDestination(storyRequest))
        }
    }
    LaunchedEffect(controller.closeRequest) {
        if (controller.closeRequest > 0 && backStack.lastOrNull() is CommentsDestination) {
            popMainBackStack()
        }
    }

    val provider = entryProvider<NavKey> {
        entry<StoriesDestination>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = { EmptyCommentsScreen() },
            ),
        ) {
            StoriesPane(
                controller = controller,
                statusBarColor = paneStatusBarColor,
                statusBarHeight = statusBarHeight,
                drawStatusBarProtection = true,
            )
        }

        entry<CommentsDestination>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) { destination ->
            CommentsPane(
                request = destination.request,
                controller = controller,
                statusBarColor = statusBarColor,
                statusBarHeight = statusBarHeight,
                drawStatusBarProtection = isTwoPane,
            )
        }
    }

    PredictiveBackHandler(
        enabled = backStack.lastOrNull() is CommentsDestination,
    ) { events ->
        val internalBackCoordinator = controller.getCommentsCoordinator()
            ?.takeIf(CommentsCoordinator::handlesBackInternally)
        if (internalBackCoordinator != null) {
            var predictiveBackStarted = false
            try {
                events.collect { event ->
                    if (predictiveBackStarted) {
                        internalBackCoordinator.updateInternalPredictiveBack(event)
                    } else {
                        predictiveBackStarted = true
                        internalBackCoordinator.startInternalPredictiveBack(event)
                    }
                }
                internalBackCoordinator.commitInternalBack()
            } catch (_: CancellationException) {
                if (predictiveBackStarted) {
                    internalBackCoordinator.cancelInternalPredictiveBack()
                }
            }
            return@PredictiveBackHandler
        }

        if (isTwoPane) {
            var frozenWebViewCoordinator: CommentsCoordinator? = null
            try {
                events.collect {
                    val currentFrozenCoordinator = frozenWebViewCoordinator
                    if (currentFrozenCoordinator == null) {
                        controller.getCommentsCoordinator()
                            ?.takeIf { it.beginVisibleWebViewPredictiveBackScrollFreeze() }
                            ?.let { frozenWebViewCoordinator = it }
                    } else {
                        currentFrozenCoordinator
                            .maintainVisibleWebViewPredictiveBackScrollFreeze()
                    }
                }
                popMainBackStack()
            } catch (_: CancellationException) {
                // A cancelled two-pane gesture keeps the current detail selected.
            } finally {
                frozenWebViewCoordinator?.endVisibleWebViewPredictiveBackScrollFreeze()
            }
            return@PredictiveBackHandler
        }

        var animation: DefaultActivityPredictiveBackAnimation? = null
        var frozenWebViewCoordinator: CommentsCoordinator? = null
        try {
            events.collect { event ->
                val currentFrozenCoordinator = frozenWebViewCoordinator
                if (currentFrozenCoordinator == null) {
                    controller.getCommentsCoordinator()
                        ?.takeIf { it.beginVisibleWebViewPredictiveBackScrollFreeze() }
                        ?.let { frozenWebViewCoordinator = it }
                } else {
                    currentFrozenCoordinator.maintainVisibleWebViewPredictiveBackScrollFreeze()
                }
                val currentAnimation = animation
                    ?: DefaultActivityPredictiveBackAnimation(event).also {
                        animation = it
                        activeBackAnimation = it
                    }
                backAnimationScope.launch {
                    currentAnimation.animate(event)
                }
            }

            val currentAnimation = animation
            if (currentAnimation == null) {
                popMainBackStack()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            completedPredictivePop = true
            popMainBackStack()
            activeBackAnimation = null
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeBackAnimation === animation) activeBackAnimation = null
            }
        } finally {
            frozenWebViewCoordinator?.endVisibleWebViewPredictiveBackScrollFreeze()
        }
    }

    val settingsRequest = controller.settingsRequest
    val settingsAnimationScope = rememberCoroutineScope()
    var activeSettingsBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedSettingsPredictiveBack by remember { mutableStateOf(false) }

    LaunchedEffect(settingsRequest?.serial) {
        if (settingsRequest != null) completedSettingsPredictiveBack = false
    }

    fun closeSettings() {
        val needsRestart = controller.consumeSettingsRestartRequest()
        controller.closeSettings()
        if (needsRestart) {
            activity.restartAfterSettingsChange()
        } else {
            activity.applySettingsChanges()
        }
    }

    PredictiveBackHandler(enabled = settingsRequest != null && !storyOpenedFromSettings) { events ->
        var animation: DefaultActivityPredictiveBackAnimation? = null
        try {
            events.collect { event ->
                val currentAnimation = animation
                    ?: DefaultActivityPredictiveBackAnimation(event).also {
                        animation = it
                        activeSettingsBackAnimation = it
                    }
                settingsAnimationScope.launch {
                    currentAnimation.animate(event)
                }
            }

            val currentAnimation = animation
            if (currentAnimation == null) {
                closeSettings()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            completedSettingsPredictiveBack = true
            closeSettings()
            activeSettingsBackAnimation = null
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeSettingsBackAnimation === animation) {
                    activeSettingsBackAnimation = null
                }
            }
        }
    }

    val submissionsRequest = controller.submissionsRequest
    val submissionsAnimationScope = rememberCoroutineScope()
    var activeSubmissionsBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedSubmissionsPredictiveBack by remember { mutableStateOf(false) }

    LaunchedEffect(submissionsRequest?.serial) {
        if (submissionsRequest != null) completedSubmissionsPredictiveBack = false
    }

    PredictiveBackHandler(
        enabled = submissionsRequest != null && !storyOpenedFromSubmissions,
    ) { events ->
        var animation: DefaultActivityPredictiveBackAnimation? = null
        try {
            events.collect { event ->
                val currentAnimation = animation
                    ?: DefaultActivityPredictiveBackAnimation(event).also {
                        animation = it
                        activeSubmissionsBackAnimation = it
                    }
                submissionsAnimationScope.launch {
                    currentAnimation.animate(event)
                }
            }
            val currentAnimation = animation
            if (currentAnimation == null) {
                controller.closeSubmissions()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            completedSubmissionsPredictiveBack = true
            controller.closeSubmissions()
            activeSubmissionsBackAnimation = null
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeSubmissionsBackAnimation === animation) {
                    activeSubmissionsBackAnimation = null
                }
            }
        }
    }

    val editorRequest = controller.editorRequest
    val editorAnimationScope = rememberCoroutineScope()
    var activeEditorBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedEditorPredictiveBack by remember { mutableStateOf(false) }

    LaunchedEffect(editorRequest?.serial) {
        if (editorRequest != null) completedEditorPredictiveBack = false
    }

    PredictiveBackHandler(enabled = editorRequest != null) { events ->
        var animation: DefaultActivityPredictiveBackAnimation? = null
        try {
            events.collect { event ->
                val currentAnimation = animation
                    ?: DefaultActivityPredictiveBackAnimation(event).also {
                        animation = it
                        activeEditorBackAnimation = it
                    }
                editorAnimationScope.launch {
                    currentAnimation.animate(event)
                }
            }
            val currentAnimation = animation
            if (currentAnimation == null) {
                controller.closeEditor()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            completedEditorPredictiveBack = true
            controller.closeEditor()
            activeEditorBackAnimation = null
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeEditorBackAnimation === animation) {
                    activeEditorBackAnimation = null
                }
            }
        }
    }

    val coulombGasVisible = controller.coulombGasVisible
    PredictiveBackHandler(enabled = coulombGasVisible) { events ->
        try {
            events.collect { }
            controller.closeCoulombGas()
        } catch (_: CancellationException) {
            // A cancelled gesture leaves the simulation open.
        }
    }

    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = provider,
    )
    val sceneState = rememberSceneState(
        entries = decoratedEntries,
        sceneStrategies = listOf(sceneStrategy),
        onBack = ::popMainBackStack,
    )
    val navDisplayEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
    )
    val settingsTransitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (
                        (settingsRequest != null && !storyOpenedFromSettings) ||
                        (submissionsRequest != null && !storyOpenedFromSubmissions) ||
                        editorRequest != null ||
                        coulombGasVisible
                    ) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(activeSettingsBackAnimation?.enterModifier ?: Modifier),
        ) {
            if (isTwoPane) {
                NavDisplay(
                    sceneState = sceneState,
                    navigationEventState = navDisplayEventState,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { mainOpenTransition() },
                    popTransitionSpec = { mainPopTransition() },
                    predictivePopTransitionSpec = { _ -> mainPopTransition() },
                )
            } else {
                SinglePaneNavigation(
                    controller = controller,
                    animation = activeBackAnimation,
                    completedPredictivePop = completedPredictivePop,
                    showStoriesPane = !storyOpenedFromSubmissions,
                    storiesStatusBarColor = paneStatusBarColor,
                    commentsStatusBarColor = statusBarColor,
                    statusBarHeight = statusBarHeight,
                )
            }
        }

        controller.storiesComposeController?.let { storiesController ->
            if (storiesController.storyPreviewOverlay != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(4f),
                ) {
                    StoryPreviewOverlay(storiesController)
                }
            }
        }

        controller.commentsComposeController?.let { commentsController ->
            if (commentsController.linkPreviewOverlay != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(4.5f),
                ) {
                    CommentLinkPreviewOverlay(commentsController)
                }
            }
        }

        AnimatedVisibility(
            visible = settingsRequest != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (storyOpenedFromSettings) -1f else 5f)
                .then(
                    if (
                        (settingsRequest != null && !storyOpenedFromSettings) ||
                        submissionsRequest != null ||
                        editorRequest != null ||
                        coulombGasVisible
                    ) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(activeSettingsBackAnimation?.exitModifier ?: Modifier),
            enter = settingsOpenEnter(settingsTransitionOffsetPx),
            exit = if (completedSettingsPredictiveBack) {
                ExitTransition.None
            } else {
                settingsPopExit(settingsTransitionOffsetPx)
            },
        ) {
            if (settingsRequest != null || !completedSettingsPredictiveBack) {
                controller.lastSettingsRequest?.let { request ->
                    key(request.serial, controller.settingsThemeRevision) {
                        HarmonicTheme {
                            SettingsShell(
                                initialSection = controller
                                    .getInitialSettingsSectionRoute(request)
                                    ?.let(SettingsSection::fromRoute),
                                onBackFromSettings = ::closeSettings,
                                onSectionChanged = controller::updateSettingsSection,
                                onThemeChanged = {
                                    ThemeUtils.setupTheme(activity)
                                    controller.onSettingsThemeChanged()
                                },
                                onRequestRestart = controller::requestSettingsRestart,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = submissionsRequest != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (storyOpenedFromSubmissions) -1f else 7f)
                .then(
                    if (
                        storyOpenedFromSubmissions ||
                        editorRequest != null ||
                        coulombGasVisible
                    ) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(activeSubmissionsBackAnimation?.exitModifier ?: Modifier),
            enter = settingsOpenEnter(settingsTransitionOffsetPx),
            exit = if (completedSubmissionsPredictiveBack) {
                ExitTransition.None
            } else {
                settingsPopExit(settingsTransitionOffsetPx)
            },
        ) {
            if (submissionsRequest != null || !completedSubmissionsPredictiveBack) {
                controller.lastSubmissionsRequest?.let { request ->
                    key(request.serial) {
                        val coordinator = remember(request.serial) {
                            SubmissionsCoordinator(
                                activity = activity,
                                sessionKey = request.serial,
                                userName = request.userName,
                                navigator = SubmissionsCoordinator.Navigator { story, showWebsite ->
                                    controller.prepareToOpenStoryFromSubmissions()
                                    controller.closeSettings()
                                    activity.openStory(story, 0, showWebsite)
                                },
                            )
                        }
                        DisposableEffect(coordinator) {
                            onDispose(coordinator::close)
                        }
                        SubmissionsScreen(coordinator.composeController)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = editorRequest != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .then(
                    if (coulombGasVisible) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(activeEditorBackAnimation?.exitModifier ?: Modifier),
            enter = settingsOpenEnter(settingsTransitionOffsetPx),
            exit = if (completedEditorPredictiveBack) {
                ExitTransition.None
            } else {
                settingsPopExit(settingsTransitionOffsetPx)
            },
        ) {
            if (editorRequest != null || !completedEditorPredictiveBack) {
                controller.lastEditorRequest?.let { request ->
                    key(request.serial) {
                        Box(Modifier.fillMaxSize()) {
                            // The editor is a modal sibling of the story navigation. Its opaque
                            // surface is not itself a pointer target, so keep an explicit barrier
                            // behind it to prevent taps in field gutters reaching the story layer.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            do {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                            } while (event.changes.any { it.pressed })
                                        }
                                    },
                            )

                            val editorController = remember(request.serial) {
                                ComposeEditorController(activity)
                            }
                            val coordinator = remember(request.serial) {
                                ComposeEditorCoordinator(
                                    activity,
                                    request.destination,
                                    controller::closeEditor,
                                )
                            }
                            SideEffect {
                                coordinator.attachController(editorController)
                            }
                            ComposeEditorScreen(
                                type = coordinator.type,
                                parentText = coordinator.parentText,
                                postTitle = coordinator.postTitle,
                                user = coordinator.user,
                                titleMaxLength = coordinator.titleMaxLength,
                                submitting = editorController.submitting,
                                onClose = controller::closeEditor,
                                onSubmit = coordinator::submit,
                                onOpenLink = { url -> Utils.openLinkMaybeHN(activity, url) },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = coulombGasVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            DisposableEffect(activity) {
                activity.setImmersiveContentEnabled(true)
                onDispose {
                    activity.setImmersiveContentEnabled(false)
                }
            }
            CoulombGasScreen()
        }

        if (controller.welcomeDialogVisible) {
            WelcomeSettingsDialog(
                styleChooser = false,
                onDismiss = controller::dismissWelcomeDialog,
            )
        }

        if (controller.changelogDialogVisible) {
            SettingsChangelogDialog(
                onDismiss = controller::dismissChangelogDialog,
                onOpenGithub = {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/SimonHalvdansson/Harmonic-HN"),
                        ),
                    )
                },
            )
        }

        if (controller.cacheStoriesDialogVisible) {
            CacheStoriesDialog(
                initialStoryCount = SettingsUtils.getStoriesToCache(activity),
                onDismiss = controller::dismissCacheStoriesDialog,
                onConfirm = controller::confirmCacheStories,
            )
        }

        if (controller.loginDialogVisible) {
            LoginDialog(
                onDismiss = controller::dismissLoginDialog,
                onAccountStateChanged = activity::onAccountStateChanged,
            )
        }

        controller.captchaRequest?.let { request ->
            key(request.serial) {
                CaptchaDialog(
                    challenge = request.challenge,
                    onDismiss = controller::dismissCaptchaDialog,
                    onCaptchaResponse = controller::completeCaptchaDialog,
                )
            }
        }

        controller.userRequest?.let { request ->
            key(request.serial) {
                UserSettingsDialog(
                    userName = request.userName,
                    onDismiss = controller::dismissUserDialog,
                    onTagChanged = controller::notifyUserTagChanged,
                )
            }
        }

        controller.failureRequest?.let { request ->
            key(request.serial) {
                FailureDetailDialog(
                    title = request.title,
                    message = request.message,
                    showCopyComment = request.clipboardText != null,
                    onCopyComment = {
                        request.clipboardText?.let { text ->
                            com.simon.harmonichackernews.platform.AndroidClipboardService(activity)
                                .copy("Hacker News comment", text)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                android.widget.Toast.makeText(
                                    activity,
                                    "Comment copied to clipboard",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        controller.dismissFailureDetailDialog()
                    },
                    onDismiss = controller::dismissFailureDetailDialog,
                )
            }
        }
    }

}

@Composable
private fun SinglePaneNavigation(
    controller: MainNavigationController,
    animation: DefaultActivityPredictiveBackAnimation?,
    completedPredictivePop: Boolean,
    showStoriesPane: Boolean,
    storiesStatusBarColor: Color,
    commentsStatusBarColor: Color,
    statusBarHeight: Dp,
) {
    val storyRequest = controller.storyRequest
    val displayedRequest = storyRequest ?: controller.lastStoryRequest
    var paneWidth by remember { mutableIntStateOf(0) }
    val storiesOffset by animateFloatAsState(
        targetValue = if (storyRequest == null) 0f else -0.2f,
        animationSpec = if (storyRequest == null) {
            snap()
        } else {
            tween(
                durationMillis = NavigationTransitionDurationMillis,
                easing = navigationEasing(),
            )
        },
        label = "stories navigation offset",
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { paneWidth = it.width }
                .graphicsLayer {
                    alpha = if (showStoriesPane) 1f else 0f
                    translationX = if (animation == null && !completedPredictivePop) {
                        paneWidth * storiesOffset
                    } else {
                        0f
                    }
                }
                .then(
                    if (showStoriesPane) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { }
                    },
                )
                .then(animation?.enterModifier ?: Modifier),
        ) {
            StoriesPane(controller)
            StatusBarProtection(
                color = storiesStatusBarColor,
                statusBarHeight = statusBarHeight,
            )
        }

        // Keep the visibility host alive while Stories is showing. Recreating it with
        // visible=true skipped the enter transition on every open after a predictive pop.
        // Predictive back already animates Comments fully away, so that completed path disposes
        // immediately while ordinary back presses retain the normal pop animation.
        AnimatedVisibility(
            visible = storyRequest != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .then(animation?.exitModifier ?: Modifier),
            enter = commentsOpenEnter(),
            exit = if (completedPredictivePop) ExitTransition.None else commentsPopExit(),
        ) {
            // AnimatedVisibility can retain its last content for one frame even with a snap
            // exit. Stop emitting the committed predictive-back destination so removing its
            // finished graphics transform cannot reveal it again.
            if (storyRequest != null || !completedPredictivePop) {
                displayedRequest?.let { request ->
                    key(request.serial) {
                        CommentsPane(
                            request = request,
                            controller = controller,
                            statusBarColor = commentsStatusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun StoriesPane(
    controller: MainNavigationController,
    statusBarColor: Color = Color.Transparent,
    statusBarHeight: Dp = 0.dp,
    drawStatusBarProtection: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        controller.storiesComposeController?.let { StoriesScreen(it) }
        if (drawStatusBarProtection) {
            StatusBarProtection(
                color = statusBarColor,
                statusBarHeight = statusBarHeight,
            )
        }
    }
}

@Composable
private fun CommentsPane(
    request: MainStoryRequest,
    controller: MainNavigationController,
    statusBarColor: Color = Color.Transparent,
    statusBarHeight: Dp = 0.dp,
    drawStatusBarProtection: Boolean = false,
) {
    val activity = LocalActivity.current as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var coordinator by remember(activity, request.serial) {
        mutableStateOf<CommentsCoordinator?>(null)
    }
    DisposableEffect(controller, activity, request.serial, lifecycleOwner) {
        val activeCoordinator = CommentsCoordinator(
            activity,
            request.destination,
            request.serial,
            controller.consumeCommentsSavedState(request.serial),
        )
        coordinator = activeCoordinator
        controller.attachCommentsCoordinator(activeCoordinator)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activeCoordinator.onStart()
                Lifecycle.Event.ON_RESUME -> activeCoordinator.onResume()
                Lifecycle.Event.ON_STOP -> activeCoordinator.onStop()
                Lifecycle.Event.ON_DESTROY -> activeCoordinator.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.detachCommentsCoordinator(activeCoordinator)
            activeCoordinator.onDestroy()
            if (coordinator === activeCoordinator) coordinator = null
        }
    }
    coordinator?.let { activeCoordinator ->
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { activeCoordinator.webViewRoot },
            )
            controller.commentsComposeController?.let { commentsController ->
                if (!commentsController.webViewFullscreen) {
                    CommentsScaffold(commentsController)
                }
            }
            if (drawStatusBarProtection) {
                StatusBarProtection(
                    color = statusBarColor,
                    statusBarHeight = statusBarHeight,
                )
            }
        }
    }
}

@Composable
private fun StatusBarProtection(
    color: Color,
    statusBarHeight: Dp,
) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarHeight + 16.dp)
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.92f),
                    0.58f to color.copy(alpha = 0.72f),
                    1f to color.copy(alpha = 0f),
                ),
            ),
    )
}

private const val NavigationTransitionDurationMillis = 450
private const val NavigationFadeDurationMillis = 90
private const val LEGACY_COMMENTS_PANE_WEIGHT = 5f

private fun navigationEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private fun mainOpenTransition(): ContentTransform = ContentTransform(
    targetContentEnter = commentsOpenEnter(),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NavigationTransitionDurationMillis,
            easing = navigationEasing(),
        ),
        targetOffsetX = { -it / 5 },
    ),
    targetContentZIndex = 1f,
)

private fun commentsOpenEnter(): EnterTransition = slideInHorizontally(
    animationSpec = tween(
        durationMillis = NavigationTransitionDurationMillis,
        easing = navigationEasing(),
    ),
    initialOffsetX = { it },
) + fadeIn(
    animationSpec = tween(
        durationMillis = NavigationFadeDurationMillis,
        delayMillis = 45,
        easing = LinearEasing,
    ),
)

private fun mainPopTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = NavigationTransitionDurationMillis,
            easing = navigationEasing(),
        ),
        initialOffsetX = { -it / 5 },
    ),
    initialContentExit = commentsPopExit(),
    targetContentZIndex = -1f,
)

private fun commentsPopExit(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = NavigationTransitionDurationMillis,
        easing = navigationEasing(),
    ),
    targetOffsetX = { it },
) + fadeOut(
    animationSpec = tween(
        durationMillis = NavigationFadeDurationMillis,
        delayMillis = 35,
        easing = LinearEasing,
    ),
)

private fun settingsOpenEnter(offsetPx: Int): EnterTransition = slideInHorizontally(
    animationSpec = tween(
        durationMillis = NavigationTransitionDurationMillis,
        easing = navigationEasing(),
    ),
    initialOffsetX = { offsetPx },
) + fadeIn(
    animationSpec = tween(
        durationMillis = NavigationFadeDurationMillis,
        delayMillis = 50,
        easing = LinearEasing,
    ),
)

private fun settingsPopExit(offsetPx: Int): ExitTransition = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = NavigationTransitionDurationMillis,
        easing = navigationEasing(),
    ),
    targetOffsetX = { offsetPx },
) + fadeOut(
    animationSpec = tween(
        durationMillis = NavigationFadeDurationMillis,
        delayMillis = 35,
        easing = LinearEasing,
    ),
)
