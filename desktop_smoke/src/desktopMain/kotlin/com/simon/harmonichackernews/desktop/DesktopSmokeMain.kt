package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.StoriesFeatureHost
import com.simon.harmonichackernews.app.createStoriesStore
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.presentation.EmbeddedWebContentSession
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.WebContentDriver
import com.simon.harmonichackernews.presentation.WebContentDriverState
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.editor.SharedEditorScreen
import com.simon.harmonichackernews.ui.navigation.SharedHarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.SharedSinglePaneNavigationScene
import com.simon.harmonichackernews.ui.settings.SettingsListScreen
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SharedSettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.rememberSettingsNavigationStore
import com.simon.harmonichackernews.ui.stories.SharedStoriesRoute
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.stringResource

fun main() {
    val bootstrap = DesktopHarmonicAppBootstrap.inMemory(
        userAgent = "Harmonic-HN-Desktop-Smoke",
    )
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Harmonic KMP smoke host",
            ) {
                ProvideHarmonicUiDependencies(
                    HarmonicUiDependencies(bootstrap.app, bootstrap.scene),
                ) {
                    HarmonicTheme(
                        colors = desktopPalette.colors,
                        colorScheme = desktopPalette.colorScheme,
                    ) {
                        Surface(Modifier.fillMaxSize()) {
                            DesktopSmokeContent(bootstrap.app, bootstrap.scene)
                        }
                    }
                }
            }
        }
    } finally {
        bootstrap.close()
    }
}

@Composable
private fun DesktopSmokeContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
) {
    val navigation by scene.navigation.state.collectAsState()
    val transitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    SharedHarmonicAppRoot(
        navigation = navigation,
        transitionOffsetPx = transitionOffsetPx,
        completedSettingsPredictiveBack = false,
        completedSubmissionsPredictiveBack = false,
        completedEditorPredictiveBack = false,
        base = {
            SharedSinglePaneNavigationScene(
                storyRequest = navigation.storyRequest,
                lastStoryRequest = navigation.lastStoryRequest,
                completedPredictivePop = false,
                predictiveBackActive = false,
                showStoriesPane = true,
                stories = { DesktopStoriesContent(app, scene) },
                comments = { request ->
                    DesktopCommentsBrowserContract(
                        app = app,
                        storyId = request.storyId,
                        scrollToCommentId = request.route.scrollToCommentId,
                        onClose = scene.navigation::detailRemovedFromBackStack,
                    )
                },
            )
        },
        settings = {
            DesktopSettingsShell(
                app = app,
                scene = scene,
                initialSection = SettingsSection.fromRoute(
                    navigation.currentSettingsSectionRoute.orEmpty(),
                ),
            )
        },
        submissions = {
            DesktopSmokeDestination(
                title = "Shared submissions destination",
                detail = navigation.submissionsRequest?.userName.orEmpty(),
                onClose = scene.navigation::closeSubmissions,
            )
        },
        editor = {
            navigation.editorRequest?.destination?.let { destination ->
                SharedEditorScreen(
                    type = destination.type,
                    parentText = destination.parentText,
                    postTitle = destination.postTitle,
                    user = destination.userName,
                    submitting = false,
                    onClose = scene.navigation::closeEditor,
                    onSubmit = {},
                )
            }
        },
        immersive = {
            DesktopSmokeDestination(
                title = "Shared immersive destination",
                detail = "Coulomb gas host layer",
                onClose = scene.navigation::closeCoulombGas,
            )
        },
        foreground = {},
    )
}

