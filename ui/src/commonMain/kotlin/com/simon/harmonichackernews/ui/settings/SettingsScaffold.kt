package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.ui.theme.themeBackground
import androidx.compose.ui.draw.drawBehind

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

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
    SettingsListEntry(SettingsListGroup.Reading, SettingsSection.HeaderDisplay, Res.drawable.ic_web, Res.string.settings_summary_header_display),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.WebLinks, Res.drawable.ic_web_asset, Res.string.settings_summary_web_links),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.FiltersTags, Res.drawable.ic_filter_list, Res.string.settings_summary_filters_tags),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.AiSummary, Res.drawable.ic_auto_awesome, Res.string.settings_summary_ai_summary),
    SettingsListEntry(SettingsListGroup.Tools, SettingsSection.Notifications, Res.drawable.ic_notifications, Res.string.settings_summary_notifications),
    SettingsListEntry(SettingsListGroup.App, SettingsSection.Data, Res.drawable.ic_data_table, Res.string.settings_summary_data),
    SettingsListEntry(SettingsListGroup.App, SettingsSection.About, Res.drawable.ic_info, Res.string.settings_summary_about),
    SettingsListEntry(SettingsListGroup.App, SettingsSection.Debug, Res.drawable.ic_api, Res.string.settings_summary_debug),
)

/** Hosts keep scroll owners outside Navigation 3's pane-count recreation boundary. */
val LocalSettingsListState = staticCompositionLocalOf<LazyListState?> { null }

@Composable
internal fun rememberSettingsListState(): LazyListState =
    LocalSettingsListState.current ?: rememberLazyListState()

@Composable
internal fun itemBackgroundColor(): Color = HarmonicTheme.colors.cardBackground

