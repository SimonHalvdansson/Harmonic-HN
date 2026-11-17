package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategorySettings
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_palette
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.StoryBooleanPreference
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.storyContentAlpha
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun ClickedPostsSettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
    categorySettings: ArticleCategorySettings? = null,
) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val story = settings.story
    val categories = categorySettings?.updates?.collectAsStateWithLifecycle(initialValue = categorySettings.snapshot())?.value
    val selectedCategories = if (categories?.enabled == true) {
        categories.categories.filter { it.id in categories.selected }.take(2)
    } else emptyList()
    val model = SettingsStoryPreviewModel.copy(
        categoryLabels = selectedCategories.map { categories!!.name(it) },
        categoryIds = selectedCategories.map { it.id },
    )
    val animationScope = rememberCoroutineScope()
    var resetAnimation by remember { mutableStateOf<Job?>(null) }
    var resetOpacity by remember { mutableStateOf<Float?>(null) }
    val displayedOpacity = resetOpacity ?: story.clickedOpacity.toFloat()

    fun resetOpacityMultiplier() {
        val start = displayedOpacity
        resetAnimation?.cancel()
        resetOpacity = start
        // Persist the final value immediately, even if the user leaves during the animation.
        repository.setClickedOpacity(0)
        resetAnimation = animationScope.launch {
            animate(start, 0f, animationSpec = tween(180)) { value, _ -> resetOpacity = value }
            resetOpacity = null
            resetAnimation = null
        }
    }

    val enabled = story.dimReadStories && !story.hideRead
    val style = StoryDisplaySettings.from(story).toStoryRowStyle(
        StoryRowStyleContext(model.points, model.commentCount, isRead = false),
    ).copy(clickedOpacity = displayedOpacity.roundToInt())
    val hasBackground = style.hasBackground || style.tintCard
    val baseAlpha = storyContentAlpha(true, HarmonicTheme.isDark, style.tintCard)
    val adjustedAlpha = storyContentAlpha(true, HarmonicTheme.isDark, style.tintCard, style.clickedOpacity)
    val multiplier = (adjustedAlpha / baseAlpha * 100).roundToInt()
    SettingsPage(
        title = "Gray out clicked posts", showNavigation = true, onBack = onBack,
        contentVersion = settings.hashCode(),
        headerContent = {
            SettingsMainToggle(
                title = "Gray out clicked posts", checked = story.dimReadStories,
                enabled = !story.hideRead,
                onCheckedChange = { repository.setStoryBoolean(StoryBooleanPreference.GRAY_OUT_CLICKED, it) },
            )
        },
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                for (clicked in listOf(false, true)) {
                    StoryRow(
                        model = model, style = style.copy(dimmed = clicked && enabled),
                        modifier = Modifier.testTag(
                            if (clicked) "clicked-post-preview" else "unclicked-post-preview",
                        ),
                        listItem = true, animateChanges = false,
                        cardPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
        }
        item {
            SettingsCategory("Appearance") {
                SliderSetting(
                    title = "Opacity multiplier", valueLabel = "$multiplier%",
                    value = displayedOpacity, valueRange = -100f..100f,
                    steps = 0, enabled = enabled,
                    onValueChange = {
                        resetAnimation?.cancel()
                        resetAnimation = null
                        resetOpacity = null
                        repository.setClickedOpacity(it.roundToInt())
                    },
                    onReset = ::resetOpacityMultiplier,
                    resetEnabled = story.clickedOpacity != 0,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Apply to background", icon = Res.drawable.ic_palette,
                    checked = story.dimClickedBackground, enabled = enabled && hasBackground,
                    onCheckedChange = repository::setDimClickedBackground,
                )
            }
        }
        if (story.hideRead) item {
            Text(
                "Turn off Hide clicked posts to use this setting.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                fontFamily = AppUiFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
