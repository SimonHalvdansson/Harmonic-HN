package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategoryInputSource
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryResult
import com.simon.harmonichackernews.categorization.ArticleCategoryScore
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryCategoryInspection
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.toStoryRowStyle

internal fun sampleCategoryInspection(source: ArticleCategoryInputSource = ArticleCategoryInputSource.PREVIEW_TEXT) =
    StoryCategoryInspection(
        storyId = 0,
        title = "A small language model that runs on your laptop",
        url = null,
        result = ArticleCategoryResult(listOf(
            ArticleCategoryScore("ai", .42f), ArticleCategoryScore("hardware", .34f),
            ArticleCategoryScore("software", .30f), ArticleCategoryScore("science", .19f),
        ), source),
        preferences = ArticleCategoryPreferences(enabled = true),
        sample = true,
    )

/** Repeatable interaction fixture. Its scores are deliberately separate from live inference. */
@Composable
internal fun CategoryInspectorPreviewDialog(onDismiss: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    var storyOpens by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf(ArticleCategoryInputSource.PREVIEW_TEXT) }
    val preferences by app.articleCategorySettings.updates.collectAsStateWithLifecycle(
        initialValue = app.articleCategorySettings.snapshot())
    val inspection = remember(source, preferences.names, preferences.categories) {
        val sample = sampleCategoryInspection(source)
        sample.copy(preferences = sample.preferences.copy(names = preferences.names, categories = preferences.categories))
    }
    val model = remember(inspection) {
        SettingsStoryPreviewModel.copy(title = inspection.title, previewText = "", faviconUrl = null,
            previewImageUrl = null, categoryLabels = inspection.labels, categoryInspection = inspection)
    }
    val settings = StoryDisplaySettings.from(app.userSettings.story).copy(
        previewImageMode = StoryPreviewMode.OFF, showPreviewText = false)
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Category inspector preview") },
        scrollableContent = true,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("category-inspector-preview-scroll"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsDialogBodyText("Tap a category to inspect its sample scores. Category taps should keep the story counter at zero.")
                SegmentedSetting(
                    options = listOf(ArticleCategoryInputSource.TITLE_ONLY to "Title only",
                        ArticleCategoryInputSource.PREVIEW_TEXT to "Title + preview"),
                    selected = source, onSelected = { source = it },
                )
                StoryRow(model, settings.toStoryRowStyle(StoryRowStyleContext(model.points, model.commentCount, false)),
                    animateChanges = false, onLinkClick = { storyOpens += 1 },
                    modifier = Modifier.testTag("category-inspector-sample-story"))
                Text("Story opens: $storyOpens", Modifier.testTag("category-inspector-story-opens"))
                SettingsDialogBodyText("Sample scores are for this preview only. Edit examples opens the real category editor.")
            }
        },
        confirmButton = { SettingsDialogTextButton(onClick = onDismiss) { Text("Close") } },
    )
}
