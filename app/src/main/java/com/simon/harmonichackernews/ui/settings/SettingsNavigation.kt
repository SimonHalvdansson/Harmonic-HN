package com.simon.harmonichackernews.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android adapter for window facts, predictive back, and native settings effects. */
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
    val dependencies = LocalHarmonicUiDependencies.current
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val configuration = LocalConfiguration.current
    val supportsTwoPane = configuration.smallestScreenWidthDp >= 600
    val hasHingeAngleSensor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    val isFoldable = adaptiveInfo.windowPosture.hingeList.isNotEmpty() || hasHingeAngleSensor
    val directive = remember(adaptiveInfo, supportsTwoPane, isFoldable) {
        val adaptiveDirective = calculatePaneScaffoldDirective(adaptiveInfo)
        adaptiveDirective.copy(
            maxHorizontalPartitions = if (supportsTwoPane) {
                adaptiveDirective.maxHorizontalPartitions
            } else {
                1
            },
            horizontalPartitionSpacerSize = if (isFoldable) 12.dp else 16.dp,
        )
    }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val navigation = rememberSettingsNavigationStore(initialSection, isTwoPane)
    val navigationState by navigation.state.collectAsState()
    val backAnimationScope = rememberCoroutineScope()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var isBackAnimationRunning by remember { mutableStateOf(false) }

    fun popSettingsBackStack() {
        if (!navigation.navigateBack()) onBackFromSettings()
    }

    PredictiveBackHandler(enabled = navigationState.canNavigateBackWithinSettings) { events ->
        if (isTwoPane) {
            try {
                events.collect {}
                popSettingsBackStack()
            } catch (_: CancellationException) {
                // A cancelled two-pane gesture leaves the selected detail unchanged.
            }
            return@PredictiveBackHandler
        }

        isBackAnimationRunning = true
        var animation: DefaultActivityPredictiveBackAnimation? = null
        try {
            events.collect { event ->
                val current = animation ?: DefaultActivityPredictiveBackAnimation(event).also {
                    animation = it
                    activeBackAnimation = it
                }
                backAnimationScope.launch { current.animate(event) }
            }
            val current = animation
            if (current == null) {
                isBackAnimationRunning = false
                popSettingsBackStack()
                return@PredictiveBackHandler
            }
            current.finish()
            popSettingsBackStack()
            activeBackAnimation = null
            isBackAnimationRunning = false
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeBackAnimation === animation) activeBackAnimation = null
                isBackAnimationRunning = false
            }
        }
    }

    LaunchedEffect(initialSection) { initialSection?.let(navigation::navigateTo) }

    SharedSettingsNavigationShell(
        navigation = navigation,
        directive = directive,
        isFoldable = isFoldable,
        tabletPaneHorizontalPadding = if (isTwoPane && !isFoldable) {
            dimensionResource(R.dimen.settings_extra_pane_padding)
        } else {
            0.dp
        },
        onBackFromSettings = onBackFromSettings,
        onSectionChanged = onSectionChanged,
        modifier = modifier,
        predictiveBackOverlay = activeBackAnimation?.let {
            SettingsPredictiveBackOverlay(it.enterModifier, it.exitModifier)
        },
        renderList = { selectedSection, showSelection, onBack, onSectionSelected ->
            SettingsListScreen(
                selectedSection = selectedSection,
                showSelection = showSelection,
                showDebugSettings = dependencies.metadata.debugSettingsEnabled,
                onBack = onBack,
                onSectionSelected = onSectionSelected,
            )
        },
        renderDetail = { section, singlePane, onBack, onNavigate ->
            when (section) {
                SettingsSection.Appearance -> AppearanceSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onNavigate = { onNavigate(it, singlePane) },
                    onThemeChanged = onThemeChanged,
                )
                SettingsSection.Stories -> StoriesSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onRequestRestart = onRequestRestart,
                )
                SettingsSection.Comments -> CommentsSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                )
                SettingsSection.WebLinks -> SharedWebLinksSettingsRoute(
                    repository = dependencies.settings,
                    showNavigation = singlePane,
                    onBack = onBack,
                )
                SettingsSection.FiltersTags -> FiltersTagsSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                )
                SettingsSection.AiSummary -> AiSummarySettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                )
                SettingsSection.Data -> DataSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onRequestRestart = onRequestRestart,
                )
                SettingsSection.Debug -> DebugSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onOpenLinkPreviews = {
                        onNavigate(SettingsSection.DebugLinkPreviews, true)
                    },
                )
                SettingsSection.DebugLinkPreviews -> LinkPreviewsDebugScreen(onBack)
                SettingsSection.About -> AboutScreen(
                    onBack = onBack,
                    onOpenGithub = { dependencies.links.open(dependencies.metadata.projectUrl) },
                    onOpenChangelog = ChangelogDialogController::show,
                    onOpenLicenses = {
                        onNavigate(SettingsSection.Licenses, true)
                    },
                    onOpenPrivacy = { dependencies.links.open(dependencies.metadata.privacyUrl) },
                    showNavigation = singlePane,
                    singlePane = singlePane,
                )
                SettingsSection.Licenses -> LicensesScreen(
                    onBack = onBack,
                    onOpenLicense = { dependencies.links.open(it) },
                    singlePane = singlePane,
                )
            }
        },
    )

    SimpleMessageDialogController.Content()
    ChangelogDialogController.Content()
}
