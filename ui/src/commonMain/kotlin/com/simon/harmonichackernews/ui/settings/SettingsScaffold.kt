package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private enum class SettingsListGroup { Reading, Tools, App }

private data class SettingsListEntry(
    val group: SettingsListGroup,
    val section: SettingsSection,
    val icon: DrawableResource,
    val summary: StringResource,
)

private val MainSettingsEntries = listOf(
    SettingsListEntry(SettingsListGroup.Reading, SettingsSection.Appearance, Res.drawable.ic_style, Res.string.settings_summary_appearance),
    SettingsListEntry(SettingsListGroup.Reading, SettingsSection.Stories, Res.drawable.ic_newspaper, Res.string.settings_summary_stories),
    SettingsListEntry(SettingsListGroup.Reading, SettingsSection.Comments, Res.drawable.ic_comment, Res.string.settings_summary_comments),
    SettingsListEntry(SettingsListGroup.Reading, SettingsSection.WebLinks, Res.drawable.ic_web_asset, Res.string.settings_summary_web_links),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.FiltersTags, Res.drawable.ic_filter_list, Res.string.settings_summary_filters_tags),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.AiSummary, Res.drawable.ic_auto_awesome, Res.string.settings_summary_ai_summary),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.Notifications, Res.drawable.ic_notifications, Res.string.settings_summary_notifications),
    SettingsListEntry(SettingsListGroup.App, SettingsSection.Data, Res.drawable.ic_data_table, Res.string.settings_summary_data),
    SettingsListEntry(SettingsListGroup.App, SettingsSection.About, Res.drawable.ic_info, Res.string.settings_summary_about),
    SettingsListEntry(SettingsListGroup.App, SettingsSection.Debug, Res.drawable.ic_api, Res.string.settings_summary_debug),
)

@Composable
internal fun itemBackgroundColor(): Color = MaterialTheme.colorScheme.cardBackground

@Composable
private fun SettingsTopAppBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    val platformStyle = LocalSettingsPlatformStyle.current
    HarmonicTopAppBar(
        title = title,
        onBack = onBack,
        toolbarHeight = platformStyle.topBarHeight,
        navigationHeight = platformStyle.topBarNavigationHeight,
        navigationInset = platformStyle.topBarNavigationInset,
        platformTextStyle = platformStyle.textStyle,
    )
}

