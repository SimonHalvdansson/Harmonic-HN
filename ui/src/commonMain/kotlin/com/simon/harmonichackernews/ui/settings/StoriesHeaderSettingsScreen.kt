package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_horizontal_split
import com.simon.harmonichackernews.resources.ic_more_vert
import com.simon.harmonichackernews.resources.settings_section_stories_header
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AppearanceBooleanPreference
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.ui.stories.FrontpageHeaderControls
import com.simon.harmonichackernews.ui.stories.StoriesHeaderMenus
import com.simon.harmonichackernews.ui.stories.StoryTypeChips
import com.simon.harmonichackernews.ui.stories.frontpageHeaderBottomSpacing
import com.simon.harmonichackernews.ui.stories.frontpageHeaderTopSpacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun StoriesHeaderSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    StoriesHeaderSettingsScreen(
        settings = StoryDisplaySettings.from(settings.story),
        frontpages = StoryTypeMenuPolicy.frontpages(settings.story.additionalFrontpages, settings.story.frontpageOrder),
        onSelectorChanged = repository::setStoryListSelector,
        onCompactHeaderChanged = { repository.setAppearanceBoolean(AppearanceBooleanPreference.COMPACT_HEADER, it) },
        onBack = onBack,
    )
}

@Composable
internal fun StoriesHeaderSettingsScreen(
    settings: StoryDisplaySettings,
    frontpages: List<StoryType>,
    onSelectorChanged: (StoryListSelector) -> Unit,
    onCompactHeaderChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_stories_header),
        showNavigation = true,
        onBack = onBack,
        contentVersion = settings.hashCode(),
        pinnedContent = { StoriesHeaderPreview(settings, frontpages) },
    ) {
        item {
            SettingsCategory("Layout") {
                SegmentedSetting(
                    title = "Frontpage selector",
                    options = listOf(StoryListSelector.DROPDOWN to "Dropdown", StoryListSelector.CHIPS to "Chips"),
                    selected = settings.listSelector,
                    onSelected = onSelectorChanged,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Compact header",
                    summary = "Reduce spacing around the frontpage header",
                    icon = Res.drawable.ic_horizontal_split,
                    checked = settings.compactHeader,
                    onCheckedChange = onCompactHeaderChanged,
                )
            }
        }
    }
}

@Composable
private fun StoriesHeaderPreview(settings: StoryDisplaySettings, frontpages: List<StoryType>) {
    var selectedName by rememberSaveable { mutableStateOf(StoryType.TOP_STORIES.name) }
    val selectedIndex = frontpages.indexOfFirst { it.name == selectedName }.coerceAtLeast(0)
    val select: (Int) -> Unit = { frontpages.getOrNull(it)?.let { type -> selectedName = type.name } }
    Layout(
        modifier = Modifier.fillMaxWidth().testTag("stories-header-preview"),
        content = {
            // Measure the largest layout at this width and font scale, without placing it.
            // Its independent menu state cannot dismiss the live preview's dropdown.
            Box {
                StoriesHeaderPreviewContent(
                    settings.copy(listSelector = StoryListSelector.CHIPS, compactHeader = false),
                    frontpages, selectedIndex, {}, animateChanges = false,
                )
            }
            Box(Modifier.testTag("stories-header-preview-content")) {
                StoriesHeaderPreviewContent(settings, frontpages, selectedIndex, select)
            }
        },
    ) { measurables, constraints ->
        val sampleConstraints = constraints.copy(minHeight = 0)
        val largest = measurables[0].measure(sampleConstraints)
        val current = measurables[1].measure(sampleConstraints)
        val width = constraints.constrainWidth(largest.width)
        val height = constraints.constrainHeight(largest.height)
        layout(width, height) {
            current.placeRelative((width - current.width) / 2, (height - current.height) / 2)
        }
    }
}

@Composable
private fun StoriesHeaderPreviewContent(
    settings: StoryDisplaySettings,
    frontpages: List<StoryType>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    animateChanges: Boolean = true,
) {
    val labels = frontpages.map { it.label }
    val menus = remember { StoriesHeaderMenus() }
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val topSpacing by animateDpAsState(
        frontpageHeaderTopSpacing(settings.compactHeader),
        tween(320, easing = FastOutSlowInEasing), label = "Preview header top spacing",
    )
    val bottomSpacing by animateDpAsState(
        frontpageHeaderBottomSpacing(settings.compactHeader),
        tween(320, easing = FastOutSlowInEasing), label = "Preview header bottom spacing",
    )
    Column(
        Modifier.fillMaxWidth().padding(
            top = if (animateChanges) topSpacing else frontpageHeaderTopSpacing(settings.compactHeader),
        ),
    ) {
        FrontpageHeaderControls(
            settings = settings,
            title = labels.getOrElse(selectedIndex) { "Top Stories" },
            typeLabels = labels,
            selectedTypeIndex = selectedIndex,
            menus = menus,
            onTypeSelected = onSelected,
            modifier = Modifier.padding(horizontal = 16.dp),
            moreMenu = { _, _ ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_more_vert), null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        AnimatedVisibility(
            visible = settings.listSelector == StoryListSelector.CHIPS,
            enter = expandVertically(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(320, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
        ) {
            StoryTypeChips(
                labels = labels,
                selectedIndex = selectedIndex,
                fontFamily = typography.family,
                contentPadding = PaddingValues(horizontal = 16.dp),
                onSelected = onSelected,
                modifier = Modifier.padding(top = 8.dp).testTag("stories-header-preview-chips"),
            )
        }
        StoryRow(
            model = SettingsStoryPreviewModel.copy(index = "1.", title = "A faster way to solve equations"),
            style = settings.copy(previewImageMode = StoryPreviewMode.OFF, showPreviewText = false)
                .toStoryRowStyle(StoryRowStyleContext(score = 53, commentCount = 18, isRead = false)),
            modifier = Modifier.padding(
                top = if (animateChanges) bottomSpacing else frontpageHeaderBottomSpacing(settings.compactHeader),
            ).testTag("stories-header-preview-story"),
            listItem = true,
            animateChanges = false,
        )
    }
}