@Composable
private fun DesktopStoriesContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val defaultStoryHeightPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val store = remember(app, scene, scope) {
        app.createStoriesStore(
            StoriesFeatureHost(
                scope = scope,
                sessionState = scene.sessions.stories,
                platform = app.storiesPlatformDependencies(),
                userSettings = app.userSettings,
            ),
        )
    }
    val controller = remember(store, defaultStoryHeightPx) {
        lateinit var created: StoriesComposeController
        val callbacks = object : StoriesFeatureListener.PlatformCallbacks {
            override fun onSearchStateChanged(searching: Boolean) {
                created.endPredictiveBack()
            }

            override fun showFrontDatePicker() {
                val state = store.state.value
                created.showFrontDatePicker(
                    state.frontDateSelectedMillis,
                    state.frontDateEarliestMillis,
                    state.frontDateLatestMillis,
                )
            }

            override fun onStoryPreviewVisibilityChanged(showing: Boolean) = Unit
            override fun isSplitLayout(): Boolean = false
        }
        created = StoriesComposeController.create(
            defaultStoryHeightPx = defaultStoryHeightPx,
            savedItemState = store.savedItemState,
            listener = StoriesFeatureListener(store, callbacks),
        )
        created
    }
    val state by store.state.collectAsState()
    val colors = HarmonicTheme.colors
    val filterColors = remember(colors) {
        HarmonicFilterButtonColors(
            checkedBackground = colors.storyNormal,
            checkedText = colors.background,
            checkedStroke = colors.storyNormal,
            uncheckedText = colors.storyNormal,
            uncheckedStroke = colors.outlineVariant,
        )
    }

    DisposableEffect(store) {
        store.start()
        store.onStart()
        onDispose {
            store.onStop()
            store.close()
        }
    }
    LaunchedEffect(state, controller) {
        val lastUpdatedText = state.lastUpdatedMillis?.let { millis ->
            PresentationCopy.lastUpdated(app.platform.timeFormatting.time(millis))
        }
        controller.updateContent(
            StoriesScreenStateFactory.create(
                state,
                StoriesPlatformPresentation(lastUpdatedText, contentInsetStartPx = 0),
            ),
        )
    }
    LaunchedEffect(store, controller, scene) {
        store.effects.collect { effect ->
            when (effect) {
                is StoriesRuntimeEffect.OpenStory ->
                    scene.navigation.openStory(effect.destination)
                is StoriesRuntimeEffect.OpenExternalLink ->
                    scene.links.openExternal(ExternalLinkRequest(effect.url))
                is StoriesRuntimeEffect.Platform -> when (val platform = effect.effect) {
                    StoriesPlatformEffect.OpenSettings -> scene.navigation.openSettings(null)
                    StoriesPlatformEffect.RequestLogin -> scene.navigation.showLoginDialog()
                    is StoriesPlatformEffect.OpenProfile ->
                        scene.navigation.showUserDialog(platform.userName)
                    StoriesPlatformEffect.ShowCacheDialog ->
                        scene.navigation.showCacheStoriesDialog()
                    StoriesPlatformEffect.OpenSubmitEditor ->
                        scene.navigation.openEditor(EditorDestination(EditorType.POST))
                }
                is StoriesRuntimeEffect.PreviewActionCompleted ->
                    controller.finishStoryPreviewAction(effect.storyId, effect.action)
                is StoriesRuntimeEffect.StoryChanged ->
                    effect.storyId?.let(controller::invalidateStory)
                StoriesRuntimeEffect.LoginRequired -> scene.navigation.showLoginDialog()
                is StoriesRuntimeEffect.UserMessage -> scene.userMessages.show(effect.message)
                is StoriesRuntimeEffect.SavedActionFailed -> {
                    if (effect.presentation.showDetails) {
                        scene.navigation.showFailureDetailDialog(
                            effect.presentation.failureSummary,
                            effect.presentation.failureDetail,
                            null,
                        )
                    }
                    scene.userMessages.show(effect.presentation.message)
                }
            }
        }
    }

    SharedStoriesRoute(
        controller = controller,
        tintStore = app.storyResourceTints,
        commentText = { AnnotatedString(it) },
        filterColors = filterColors,
        extraCompactSelectedText = false,
        compactSelectedText = false,
    )
}

