package com.simon.harmonichackernews.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import com.simon.harmonichackernews.platform.accountOrNull
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.about.AndroidAboutScreen
import com.simon.harmonichackernews.ui.licenses.AndroidLicensesScreen
import com.simon.harmonichackernews.ui.navigation.animatedExtraPanePadding
import com.simon.harmonichackernews.ui.navigation.StatusBarProtection

/** Android adapter for window facts, predictive back, and native settings effects. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsShell(
    initialSection: SettingsSection?,
    onBackFromSettings: () -> Unit,
    backHandlerEnabled: Boolean,
    onSectionChanged: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dependencies = LocalHarmonicUiDependencies.current
    val settingsAccountState by dependencies.platform.accounts.accountState.collectAsStateWithLifecycle()
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
    val predictiveBack = rememberSettingsPredictiveBack(
        navigation, isTwoPane, backHandlerEnabled, onBackFromSettings,
    )

    SettingsNavigationShell(
        navigation = navigation,
        directive = directive,
        supportsTwoPane = supportsTwoPane,
        isFoldable = isFoldable,
        tabletPaneHorizontalPadding = animatedExtraPanePadding(),
        onBackFromSettings = onBackFromSettings,
        onSectionChanged = onSectionChanged,
        modifier = modifier,
        predictiveBackOverlay = predictiveBack.overlay,
        completedPredictiveBack = predictiveBack.completed,
        renderList = { selectedSection, showSelection, onBack, onSectionSelected ->
            SettingsListScreen(
                selectedSection = selectedSection,
                showSelection = showSelection,
                showDebugSettings = dependencies.metadata.debugSettingsEnabled,
                loggedIn = settingsAccountState.accountOrNull != null,
                onBack = onBack,
                onSectionSelected = onSectionSelected,
            )
        },
        renderDetail = { section, singlePane, onBack, onNavigate ->
            when (section) {
                SettingsSection.Appearance -> AndroidAppearanceSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onNavigate = { target ->
                        onNavigate(
                            target,
                            singlePane || target == SettingsSection.Theme ||
                                target == SettingsSection.PaletteTint || target == SettingsSection.Font,
                        )
                    },
                    onThemeChanged = onThemeChanged,
                )
                SettingsSection.Font -> FontSettingsRoute(dependencies.settings, onBack)
                SettingsSection.Theme -> AndroidThemeSettingsScreen(
                    showNavigation = true,
                    onBack = onBack,
                    onThemeChanged = onThemeChanged,
                )
                SettingsSection.PaletteTint -> AndroidPaletteTintSettingsScreen(
                    showNavigation = true,
                    onBack = onBack,
                )
                SettingsSection.Notifications -> AndroidNotificationsSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                )
                SettingsSection.Stories -> AndroidStoriesSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onManageFrontpages = { onNavigate(SettingsSection.Frontpages, true) },
                    onPaletteTintRequested = { onNavigate(SettingsSection.PaletteTint, true) },
                )
                SettingsSection.Frontpages -> ManageFrontpagesSettingsRoute(
                    repository = dependencies.settings,
                    onBack = onBack,
                    hasAccount = settingsAccountState.accountOrNull != null,
                )
                SettingsSection.Comments -> AndroidCommentsSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onThreadDepthRequested = { onNavigate(SettingsSection.ThreadDepth, true) },
                    onQuoteHighlightingRequested = { onNavigate(SettingsSection.QuoteHighlighting, true) },
                    onUserAvatarsRequested = { onNavigate(SettingsSection.UserAvatars, true) },
                )
                SettingsSection.QuoteHighlighting -> QuoteHighlightingSettingsRoute(dependencies.settings, onBack)
                SettingsSection.HeaderDisplay -> HeaderDisplaySettingsRoute(
                    dependencies.settings, onBack,
                    statusBarProtection = { color, height -> StatusBarProtection(color, height) },
                )
                SettingsSection.UserAvatars -> UserAvatarSettingsRoute(dependencies.settings, onBack)
                SettingsSection.ThreadDepth -> ThreadDepthIndicatorsSettingsRoute(
                    repository = dependencies.settings,
                    onBack = onBack,
                )
                SettingsSection.WebLinks -> WebLinksSettingsRoute(
                    repository = dependencies.settings,
                    showNavigation = singlePane,
                    onBack = onBack,
                    onReaderModeRequested = { onNavigate(SettingsSection.ReaderMode, true) },
                )
                SettingsSection.ReaderMode -> ReaderModeSettingsRoute(
                    dependencies.settings, onBack, preview = { reading, _ -> AndroidReaderModePreview(reading) },
                )
                SettingsSection.FiltersTags -> AndroidFiltersTagsSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onManageFrontpages = { onNavigate(SettingsSection.Frontpages, true) },
                )
                SettingsSection.AiSummary -> AndroidAiSummarySettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                )
                SettingsSection.Data -> AndroidDataSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onSettingsReset = onThemeChanged,
                )
                SettingsSection.Debug -> AndroidDebugSettingsScreen(
                    showNavigation = singlePane,
                    onBack = onBack,
                    onOpenGlassSettings = { onNavigate(SettingsSection.Glass, true) },
                    onOpenLinkPreviews = {
                        onNavigate(SettingsSection.DebugLinkPreviews, true)
                    },
                )
                SettingsSection.DebugLinkPreviews -> AndroidLinkPreviewsDebugScreen(onBack)
                SettingsSection.Glass -> GlassSettingsRoute(dependencies.settings, onBack)
                SettingsSection.About -> AndroidAboutScreen(
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
                SettingsSection.Licenses -> AndroidLicensesScreen(
                    onBack = onBack,
                    onOpenLicense = { dependencies.links.open(it) },
                    singlePane = singlePane,
                )
            }
        },
    )

    ChangelogDialogController.Content()
}
