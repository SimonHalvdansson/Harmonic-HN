package com.simon.harmonichackernews.ui.navigation

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.CommentsCoordinator
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.HarmonicSceneViewModel
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.StoriesCoordinator
import com.simon.harmonichackernews.ui.comments.AndroidCommentActionOverlay
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.comments.AndroidCommentLinkPreviewOverlay
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsScaffold
import com.simon.harmonichackernews.ui.comments.CommentsHazeHost
import com.simon.harmonichackernews.ui.comments.CommentsUpButton
import com.simon.harmonichackernews.ui.common.CaptchaDialog
import com.simon.harmonichackernews.presentation.CaptchaResultHandler
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.common.FailureDetailDialog
import com.simon.harmonichackernews.ui.common.AndroidLoginDialog
import com.simon.harmonichackernews.ui.common.UserMessageSnackbarHost
import com.simon.harmonichackernews.ui.debug.CoulombGasScreen
import com.simon.harmonichackernews.ui.editor.EditorComposeController
import com.simon.harmonichackernews.ui.editor.ComposeEditorCoordinator
import com.simon.harmonichackernews.ui.editor.ComposeEditorScreen
import com.simon.harmonichackernews.ui.settings.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.settings.SettingsChangelogDialog
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SettingsShell
import com.simon.harmonichackernews.ui.settings.ProvideSettingsPlatformStyle
import com.simon.harmonichackernews.ui.settings.SettingsPlatformStyle
import com.simon.harmonichackernews.ui.settings.AndroidUserSettingsDialog
import com.simon.harmonichackernews.ui.settings.AndroidWelcomeSettingsDialog
import com.simon.harmonichackernews.ui.submissions.SubmissionsCoordinator
import com.simon.harmonichackernews.ui.submissions.AndroidSubmissionsScreen
import com.simon.harmonichackernews.ui.stories.CacheStoriesDialog
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.AndroidStoriesScreen
import com.simon.harmonichackernews.ui.stories.AndroidStoryPreviewOverlay
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.data.toEditorDestination
import com.simon.harmonichackernews.data.toStoryDestinationOrNull
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.MainEditorRequest
import com.simon.harmonichackernews.navigation.MainCaptchaRequest
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainFailureRequest
import com.simon.harmonichackernews.navigation.MainNavigationRestoration
import com.simon.harmonichackernews.navigation.MainNavigationRestorationCodec
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainSettingsRequest
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainSubmissionsRequest
import com.simon.harmonichackernews.navigation.MainUserRequest
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.settings.AppLaunchDialog
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.utils.ThemeUtils
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Imperative bridge used by the existing story-loading controller while Navigation 3 owns the
 * actual list/detail history and adaptive layout.
 */
