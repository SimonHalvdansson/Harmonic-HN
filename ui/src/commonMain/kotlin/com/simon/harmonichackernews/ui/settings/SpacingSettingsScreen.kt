package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategorySettings
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_horizontal_rule
import com.simon.harmonichackernews.resources.palette1
import com.simon.harmonichackernews.resources.palette5
import com.simon.harmonichackernews.resources.settings_section_spacing
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.settings.RowSpacingPreferences
import com.simon.harmonichackernews.settings.StoryBooleanPreference
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import kotlin.math.roundToInt
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

@Composable
fun StoriesSpacingSettingsRoute(
    repository: AppSettingsRepository,
    categorySettings: ArticleCategorySettings?,
    onBack: () -> Unit,
) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val categories = categorySettings?.updates?.collectAsStateWithLifecycle(initialValue = categorySettings.snapshot())?.value
    val previewCategories = if (categories?.enabled == true) {
        categories.categories.filter { it.id in categories.selected }.take(2)
    } else emptyList()
    val stories = remember {
        listOf(
            SettingsStoryPreviewModel.copy(index = "1."),
            SettingsStoryPreviewModel.copy(
                index = "2.", title = "Show HN: A small tool for exploring the night sky",
                previewText = "An interactive map of the stars visible from your neighborhood.",
                points = 128, commentCount = 42, age = "3h",
                previewImageFallback = Res.drawable.palette5,
            ),
            SettingsStoryPreviewModel.copy(
                index = "3.", title = "The quiet craft of building useful software",
                previewText = "Notes on making everyday tools simpler and more reliable.",
                points = 76, commentCount = 24, age = "4h",
                previewImageFallback = Res.drawable.palette1,
            ),
        )
    }
    SpacingSettingsPage(
        spacing = settings.story.spacing,
        dividers = settings.story.showDividers,
        contentVersion = settings.hashCode(),
        onBack = onBack,
        onSpacingChanged = repository::setStorySpacing,
        onDividersChanged = { repository.setStoryBoolean(StoryBooleanPreference.SHOW_DIVIDERS, it) },
        preview = {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("stories-spacing-preview")) {
                stories.forEachIndexed { index, story ->
                    StoryRow(
                        model = story.copy(
                            categoryLabels = previewCategories.map { categories!!.name(it) },
                            categoryIds = previewCategories.map { it.id },
                        ),
                        style = StoryDisplaySettings.from(settings.story).toStoryRowStyle(
                            StoryRowStyleContext(score = story.points, commentCount = story.commentCount, isRead = false),
                        ).copy(showDivider = settings.story.showDividers && index < stories.lastIndex),
                        listItem = true,
                        animateChanges = true,
                    )
                }
            }
        },
    )
}

@Composable
fun CommentsSpacingSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val state = presenter.state(settings)
    val comments = remember {
        val createdAt = Clock.System.now().epochSeconds.toInt() - 3600
        listOf(
            "willow" to "Small details can make a conversation much easier to follow.",
            "lantern" to "A little breathing room makes a difference.",
            "pebble" to "I like being able to choose what feels comfortable to read.",
        ).mapIndexed { index, (author, body) ->
            PortableCommentItem(
                comment = CommentSnapshot(
                    id = -4080 - index, author = author, text = body, expandedAnchorText = body,
                    createdAtEpochSeconds = createdAt + index * 60,
                ),
                presentation = CommentPresentationSnapshot(expanded = true, depth = index),
            )
        }
    }
    SpacingSettingsPage(
        spacing = state.spacing,
        dividers = state.showDividers,
        contentVersion = settings.hashCode(),
        onBack = onBack,
        onSpacingChanged = repository::setCommentSpacing,
        onDividersChanged = { repository.setCommentBoolean(CommentBooleanPreference.SHOW_DIVIDERS, it) },
        preview = {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("comments-spacing-preview")) {
                comments.forEachIndexed { index, comment ->
                    CommentRow(
                        comment = comment,
                        // Preview controls always animate; the expand/collapse preference is independent.
                        style = state.toPreviewCommentRowStyle().copy(
                            animateChanges = true,
                            showDivider = state.showDividers && index < comments.lastIndex,
                        ),
                        storyAuthor = null, accountUser = null, userTag = null, subtreeReplyCount = 0,
                        collapseParent = state.collapseParent,
                        showTopLevelIndicator = state.topLevelIndicators,
                        nextCommentDepth = comments.getOrNull(index + 1)?.depth,
                        enableLongClick = false,
                        onToggleExpanded = {}, onShowActions = {},
                        onLinkLongClick = { _, _, _ -> }, onReferenceLongClick = { _, _, _ -> },
                    )
                }
            }
        },
    )
}

@Composable
private fun SpacingSettingsPage(
    spacing: Int,
    dividers: Boolean,
    contentVersion: Int,
    onBack: () -> Unit,
    onSpacingChanged: (Int) -> Unit,
    onDividersChanged: (Boolean) -> Unit,
    preview: @Composable () -> Unit,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_spacing),
        showNavigation = true,
        onBack = onBack,
        contentVersion = contentVersion,
        pinnedContent = preview,
    ) {
        settingsCategory("Layout") {
            row {
                SliderSetting(
                    title = "Vertical spacing",
                    valueLabel = "$spacing%",
                    value = spacing.toFloat(),
                    valueRange = RowSpacingPreferences.MIN.toFloat()..RowSpacingPreferences.MAX.toFloat(),
                    steps = (RowSpacingPreferences.MAX - RowSpacingPreferences.MIN) / RowSpacingPreferences.STEP - 1,
                    onValueChange = { onSpacingChanged(it.roundToInt()) },
                    onReset = { onSpacingChanged(RowSpacingPreferences.DEFAULT) },
                    resetEnabled = spacing != RowSpacingPreferences.DEFAULT,
                )
            }
            row {
                SwitchSettingRow(
                    title = "Dividers",
                    icon = Res.drawable.ic_horizontal_rule,
                    checked = dividers,
                    onCheckedChange = onDividersChanged,
                )
            }
        }
    }
}
