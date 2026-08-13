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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.presentation.EmbeddedWebContentSession
import com.simon.harmonichackernews.presentation.StoryFeedRefreshPolicy
import com.simon.harmonichackernews.presentation.WebContentDriver
import com.simon.harmonichackernews.presentation.WebContentDriverState
import com.simon.harmonichackernews.settings.StoryBooleanPreference
import com.simon.harmonichackernews.settings.StoryStringPreference
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.editor.SharedEditorScreen
import com.simon.harmonichackernews.ui.navigation.SharedHarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.SharedSinglePaneNavigationScene
import com.simon.harmonichackernews.ui.settings.SettingsListScreen
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SharedSettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.rememberSettingsNavigationStore
import com.simon.harmonichackernews.ui.stories.SharedStoriesRoot
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
                ProvideHarmonicUiDependencies(HarmonicUiDependencies(bootstrap.app)) {
                    HarmonicTheme(
                        colors = desktopPalette.colors,
                        colorScheme = desktopPalette.colorScheme,
                    ) {
                        Surface(Modifier.fillMaxSize()) {
                            DesktopSmokeContent(bootstrap.app)
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
private fun DesktopSmokeContent(app: HarmonicAppComposition) {
    val navigation by app.navigation.state.collectAsState()
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
                stories = { DesktopStoriesContent(app) },
                comments = { request ->
                    DesktopCommentsBrowserContract(
                        app = app,
                        storyId = request.storyId,
                        scrollToCommentId = request.route.scrollToCommentId,
                        onClose = app.navigation::detailRemovedFromBackStack,
                    )
                },
            )
        },
        settings = {
            DesktopSettingsShell(
                app = app,
                initialSection = SettingsSection.fromRoute(
                    navigation.currentSettingsSectionRoute.orEmpty(),
                ),
            )
        },
        submissions = {
            DesktopSmokeDestination(
                title = "Shared submissions destination",
                detail = navigation.submissionsRequest?.userName.orEmpty(),
                onClose = app.navigation::closeSubmissions,
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
                    onClose = app.navigation::closeEditor,
                    onSubmit = {},
                )
            }
        },
        immersive = {
            DesktopSmokeDestination(
                title = "Shared immersive destination",
                detail = "Coulomb gas host layer",
                onClose = app.navigation::closeCoulombGas,
            )
        },
        foreground = {},
    )
}

@Composable
private fun DesktopStoriesContent(app: HarmonicAppComposition) {
    val settings by app.settings.updates.collectAsState(initial = app.settings.snapshot())
    var menuExpanded by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    val refreshSource = remember {
        StoryFeedRefreshPolicy.plan(
            searching = false,
            storyType = StoryType.TOP_STORIES,
            showSwipeRefreshIndicator = false,
            showMainLoadingIndicator = false,
            listIsEmpty = true,
        ).source
    }

    SharedStoriesRoot(
        searching = searchMode,
        suppressSearchAutoFocus = false,
        predictiveBackActive = false,
        predictiveBackProgress = 0f,
        backgroundColor = desktopPalette.colors.background,
        mainLayer = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Shared stories smoke host", style = MaterialTheme.typography.headlineMedium)
                Text("Refresh policy selected: $refreshSource")
                Text("Real shared graph: CIO network, settings, sessions, and navigation are ready")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            app.settings.setStoryString(
                                StoryStringPreference.DISPLAY_STYLE,
                                if (settings.story.cardStyle) "standard" else "card",
                            )
                        },
                    ) {
                        Text(if (settings.story.cardStyle) "Use standard row" else "Use card row")
                    }
                    Button(
                        onClick = {
                            app.settings.setStoryBoolean(
                                StoryBooleanPreference.COMPACT_VIEW,
                                !settings.story.compactView,
                            )
                        },
                    ) {
                        Text(
                            if (settings.story.compactView) {
                                "Use comfortable spacing"
                            } else {
                                "Use compact spacing"
                            },
                        )
                    }
                    Button(onClick = { menuExpanded = true }) { Text("Shared menu") }
                    Button(
                        onClick = {
                            app.navigation.openEditor(EditorDestination(EditorType.POST))
                        },
                    ) { Text("Shared editor") }
                    Button(onClick = { searchMode = true }) { Text("Shared search root") }
                    Button(onClick = { app.navigation.openSettings(null) }) { Text("Settings") }
                    Button(
                        onClick = { app.navigation.openStory(StoryDestination(storyId = 8863)) },
                    ) { Text("Comments host") }
                    Button(
                        onClick = { app.navigation.openSubmissions("pg") },
                    ) { Text("Submissions") }
                    HarmonicDropdownMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                    ) {
                        HarmonicMenuText(
                            text = "Desktop target compiled this menu",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                StoryItem(
                    model = SettingsStoryPreviewModel,
                    style = StoryItemStyle(
                        previewImageMode = "large",
                        borderlessLargeImage = false,
                        compact = settings.story.compactView,
                        showSummary = true,
                        showFavicon = true,
                        showPoints = true,
                        compactPoints = false,
                        includeTopLevelDomain = true,
                        showCommentCount = true,
                        showIndex = true,
                        commentsOnLeft = false,
                        tintCard = true,
                        cardStyle = settings.story.cardStyle,
                        useHotnessIcon = false,
                        preferredFont = "googlesansflexrounded",
                        textSize = 17.5f,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        searchLayer = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Shared search layer", style = MaterialTheme.typography.headlineMedium)
                Text("This verifies the portable stories root on the desktop target.")
                Button(onClick = { searchMode = false }) { Text("Back to stories") }
            }
        },
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
        onBackFromSettings = app.navigation::closeSettings,
        onSectionChanged = { section ->
            app.navigation.updateSettingsSection(section.route)
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
