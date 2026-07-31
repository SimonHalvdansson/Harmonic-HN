package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.Changelog
import com.simon.harmonichackernews.utils.Utils

enum class SettingsSection(
    val route: String,
    val title: String,
) {
    Appearance("appearance", "Appearance"),
    Stories("stories", "Stories"),
    Comments("comments", "Comments"),
    WebLinks("web_links", "Web and links"),
    FiltersTags("filters_tags", "Filters and tags"),
    AiSummary("ai_summary", "AI summarization"),
    Data("data", "Data"),
    Debug("debug", "Debug"),
    About("about", "About"),
    Licenses("licenses", "Third-party licenses"),
    ;

    companion object {
        fun fromRoute(route: String): SettingsSection? =
            entries.firstOrNull { it.route == route }
    }
}

private data object SettingsListDestination : NavKey

private data class SettingsDetailDestination(
    val section: SettingsSection,
) : NavKey

private const val ActivityTransitionDurationMillis = 450
private const val ActivityEnterAlphaDelayMillis = 50
private const val ActivityExitAlphaDelayMillis = 35
private const val ActivityAlphaDurationMillis = 83

private fun aospFastOutExtraSlowInEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private fun activityOpenTransition(offsetPx: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        initialOffsetX = { offsetPx },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = ActivityAlphaDurationMillis,
            delayMillis = ActivityEnterAlphaDelayMillis,
            easing = LinearEasing,
        ),
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        targetOffsetX = { -offsetPx },
    ),
    targetContentZIndex = 1f,
)

private fun activityCloseTransition(offsetPx: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        initialOffsetX = { -offsetPx },
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        targetOffsetX = { offsetPx },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = ActivityAlphaDurationMillis,
            delayMillis = ActivityExitAlphaDelayMillis,
            easing = LinearEasing,
        ),
    ),
    targetContentZIndex = -1f,
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsShell(
    initialSection: SettingsSection?,
    onBackFromSettings: () -> Unit,
    onSectionChanged: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
    onRequestRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backStack = remember {
        mutableStateListOf<NavKey>().apply {
            add(SettingsListDestination)
            initialSection?.let { add(SettingsDetailDestination(it)) }
        }
    }
    val selectedSection =
        (backStack.lastOrNull() as? SettingsDetailDestination)?.section
            ?: SettingsSection.Appearance

    LaunchedEffect(selectedSection) {
        onSectionChanged(selectedSection)
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isFoldable = adaptiveInfo.windowPosture.hingeList.isNotEmpty() ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    val directive = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 12.dp)
    }
    val foldablePaneExpansionState = rememberPaneExpansionState(
        anchors = remember { listOf(PaneExpansionAnchor.Proportion(0.5f)) },
        initialAnchoredIndex = 0,
    )
    val sceneStrategy = rememberListDetailSceneStrategy<NavKey>(
        directive = directive,
        paneExpansionState = foldablePaneExpansionState.takeIf { isFoldable },
    )
    val showDetailNavigation = directive.maxHorizontalPartitions == 1
    val activityTransitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }

    fun navigateTo(section: SettingsSection) {
        if ((backStack.lastOrNull() as? SettingsDetailDestination)?.section == section) {
            return
        }
        if (backStack.lastOrNull() is SettingsDetailDestination) {
            backStack.removeLastOrNull()
        }
        backStack.add(SettingsDetailDestination(section))
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            onBackFromSettings()
        }
    }

    LaunchedEffect(initialSection) {
        initialSection?.let(::navigateTo)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = ::navigateBack,
            sceneStrategies = listOf(sceneStrategy),
            transitionSpec = {
                activityOpenTransition(activityTransitionOffsetPx)
            },
            popTransitionSpec = {
                activityCloseTransition(activityTransitionOffsetPx)
            },
            predictivePopTransitionSpec = { _ ->
                activityCloseTransition(activityTransitionOffsetPx)
            },
            entryProvider = entryProvider {
                entry<SettingsListDestination>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            AppearanceSettingsScreen(
                                showNavigation = false,
                                onBack = ::navigateBack,
                                onNavigate = ::navigateTo,
                                onThemeChanged = onThemeChanged,
                            )
                        },
                    ),
                ) {
                    SettingsListScreen(
                        selectedSection = selectedSection,
                        showSelection = !showDetailNavigation,
                        onBack = onBackFromSettings,
                        onSectionSelected = ::navigateTo,
                    )
                }

                entry<SettingsDetailDestination>(
                    metadata = ListDetailSceneStrategy.detailPane(),
                ) { destination ->
                    when (destination.section) {
                        SettingsSection.Appearance -> AppearanceSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                            onNavigate = ::navigateTo,
                            onThemeChanged = onThemeChanged,
                        )

                        SettingsSection.Stories -> StoriesSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                            onRequestRestart = onRequestRestart,
                        )

                        SettingsSection.Comments -> CommentsSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                        )

                        SettingsSection.WebLinks -> WebLinksSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                        )

                        SettingsSection.FiltersTags -> FiltersTagsSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                        )

                        SettingsSection.AiSummary -> AiSummarySettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                        )

                        SettingsSection.Data -> DataSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                            onRequestRestart = onRequestRestart,
                        )

                        SettingsSection.Debug -> DebugSettingsScreen(
                            showNavigation = showDetailNavigation,
                            onBack = ::navigateBack,
                        )

                        SettingsSection.About -> AboutScreen(
                            onBack = ::navigateBack,
                            onOpenGithub = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/SimonHalvdansson/Harmonic-HN".toUri(),
                                    ),
                                )
                            },
                            onOpenChangelog = {
                                SimpleMessageDialogController.show(
                                    title = "Changelog",
                                    message = Changelog.getFormatted(context).toString(),
                                )
                            },
                            onOpenLicenses = {
                                backStack.add(
                                    SettingsDetailDestination(SettingsSection.Licenses),
                                )
                            },
                            onOpenPrivacy = {
                                Utils.launchCustomTab(
                                    context as androidx.fragment.app.FragmentActivity,
                                    "https://simonhalvdansson.github.io/harmonic_privacy.html",
                                )
                            },
                            showNavigation = showDetailNavigation,
                            singlePane = showDetailNavigation,
                        )

                        SettingsSection.Licenses -> LicensesScreen(
                            onBack = ::navigateBack,
                            onOpenLicense = { url ->
                                Utils.launchCustomTab(
                                    context as androidx.fragment.app.FragmentActivity,
                                    url,
                                )
                            },
                            singlePane = showDetailNavigation,
                        )

                    }
                }
            },
        )
    }

    SimpleMessageDialogController.Content()
}