@Composable
private fun SettingsTopAppBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    val platformStyle = LocalSettingsPlatformStyle.current
    HarmonicTopAppBar(
        title = title,
        onBack = onBack.takeUnless { platformStyle.navigationInSideRail },
        toolbarHeight = platformStyle.topBarHeight,
        navigationHeight = platformStyle.topBarNavigationHeight,
        navigationInset = platformStyle.topBarNavigationInset,
        platformTextStyle = platformStyle.textStyle,
        titleStartPadding = if (platformStyle.navigationInSideRail) {
            HarmonicDimens.settings_list_segment_horizontal_margin
        } else {
            16.dp
        },
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
    showNotifications: Boolean = true,
) {
    val versionName = LocalHarmonicUiDependencies.current.metadata.versionName
    val settingsCardShape = RoundedCornerShape(
        HarmonicDimens.settings_list_segment_corner_radius,
    )
    val visibleGroups = remember(showDebugSettings, loggedIn, showNotifications) {
        MainSettingsEntries.filter {
            (it.section != SettingsSection.Debug || showDebugSettings) &&
                (it.section != SettingsSection.Notifications || (loggedIn && showNotifications))
        }.groupBy { it.group }
    }
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .themeBackground { pageBackground }
            .consumeSettingsPaneBounds()
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
            state = rememberSettingsListState(),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(settingsCardShape),
                    ) {
                        visibleEntries.forEachIndexed { index, entry ->
                            val isSelected = selectedSection == entry.section ||
                                entry.section == SettingsSection.Appearance &&
                                (selectedSection == SettingsSection.Theme ||
                                    selectedSection == SettingsSection.Font ||
                                    selectedSection == SettingsSection.PaletteTint) ||
                                entry.section == SettingsSection.Debug &&
                                (selectedSection == SettingsSection.DebugLinkPreviews ||
                                    selectedSection == SettingsSection.Glass) ||
                                entry.section == SettingsSection.Comments &&
                                (selectedSection == SettingsSection.ThreadDepth ||
                                    selectedSection == SettingsSection.QuoteHighlighting ||
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
                                group = group,
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
    group: SettingsListGroup,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = HarmonicTheme.colors
    val useDarkSelection = selected && HarmonicTheme.isDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = 72.dp,
            )
            .drawBehind {
                drawRect(when {
                    useDarkSelection -> colors.secondaryContainer.copy(alpha = 0.45f)
                        .compositeOver(colors.cardBackground)
                    selected -> colors.surfaceContainerHigh
                    else -> colors.cardBackground
                })
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsNavigationIcon(icon, group)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = { if (useDarkSelection) colors.onSecondaryContainer else colors.onSurface },
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 1.dp),
                color = { if (useDarkSelection) colors.onSecondaryContainer else colors.onSurfaceVariant },
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun SettingsNavigationIcon(icon: DrawableResource, group: SettingsListGroup) {
    val colors = HarmonicTheme.colors
    val (iconContainerColor, iconContentColor) = when (group) {
        SettingsListGroup.Reading -> colors.primaryContainer to colors.onPrimaryContainer
        SettingsListGroup.Tools -> colors.tertiaryContainer to colors.onTertiaryContainer
        SettingsListGroup.App -> colors.secondaryContainer to colors.onSecondaryContainer
    }
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
}

/** Consume the actual pane margins without writing inset state during lookahead placement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modifier.consumeSettingsPaneBounds(): Modifier {
    val consumed = remember { MutableWindowInsets() }
    return onGloballyPositioned { coordinates ->
        // recalculateWindowInsets shares mutable state between lookahead and actual placement.
        // Their different pane bounds can invalidate each other indefinitely in a Nav3 scaffold.
        // Global positioning observes only the placed bounds, which settle after the transition.
        val topLeft = coordinates.positionInRoot()
        val size = coordinates.size
        val bottomRight = coordinates.localToRoot(Offset(size.width.toFloat(), size.height.toFloat()))
        val rootSize = coordinates.findRootCoordinates().size
        consumed.insets = WindowInsets(
            left = topLeft.x.roundToInt().coerceAtLeast(0),
            top = topLeft.y.roundToInt().coerceAtLeast(0),
            right = (rootSize.width - bottomRight.x.roundToInt()).coerceAtLeast(0),
            bottom = (rootSize.height - bottomRight.y.roundToInt()).coerceAtLeast(0),
        )
    }.consumeWindowInsets(consumed)
}

@Composable
fun SettingsPage(
    title: String,
    showNavigation: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentVersion: Int = 0,
    showTopAppBar: Boolean = true,
    pinnedContent: (@Composable () -> Unit)? = null,
    pinnedContentBackground: Color? = null,
    floatingContent: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberSettingsListState(),
    extraBottomPadding: Dp = 0.dp,
    headerContent: (@Composable () -> Unit)? = null,
    topOverlay: (@Composable () -> Unit)? = null,
    horizontalInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    previewCanPin: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val navigationBarPx = with(density) { navigationBarPadding.roundToPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .themeBackground { pageBackground }
            .consumeSettingsPaneBounds()
            .windowInsetsPadding(horizontalInsets)
            .windowInsetsPadding(
                if (showTopAppBar) WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                else WindowInsets(0),
            ),
    ) {
        if (showTopAppBar) {
            SettingsTopAppBar(title = title, onBack = onBack.takeIf { showNavigation })
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                    // Always keep the same sticky item composed and measured. A tall preview
                    // follows its natural scroll position instead of sticking. Replacing an item
                    // with a stickyHeader (or briefly pinning it to remeasure after an edit)
                    // flashes the preview over the settings and resets animation state.
                    stickyHeader(key = "settings-preview") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val header = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == "settings-preview" }
                                    translationY = if (header == null) 0f else {
                                        (settingsPreviewOffset(listState, header, navigationBarPx, previewCanPin) -
                                            header.offset).toFloat()
                                    }
                                }
                                .themeBackground { pinnedContentBackground ?: pageBackground },
                        ) {
                            preview()
                        }
                    }
                }
                content()
            }
            topOverlay?.invoke()
            floatingContent?.let { controls ->
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp + navigationBarPadding),
                ) {
                    controls()
                }
            }
        }
    }
}

/** Natural position of a preview that LazyColumn retains as a sticky item for measurement. */
internal fun settingsPreviewOffset(
    state: LazyListState,
    header: LazyListItemInfo,
    bottomInset: Int,
    canPin: Boolean = true,
): Int = when {
    canPin && header.size <= (state.layoutInfo.viewportSize.height - bottomInset) * 0.7f -> header.offset
    state.firstVisibleItemIndex > header.index -> -header.size
    state.firstVisibleItemIndex == header.index -> -state.firstVisibleItemScrollOffset
    else -> header.offset
}

@Composable
fun SettingsCategory(
    title: String,
    titleTopPadding: Dp = HarmonicDimens.compose_settings_category_padding_top,
    content: @Composable () -> Unit,
) {
    Column {
        SettingsCategoryHeading(title, titleTopPadding)
        SettingsCard(content = content)
    }
}

/** Keep long groups lazy at row granularity, with the same continuous card and dividers. */
internal fun LazyListScope.settingsCategory(
    title: String,
    content: SettingsCategoryScope.() -> Unit,
) {
    val rows = SettingsCategoryScope().apply(content).rows
    item(key = "settings-category-$title", contentType = "settings-category-heading") {
        SettingsCategoryHeading(title, HarmonicDimens.compose_settings_category_padding_top)
    }
    rows.forEachIndexed { index, row ->
        item(key = "settings-category-$title-$index", contentType = "settings-category-row") {
            val radius = HarmonicDimens.settings_list_segment_corner_radius
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = HarmonicDimens.settings_list_segment_horizontal_margin)
                    .clip(RoundedCornerShape(
                        topStart = if (index == 0) radius else 0.dp,
                        topEnd = if (index == 0) radius else 0.dp,
                        bottomStart = if (index == rows.lastIndex) radius else 0.dp,
                        bottomEnd = if (index == rows.lastIndex) radius else 0.dp,
                    )),
            ) {
                if (index > 0) SettingsDivider()
                row()
            }
        }
    }
}

internal class SettingsCategoryScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows.add(content)
    }
}

@Composable
private fun SettingsCategoryHeading(title: String, titleTopPadding: Dp) {
    val colors = HarmonicTheme.colors
    Column {
        Crossfade(
            targetState = title,
            animationSpec = tween(180),
            label = "settings category title",
            modifier = Modifier
                .semantics { heading() }
                .padding(
                    start = HarmonicDimens.compose_settings_category_padding_start,
                    top = titleTopPadding,
                    end = HarmonicDimens.settings_list_segment_horizontal_margin,
                    bottom = HarmonicDimens.compose_settings_category_padding_bottom,
                ),
        ) { displayedTitle ->
            Text(
                text = displayedTitle,
                color = { lerp(
                    colors.onSurfaceVariant,
                    colors.primary,
                    0.45f,
                ) },
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