@Stable
class MainNavigationController internal constructor(
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
    private var storiesCoordinator: StoriesCoordinator? = null
    private var commentsCoordinator: CommentsCoordinator? = null
    private val commentsCoordinatorCache = mutableMapOf<Int, CommentsCoordinator>()
    private val commentsCoordinatorReferences = mutableMapOf<Int, Int>()
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
        navigationState.restore(restoredNavigation)
    }

    fun openStory(destination: StoryDestination) {
        if (
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
        navigationState.closeSubmissions()
    }

    fun openCoulombGas() {
        navigationState.openCoulombGas()
    }

    internal fun closeCoulombGas() {
        navigationState.closeCoulombGas()
    }

    fun getCommentsCoordinator(): CommentsCoordinator? = commentsCoordinator

    fun isAdaptiveTwoPane(): Boolean = adaptiveTwoPane

    fun isAdaptiveFoldable(): Boolean = adaptiveFoldable

    fun attachStoriesCoordinator(coordinator: StoriesCoordinator) {
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
        commentsCoordinatorCache.values.toSet().forEach(CommentsCoordinator::onDestroy)
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

    internal fun detachStoriesCoordinator(coordinator: StoriesCoordinator) {
        if (storiesCoordinator === coordinator) storiesCoordinator = null
    }

    fun attachStoriesComposeController(controller: StoriesComposeController) {
        storiesComposeController = controller
    }

    fun detachStoriesComposeController(controller: StoriesComposeController) {
        if (storiesComposeController === controller) storiesComposeController = null
    }

    internal fun retainCommentsCoordinator(
        activity: MainActivity,
        request: MainStoryRequest,
    ): CommentsCoordinator {
        val coordinator = commentsCoordinatorCache.getOrPut(request.serial) {
            CommentsCoordinator(
                activity,
                request.destination,
                request.serial,
                consumeCommentsSavedState(request.serial),
                navigation = this,
            )
        }
        commentsCoordinatorReferences[request.serial] =
            (commentsCoordinatorReferences[request.serial] ?: 0) + 1
        return coordinator
    }

    internal fun attachCommentsCoordinator(coordinator: CommentsCoordinator) {
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

    internal fun releaseCommentsCoordinator(coordinator: CommentsCoordinator) {
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
        commentsCoordinator?.setHostActive(destination == MainDestination.STORY)
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

    fun attachCommentsComposeController(
        coordinator: CommentsCoordinator,
        controller: CommentsComposeController,
    ) {
        if (navigationState.state.value.storyRequest?.serial == coordinator.sessionKey) {
            commentsComposeController = controller
        }
    }

    fun detachCommentsComposeController(
        coordinator: CommentsCoordinator,
        controller: CommentsComposeController,
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

object MainNavigationHost {
    @JvmStatic
    fun install(activity: MainActivity, savedState: Bundle?): MainNavigationController {
        val appComposition = activity.harmonicAppComposition
        val scene = ViewModelProvider(activity)[HarmonicSceneViewModel::class.java].scene
        val controller = MainNavigationController(
            scene,
            savedState,
        )
        when (
            appComposition.launchState.consumeLaunchDialog(
                currentVersion = appComposition.metadata.versionCode,
                showChangelog = appComposition.userSettings.general.showChangelog,
            )
        ) {
            AppLaunchDialog.WELCOME -> controller.showWelcomeDialog()
            AppLaunchDialog.CHANGELOG -> controller.showChangelogDialog()
            AppLaunchDialog.NONE -> Unit
        }
        val storiesCoordinator = StoriesCoordinator(
            activity = activity,
            savedInstanceState = savedState,
            navigation = controller,
        )
        controller.attachStoriesCoordinator(storiesCoordinator)
        storiesCoordinator.composeController?.let(controller::attachStoriesComposeController)
        val composeView = ComposeView(activity).apply {
            id = R.id.main_navigation_compose
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val themeSelection by appComposition.appearance.selections.collectAsState(
                    initial = appComposition.appearance.selection(),
                )
                ProvideHarmonicUiDependencies(
                    HarmonicUiDependencies(appComposition, scene),
                ) {
                    HarmonicTheme(
                        selection = themeSelection,
                    ) {
                        MainNavigation(
                            activity = activity,
                            controller = controller,
                        )
                    }
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
    val navigationSnapshot by controller.navigationState.state.collectAsState()
    val storyRequests = navigationSnapshot.storyBackStack
    val appearance = controller.scene.app.appearance
    val uiDependencies = LocalHarmonicUiDependencies.current
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
        controller.updateStoriesHostDestination(navigationSnapshot.currentDestination)
        controller.updateCommentsHostDestination(navigationSnapshot.currentDestination)
    }
    val paneProportion = if (isFoldable) {
        0.5f
    } else {
        tabletStoriesWeight / (tabletStoriesWeight + LEGACY_COMMENTS_PANE_WEIGHT)
    }
    val backAnimationScope = rememberCoroutineScope()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedPredictivePop by remember { mutableStateOf(false) }
    var observedStoryDepth by remember { mutableIntStateOf(storyRequests.size) }

    fun popMainBackStack() {
        if (controller.navigationState.state.value.currentDestination == MainDestination.STORY) {
            controller.detailRemovedFromBackStack()
        } else {
            activity.finish()
        }
    }

    val storyRequest = navigationSnapshot.storyRequest
    val storyParentDestination = navigationSnapshot.storyParentDestination
    val usesTwoPaneStoryScene = isTwoPane &&
        (storyRequest == null || storyParentDestination == MainDestination.STORIES)
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

    LaunchedEffect(storyRequests.size) {
        if (storyRequests.size > observedStoryDepth) completedPredictivePop = false
        observedStoryDepth = storyRequests.size
    }
    LaunchedEffect(navigationSnapshot.closeRequest) {
        if (
            navigationSnapshot.closeRequest > 0 &&
            navigationSnapshot.currentDestination == MainDestination.STORY
        ) {
            popMainBackStack()
        }
    }

    PredictiveBackHandler(
        enabled = navigationSnapshot.currentDestination == MainDestination.STORY,
    ) { events ->
        val storySerialAtGestureStart = controller.navigationState.state.value.storyRequest?.serial
        fun popGestureStoryIfStillCurrent() {
            if (controller.navigationState.state.value.storyRequest?.serial == storySerialAtGestureStart) {
                controller.detailRemovedFromBackStack()
            }
        }
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

        if (usesTwoPaneStoryScene) {
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
                popGestureStoryIfStillCurrent()
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
                popGestureStoryIfStillCurrent()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            completedPredictivePop = true
            popGestureStoryIfStillCurrent()
            // Keep the gesture's frozen current/previous destinations alive until the retained
            // layer compositor observes the pop, so neither a blank frame nor a deeper destination
            // appears during the handoff.
            repeat(3) { withFrameNanos { } }
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

    val settingsRequest = navigationSnapshot.settingsRequest
    val settingsAnimationScope = rememberCoroutineScope()
    var activeSettingsBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedSettingsPredictiveBack by remember { mutableStateOf(false) }

    LaunchedEffect(settingsRequest?.serial) {
        if (settingsRequest != null) completedSettingsPredictiveBack = false
    }

    fun closeSettings() {
        controller.closeSettings()
        controller.applySettingsChanges()
    }

    PredictiveBackHandler(
        enabled = navigationSnapshot.currentDestination == MainDestination.SETTINGS,
    ) { events ->
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
            // Keep the completed system-back layers alive until the retained Stories layer has
            // committed the pop. Dropping both transforms in the same composition can expose the
            // window background for one frame, which reads as a full-screen white flash.
            repeat(3) { withFrameNanos { } }
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

    val submissionsRequest = navigationSnapshot.submissionsRequest
    val submissionsAnimationScope = rememberCoroutineScope()
    var activeSubmissionsBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedSubmissionsPredictiveBack by remember { mutableStateOf(false) }

    LaunchedEffect(submissionsRequest?.serial) {
        if (submissionsRequest != null) completedSubmissionsPredictiveBack = false
    }

    PredictiveBackHandler(
        enabled = navigationSnapshot.currentDestination == MainDestination.SUBMISSIONS,
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

    val editorRequest = navigationSnapshot.editorRequest
    var editorPredictiveBackEnabled by remember(editorRequest?.serial) {
        mutableStateOf(false)
    }
    val editorAnimationScope = rememberCoroutineScope()
    var activeEditorBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedEditorPredictiveBack by remember { mutableStateOf(false) }

    LaunchedEffect(editorRequest?.serial) {
        if (editorRequest != null) completedEditorPredictiveBack = false
    }

    PredictiveBackHandler(
        enabled = navigationSnapshot.currentDestination == MainDestination.EDITOR &&
            editorPredictiveBackEnabled,
    ) { events ->
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
            repeat(3) { withFrameNanos { } }
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

    val coulombGasVisible = navigationSnapshot.coulombGasVisible
    PredictiveBackHandler(
        enabled = navigationSnapshot.currentDestination == MainDestination.IMMERSIVE,
    ) { events ->
        try {
            events.collect { }
            controller.closeCoulombGas()
        } catch (_: CancellationException) {
            // A cancelled gesture leaves the simulation open.
        }
    }

    val storiesController = controller.storiesComposeController
    PredictiveBackHandler(
        enabled = storiesController?.searching == true &&
            navigationSnapshot.currentDestination == MainDestination.STORIES,
    ) { events ->
        val searchController = storiesController ?: return@PredictiveBackHandler
        var started = false
        try {
            events.collect { event ->
                if (started) {
                    searchController.updateSearchBack(event.progress)
                } else {
                    started = searchController.startSearchBack(event.progress)
                }
            }
            searchController.finishSearchBack()
        } catch (_: CancellationException) {
            if (started) searchController.cancelSearchBack()
        }
    }

    val settingsTransitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    HarmonicAppRoot(
        navigation = navigationSnapshot,
        transitionOffsetPx = settingsTransitionOffsetPx,
        completedSettingsPredictiveBack = completedSettingsPredictiveBack,
        completedSubmissionsPredictiveBack = completedSubmissionsPredictiveBack,
        completedEditorPredictiveBack = completedEditorPredictiveBack,
        modifier = Modifier.background(HarmonicTheme.colors.background),
        basePredictiveModifier = (activeSettingsBackAnimation?.enterModifier ?: Modifier)
            .then(activeSubmissionsBackAnimation?.enterModifier ?: Modifier)
            .then(activeEditorBackAnimation?.enterModifier ?: Modifier),
        settingsPredictiveModifier = (activeSettingsBackAnimation?.exitModifier ?: Modifier)
            .then(
                if (storyParentDestination == MainDestination.SETTINGS) {
                    activeBackAnimation?.enterModifier ?: Modifier
                } else {
                    Modifier
                },
            ),
        submissionsPredictiveModifier =
            (activeSubmissionsBackAnimation?.exitModifier ?: Modifier).then(
                if (storyParentDestination == MainDestination.SUBMISSIONS) {
                    activeBackAnimation?.enterModifier ?: Modifier
                } else {
                    Modifier
                },
            ),
        editorPredictiveModifier = activeEditorBackAnimation?.exitModifier ?: Modifier,
        linkPreview = controller.commentsComposeController
            ?.takeIf { it.linkPreviewOverlay != null && !it.searchDialogVisible }
            ?.let { commentsController ->
                { AndroidCommentLinkPreviewOverlay(commentsController) }
            },
        base = {
            if (usesTwoPaneStoryScene) {
                MainNavigationScene(
                    storyRequest = storyRequest,
                    directive = directive,
                    paneProportion = paneProportion,
                    onBack = ::popMainBackStack,
                    stories = {
                        StoriesPane(
                            controller = controller,
                            statusBarColor = paneStatusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                    emptyDetail = { EmptyCommentsScreen() },
                    comments = { request ->
                        CommentsPane(
                            request = request,
                            controller = controller,
                            showUpButton = false,
                            statusBarColor = statusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                )
            } else {
                SinglePaneNavigationScene(
                    storyRequests = storyRequests,
                    completedPredictivePop = completedPredictivePop,
                    predictiveBackActive = activeBackAnimation != null,
                    showStoriesRoot = storyRequests.isEmpty() ||
                        navigationSnapshot.storyStackParentDestination == MainDestination.STORIES,
                    animateInitialStory = isTwoPane && storyRequest != null &&
                        storyParentDestination != MainDestination.STORIES,
                    storiesPredictiveModifier = if (
                        storyParentDestination == MainDestination.STORIES ||
                        storyParentDestination == MainDestination.STORY
                    ) {
                        activeBackAnimation?.enterModifier ?: Modifier
                    } else {
                        Modifier
                    },
                    commentsPredictiveModifier = activeBackAnimation?.exitModifier ?: Modifier,
                    stories = {
                        StoriesPane(
                            controller = controller,
                            statusBarColor = paneStatusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                    comments = { request ->
                        CommentsPane(
                            request = request,
                            controller = controller,
                            showUpButton = true,
                            statusBarColor = statusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                )
            }
        },
        settings = {
            if (
                settingsRequest != null ||
                !completedSettingsPredictiveBack
            ) {
                navigationSnapshot.lastSettingsRequest?.let { request ->
                    key(request.serial) {
                        ProvideSettingsPlatformStyle(
                            style = SettingsPlatformStyle(
                                topBarHeight = dimensionResource(
                                    R.dimen.compose_settings_toolbar_height,
                                ),
                                topBarNavigationHeight = dimensionResource(
                                    R.dimen.detail_toolbar_navigation_height,
                                ),
                                topBarNavigationInset = dimensionResource(
                                    R.dimen.detail_toolbar_navigation_inset,
                                ),
                                textStyle = TextStyle(
                                    platformStyle = PlatformTextStyle(
                                        includeFontPadding = true,
                                    ),
                                ),
                            ),
                        ) {
                            SettingsShell(
                                initialSection = controller
                                    .getInitialSettingsSectionRoute(request)
                                    ?.let(SettingsSection::fromRoute),
                                onBackFromSettings = ::closeSettings,
                                backHandlerEnabled = navigationSnapshot.currentDestination ==
                                    MainDestination.SETTINGS,
                                onSectionChanged = controller::updateSettingsSection,
                                onThemeChanged = {
                                    ThemeUtils.setupTheme(activity)
                                    appearance.refreshSelection()
                                },
                            )
                        }
                    }
                }
            }
        },
        submissions = {
            if (submissionsRequest != null || !completedSubmissionsPredictiveBack) {
                navigationSnapshot.lastSubmissionsRequest?.let { request ->
                    key(request.serial) {
                        val coordinator = remember(request.serial) {
                            SubmissionsCoordinator(
                                activity = activity,
                                sessionKey = request.serial,
                                userName = request.userName,
                                scene = controller.scene,
                                navigator = SubmissionsCoordinator.Navigator { destination ->
                                    controller.openStory(destination)
                                },
                            )
                        }
                        DisposableEffect(coordinator) {
                            onDispose(coordinator::close)
                        }
                        AndroidSubmissionsScreen(
                            controller = coordinator.composeController,
                            onBack = controller::closeSubmissions,
                        )
                    }
                }
            }
        },
        editor = {
            if (editorRequest != null || !completedEditorPredictiveBack) {
                navigationSnapshot.lastEditorRequest?.let { request ->
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
                                EditorComposeController()
                            }
                            val coordinator = remember(request.serial) {
                                ComposeEditorCoordinator(
                                    activity,
                                    request.destination,
                                    controller,
                                    controller::closeEditor,
                                )
                            }
                            DisposableEffect(coordinator) {
                                onDispose(coordinator::close)
                            }
                            SideEffect {
                                coordinator.attachController(editorController)
                            }
                            ComposeEditorScreen(
                                type = coordinator.type,
                                parentText = coordinator.parentText,
                                postTitle = coordinator.postTitle,
                                user = coordinator.user,
                                submitting = editorController.submitting,
                                onPredictiveBackEnabledChanged = {
                                    editorPredictiveBackEnabled = it
                                },
                                onClose = controller::closeEditor,
                                onSubmit = coordinator::submit,
                                onOpenLink = { uiDependencies.links.open(it) },
                            )
                        }
                    }
                }
            }
        },
        immersive = {
            DisposableEffect(activity) {
                activity.setImmersiveContentEnabled(true)
                onDispose {
                    activity.setImmersiveContentEnabled(false)
                }
            }
            CoulombGasScreen()
        },
        foreground = {
            if (navigationSnapshot.welcomeDialogVisible) {
            AndroidWelcomeSettingsDialog(
                styleChooser = false,
                onDismiss = controller::dismissWelcomeDialog,
            )
        }

        if (navigationSnapshot.changelogDialogVisible) {
            SettingsChangelogDialog(
                onDismiss = controller::dismissChangelogDialog,
                onOpenGithub = {
                    uiDependencies.links.open(uiDependencies.metadata.projectUrl)
                },
            )
        }

        if (navigationSnapshot.cacheStoriesDialogVisible) {
            CacheStoriesDialog(
                initialStoryCount = uiDependencies.userSettings
                    .cache.storiesToCache,
                integratedWebView = uiDependencies.userSettings.reading.integratedWebView,
                onDismiss = controller::dismissCacheStoriesDialog,
                onConfirm = controller::confirmCacheStories,
            )
        }

        if (navigationSnapshot.loginDialogVisible) {
            AndroidLoginDialog(
                onDismiss = controller::dismissLoginDialog,
            )
        }

        navigationSnapshot.captchaRequest?.let { request ->
            key(request.serial) {
                CaptchaDialog(
                    challenge = request.challenge,
                    onDismiss = controller::dismissCaptchaDialog,
                    onCaptchaResponse = controller::completeCaptchaDialog,
                )
            }
        }

        navigationSnapshot.userRequest?.let { request ->
            key(request.serial) {
                AndroidUserSettingsDialog(
                    userName = request.userName,
                    onDismiss = controller::dismissUserDialog,
                    onTagChanged = controller::notifyUserTagChanged,
                )
            }
        }

        navigationSnapshot.failureRequest?.let { request ->
            key(request.serial) {
                FailureDetailDialog(
                    title = request.title,
                    message = request.message,
                    showCopyComment = request.clipboardText != null,
                    onCopyComment = {
                        request.clipboardText?.let { text ->
                            uiDependencies.platform.clipboard.copy("Hacker News comment", text)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                controller.showMessage("Comment copied to clipboard")
                            }
                        }
                        controller.dismissFailureDetailDialog()
                    },
                    onDismiss = controller::dismissFailureDetailDialog,
                )
            }
        }

            UserMessageSnackbarHost(
                messages = uiDependencies.userMessages,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(100f),
            )
        },
    )

}

@Composable
private fun StoriesPane(
    controller: MainNavigationController,
    statusBarColor: Color = Color.Transparent,
    statusBarHeight: Dp = 0.dp,
    drawStatusBarProtection: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        val storiesController = controller.storiesComposeController
        storiesController?.let {
            AndroidStoriesScreen(it, controller::updateVisibleStories)
        }
        if (drawStatusBarProtection) {
            StatusBarProtection(
                color = statusBarColor,
                statusBarHeight = statusBarHeight,
            )
        }
        storiesController
            ?.takeIf { it.storyPreviewOverlay != null }
            ?.let { AndroidStoryPreviewOverlay(it) }
    }
}

@Composable
private fun CommentsPane(
    request: MainStoryRequest,
    controller: MainNavigationController,
    showUpButton: Boolean,
    statusBarColor: Color = Color.Transparent,
    statusBarHeight: Dp = 0.dp,
    drawStatusBarProtection: Boolean = false,
) {
    val activity = LocalActivity.current as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeCoordinator = remember(controller, activity, request.serial) {
        controller.retainCommentsCoordinator(activity, request)
    }
    SideEffect { controller.attachCommentsCoordinator(activeCoordinator) }
    DisposableEffect(controller, activeCoordinator, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activeCoordinator.onStart()
                Lifecycle.Event.ON_RESUME -> activeCoordinator.onResume()
                Lifecycle.Event.ON_STOP -> activeCoordinator.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.releaseCommentsCoordinator(activeCoordinator)
        }
    }
    CommentsHazeHost {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { activeCoordinator.webViewRoot },
            )
            activeCoordinator.composeUiController?.let { commentsController ->
                val showFloatingUpButton = showUpButton &&
                    commentsController.displaySettings?.showUpButton == true
                if (!commentsController.webViewFullscreen) {
                    CommentsScaffold(
                        controller = commentsController,
                        reserveUpButtonInset = showFloatingUpButton,
                    )
                }
                val showStatusBarProtection = drawStatusBarProtection &&
                    !(commentsController.integratedWebView && commentsController.isScrolledToTop)
                if (showStatusBarProtection) {
                    StatusBarProtection(
                        color = statusBarColor,
                        statusBarHeight = statusBarHeight,
                    )
                }
                if (showFloatingUpButton) {
                    CommentsUpButton(
                        onClick = controller::closeStory,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 4.dp)
                            .zIndex(101f),
                    )
                }
                commentsController.displaySettings?.let { settings ->
                    Box(Modifier.fillMaxSize().zIndex(102f)) {
                        AndroidCommentActionOverlay(commentsController, settings)
                    }
                }
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

private const val LEGACY_COMMENTS_PANE_WEIGHT = 5f