@Composable
private fun DesktopSmokeDestination(
    title: String,
    detail: String,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(detail)
        Button(onClick = onClose) { Text("Back") }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun DesktopSettingsShell(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    initialSection: SettingsSection?,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo)
    }
    val navigation = rememberSettingsNavigationStore(
        initialSection = initialSection,
        twoPane = directive.maxHorizontalPartitions > 1,
    )

    LaunchedEffect(initialSection) {
        initialSection?.let(navigation::navigateTo)
    }

    SharedSettingsNavigationShell(
        navigation = navigation,
        directive = directive,
        isFoldable = adaptiveInfo.windowPosture.hingeList.isNotEmpty(),
        tabletPaneHorizontalPadding = 24.dp,
        onBackFromSettings = scene.navigation::closeSettings,
        onSectionChanged = { section ->
            scene.navigation.updateSettingsSection(section.route)
        },
        renderList = { selectedSection, showSelection, onBack, onSectionSelected ->
            SettingsListScreen(
                selectedSection = selectedSection,
                showSelection = showSelection,
                showDebugSettings = app.metadata.debugSettingsEnabled,
                onBack = onBack,
                onSectionSelected = onSectionSelected,
            )
        },
        renderDetail = { section, singlePane, onBack, onNavigate ->
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(section.titleResource),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text("Shared settings route: ${section.route}")
                Text("The desktop backend can replace this detail adapter section by section.")
                if (section == SettingsSection.About) {
                    Button(onClick = { onNavigate(SettingsSection.Licenses, singlePane) }) {
                        Text("Licenses")
                    }
                }
                if (singlePane) Button(onClick = onBack) { Text("Back to settings") }
            }
        },
    )
}

@Composable
private fun DesktopCommentsBrowserContract(
    app: HarmonicAppComposition,
    storyId: Int,
    scrollToCommentId: Int,
    onClose: () -> Unit,
) {
    val driver = remember { DesktopSmokeWebContentDriver() }
    val session = remember(app, driver) {
        EmbeddedWebContentSession(app.webContent.createRuntime(), driver)
    }
    val driverState by session.controller.driverState.collectAsState()
    var pageText by remember { mutableStateOf<String?>(null) }
    val url = remember(storyId, scrollToCommentId) {
        HackerNewsLinks.itemUrl(storyId, scrollToCommentId)
    }

    LaunchedEffect(session, url) {
        session.recordRequestedUrl(url)
        session.configureReader(
            featureEnabled = true,
            integrated = true,
            defaultEnabled = false,
        )
        session.controller.load(url, archiveDomains = emptyList())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Shared comments/browser contract", style = MaterialTheme.typography.headlineMedium)
        Text("Requested through the common URL policy: $url")
        Text("Desktop driver state: ${driverState.currentUrl ?: "idle"}")
        Text(
            "This smoke adapter validates the shared browser session; a production desktop " +
                "backend supplies its native web engine here.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = session.controller::reload) { Text("Reload") }
            Button(
                onClick = {
                    session.controller.readPageText { text -> pageText = text }
                },
            ) { Text("Read page text") }
            Button(onClick = onClose) { Text("Back") }
        }
        pageText?.let { Text(it) }
    }
}

private class DesktopSmokeWebContentDriver : WebContentDriver {
    private val mutableState = MutableStateFlow(WebContentDriverState())
    override val state: StateFlow<WebContentDriverState> = mutableState.asStateFlow()

    override fun load(url: String) {
        mutableState.value = WebContentDriverState(
            currentUrl = url,
            loading = false,
            pageReady = true,
        )
    }

    override fun reload() {
        state.value.currentUrl?.let(::load)
    }

    override fun goBack(): Boolean = false

    override fun evaluateJavaScript(script: String, onResult: (String?) -> Unit) {
        onResult(null)
    }

    override fun readPageText(onResult: (String?) -> Unit) {
        onResult("Desktop smoke page for ${state.value.currentUrl.orEmpty()}")
    }
}

private val desktopPalette = HarmonicThemeCatalog.resolve("material_light", systemDark = false)
