package com.simon.harmonichackernews.ui.settings

import org.jetbrains.compose.resources.DrawableResource
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private data class SettingsListEntry(
    val section: SettingsSection,
    val icon: DrawableResource,
)

private val MainSettingsEntries = listOf(
    SettingsListEntry(SettingsSection.Appearance, Res.drawable.ic_style),
    SettingsListEntry(SettingsSection.Stories, Res.drawable.ic_newspaper),
    SettingsListEntry(SettingsSection.Comments, Res.drawable.ic_comment),
    SettingsListEntry(SettingsSection.WebLinks, Res.drawable.ic_web_asset),
    SettingsListEntry(SettingsSection.FiltersTags, Res.drawable.ic_filter_list),
    SettingsListEntry(SettingsSection.AiSummary, Res.drawable.ic_auto_awesome),
    SettingsListEntry(SettingsSection.Notifications, Res.drawable.ic_notifications),
    SettingsListEntry(SettingsSection.Data, Res.drawable.ic_data_table),
    SettingsListEntry(SettingsSection.Debug, Res.drawable.ic_api),
    SettingsListEntry(SettingsSection.About, Res.drawable.ic_info),
)

@Composable
internal fun itemBackgroundColor(): Color = HarmonicTheme.colors.itemBackground

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
    val settingsCardShape = RoundedCornerShape(
        HarmonicDimens.settings_list_segment_corner_radius,
    )
    val visibleEntries = MainSettingsEntries.filter {
        (it.section != SettingsSection.Debug || showDebugSettings) &&
            (it.section != SettingsSection.Notifications || loggedIn)
    }
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background)
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
            contentPadding = PaddingValues(
                start = HarmonicDimens.settings_list_segment_horizontal_margin,
                top = HarmonicDimens.settings_list_first_segment_top_margin,
                end = HarmonicDimens.settings_list_segment_horizontal_margin,
                bottom = HarmonicDimens.settings_list_segment_bottom_margin +
                    navigationBarPadding,
            ),
        ) {
            item {
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
                            entry.section == SettingsSection.Stories &&
                            selectedSection == SettingsSection.Frontpages ||
                            entry.section == SettingsSection.About &&
                            selectedSection == SettingsSection.Licenses
                        SettingsNavigationRow(
                            title = stringResource(entry.section.titleResource),
                            icon = entry.icon,
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

@Composable
private fun SettingsNavigationRow(
    title: String,
    icon: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = HarmonicDimens.compose_settings_row_min_height,
            )
            .background(
                if (selected) {
                    HarmonicTheme.colors.settingsHeaderSelected
                } else {
                    itemBackgroundColor()
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(
                HarmonicDimens.compose_settings_row_icon_size,
            ),
            tint = HarmonicTheme.colors.iconTint,
        )
        Spacer(
            modifier = Modifier.width(
                HarmonicDimens.compose_settings_row_icon_end_space,
            ),
        )
        Text(
            text = title,
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
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
            .background(HarmonicTheme.colors.background)
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
                            .background(HarmonicTheme.colors.background),
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
        Text(
            text = title,
            modifier = Modifier
                .semantics { heading() }
                .padding(
                    start = HarmonicDimens.compose_settings_category_padding_start,
                    top = HarmonicDimens.compose_settings_category_padding_top,
                    end = HarmonicDimens.settings_list_segment_horizontal_margin,
                    bottom = HarmonicDimens.compose_settings_category_padding_bottom,
                ),
            color = HarmonicTheme.colors.textSecondary,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
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