@Composable
fun SettingsListScreen(
    selectedSection: SettingsSection,
    showSelection: Boolean,
    showDebugSettings: Boolean,
    loggedIn: Boolean,
    onBack: () -> Unit,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val versionName = LocalHarmonicUiDependencies.current.metadata.versionName
    val settingsCardShape = RoundedCornerShape(
        HarmonicDimens.settings_list_segment_corner_radius,
    )
    val visibleGroups = MainSettingsEntries.filter {
        (it.section != SettingsSection.Debug || showDebugSettings) &&
            (it.section != SettingsSection.Notifications || loggedIn)
    }.groupBy { it.group }
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.pageBackground)
            .recalculateWindowInsets()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        SettingsTopAppBar(
            title = stringResource(Res.string.settings_title),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                start = HarmonicDimens.settings_list_segment_horizontal_margin,
                top = HarmonicDimens.settings_list_first_segment_top_margin,
                end = HarmonicDimens.settings_list_segment_horizontal_margin,
                bottom = HarmonicDimens.settings_list_segment_bottom_margin +
                    navigationBarPadding,
            ),
        ) {
            visibleGroups.forEach { (group, visibleEntries) ->
                item(key = group.name) {
                    val colors = MaterialTheme.colorScheme
                    val (iconContainerColor, iconContentColor) = when (group) {
                        SettingsListGroup.Reading -> colors.primaryContainer to colors.onPrimaryContainer
                        SettingsListGroup.Tools -> colors.tertiaryContainer to colors.onTertiaryContainer
                        SettingsListGroup.App -> colors.secondaryContainer to colors.onSecondaryContainer
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(settingsCardShape),
                    ) {
                        visibleEntries.forEachIndexed { index, entry ->
                            val isSelected = selectedSection == entry.section ||
                                entry.section == SettingsSection.Appearance &&
                                (selectedSection == SettingsSection.Theme ||
                                    selectedSection == SettingsSection.PaletteTint) ||
                                entry.section == SettingsSection.Debug &&
                                (selectedSection == SettingsSection.DebugLinkPreviews ||
                                    selectedSection == SettingsSection.Glass) ||
                                entry.section == SettingsSection.Comments &&
                                (selectedSection == SettingsSection.ThreadDepth ||
                                    selectedSection == SettingsSection.UserAvatars) ||
                                entry.section == SettingsSection.WebLinks &&
                                selectedSection == SettingsSection.ReaderMode ||
                                entry.section == SettingsSection.Stories &&
                                selectedSection == SettingsSection.Frontpages ||
                                entry.section == SettingsSection.About &&
                                selectedSection == SettingsSection.Licenses
                            SettingsNavigationRow(
                                title = stringResource(entry.section.titleResource),
                                summary = if (entry.section == SettingsSection.About) {
                                    stringResource(entry.summary, versionName)
                                } else {
                                    stringResource(entry.summary)
                                },
                                icon = entry.icon,
                                iconContainerColor = iconContainerColor,
                                iconContentColor = iconContentColor,
                                selected = showSelection && isSelected,
                                onClick = { onSectionSelected(entry.section) },
                            )
                            if (index != visibleEntries.lastIndex) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    summary: String,
    icon: DrawableResource,
    iconContainerColor: Color,
    iconContentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = 72.dp,
            )
            .background(
                if (selected) {
                    colors.secondaryContainer.copy(alpha = 0.45f).compositeOver(itemBackgroundColor())
                } else {
                    itemBackgroundColor()
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconContentColor,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 1.dp),
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
fun SettingsPage(
    title: String,
    showNavigation: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentVersion: Int = 0,
    pinnedContent: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    extraBottomPadding: Dp = 0.dp,
    headerContent: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    // A preview that scrolled out of composition must be measured again after width/font changes.
    // Temporarily declaring it sticky brings it back without changing its key or list position.
    var previewSize by remember(viewportSize.width, density.density, density.fontScale, contentVersion) {
        mutableStateOf<IntSize?>(null)
    }
    val contentHeight = viewportSize.height - with(density) { navigationBarPadding.roundToPx() }
    val pinPreview = previewSize?.let { it.height <= contentHeight * 0.7f } ?: true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.pageBackground)
            .recalculateWindowInsets()
            // Adaptive lookahead can place this pane beyond the window and produce negative
            // consumed insets. Union with zero prevents phantom safe-area padding, which would
            // give content animations a narrower target width and cause a final-frame jump.
            .consumeWindowInsets(WindowInsets(0, 0, 0, 0))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        SettingsTopAppBar(
            title = title,
            onBack = onBack.takeIf { showNavigation },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().onSizeChanged { viewportSize = it },
            state = listState,
            contentPadding = PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 24.dp + navigationBarPadding + extraBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Preference-backed rows are declared in the lazy content lambda. Capturing the
            // version here makes LazyColumn rebuild those declarations after a preference edit.
            @Suppress("UNUSED_EXPRESSION")
            contentVersion
            headerContent?.let { header ->
                item(key = "settings-header") { header() }
            }
            pinnedContent?.let { preview ->
                val previewContent: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { previewSize = it.size }
                            .background(MaterialTheme.colorScheme.pageBackground),
                    ) {
                        preview()
                    }
                }
                if (pinPreview) {
                    stickyHeader(key = "settings-preview") { previewContent() }
                } else {
                    item(key = "settings-preview") { previewContent() }
                }
            }
            content()
        }
    }
}

@Composable
fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Crossfade(
            targetState = title,
            animationSpec = tween(180),
            label = "settings category title",
            modifier = Modifier
                .semantics { heading() }
                .padding(
                    start = HarmonicDimens.compose_settings_category_padding_start,
                    top = HarmonicDimens.compose_settings_category_padding_top,
                    end = HarmonicDimens.settings_list_segment_horizontal_margin,
                    bottom = HarmonicDimens.compose_settings_category_padding_bottom,
                ),
        ) { displayedTitle ->
            Text(
                text = displayedTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(
            modifier = Modifier.height(
                HarmonicDimens.compose_settings_category_segment_gap,
            ),
        )
        SettingsCard(content = content)
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = HarmonicDimens.settings_list_segment_horizontal_margin,
            )
            .clip(
                RoundedCornerShape(
                    HarmonicDimens.settings_list_segment_corner_radius,
                ),
            ),
        content = { content() },
    )
}
