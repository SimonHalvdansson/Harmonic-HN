@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_check
import com.simon.harmonichackernews.resources.widget_config_ask_hn
import com.simon.harmonichackernews.resources.widget_config_best_stories
import com.simon.harmonichackernews.resources.widget_config_confirm
import com.simon.harmonichackernews.resources.widget_config_jobs
import com.simon.harmonichackernews.resources.widget_config_new_stories
import com.simon.harmonichackernews.resources.widget_config_show_hn
import com.simon.harmonichackernews.resources.widget_config_story_count_label
import com.simon.harmonichackernews.resources.widget_config_title
import com.simon.harmonichackernews.resources.widget_config_top_stories
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class WidgetFeedOption(
    val label: StringResource,
    val storyType: StoryType,
    val feedName: String,
)

private val WidgetFeeds = listOf(
    WidgetFeedOption(Res.string.widget_config_top_stories, StoryType.TOP_STORIES, "Top stories"),
    WidgetFeedOption(Res.string.widget_config_new_stories, StoryType.NEW_STORIES, "New stories"),
    WidgetFeedOption(Res.string.widget_config_best_stories, StoryType.BEST_STORIES, "Best stories"),
    WidgetFeedOption(Res.string.widget_config_ask_hn, StoryType.ASK_HN, "Ask HN"),
    WidgetFeedOption(Res.string.widget_config_show_hn, StoryType.SHOW_HN, "Show HN"),
    WidgetFeedOption(Res.string.widget_config_jobs, StoryType.HN_JOBS, "Jobs"),
)

/** Portable widget configuration UI reusable by Android Glance/RemoteViews and WidgetKit hosts. */
@Composable
fun WidgetConfigScreen(
    initialConfiguration: WidgetConfiguration,
    onConfirm: (WidgetConfiguration) -> Unit,
) {
    var selectedFeedIndex by rememberSaveable(initialConfiguration.storyType) {
        mutableIntStateOf(
            WidgetFeeds.indexOfFirst { it.storyType == initialConfiguration.storyType }
                .coerceAtLeast(0),
        )
    }
    var selectedStoryCount by rememberSaveable(initialConfiguration.visibleStoryCount) {
        mutableIntStateOf(initialConfiguration.visibleStoryCount)
    }

    Column(
        Modifier.fillMaxSize()
            .background(HarmonicTheme.colors.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(Res.string.widget_config_title),
            modifier = Modifier.padding(start = 32.dp, top = 40.dp, end = 32.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            letterSpacing = 0.125.sp,
        )
        Column(Modifier.fillMaxWidth().padding(top = 16.dp).selectableGroup()) {
            WidgetFeeds.forEachIndexed { index, option ->
                WidgetFeedRow(
                    text = stringResource(option.label),
                    selected = selectedFeedIndex == index,
                    onClick = { selectedFeedIndex = index },
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            Text(
                stringResource(Res.string.widget_config_story_count_label),
                modifier = Modifier.padding(top = 24.dp),
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                letterSpacing = 0.2.sp,
            )
            WidgetStoryCountSelector(
                selected = selectedStoryCount,
                onSelected = { selectedStoryCount = it },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(start = 32.dp, top = 24.dp, end = 32.dp, bottom = 16.dp)
                .height(56.dp),
        ) {
            val confirmShape = RoundedCornerShape(28.dp)
            Button(
                onClick = {
                    val feed = WidgetFeeds[selectedFeedIndex]
                    onConfirm(
                        WidgetConfiguration(
                            storyType = feed.storyType,
                            feedName = feed.feedName,
                            visibleStoryCount = selectedStoryCount,
                        ),
                    )
                },
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                shapes = ButtonDefaults.shapes(shape = confirmShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.widget_config_confirm),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun WidgetFeedRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            RadioButton(selected = selected, onClick = null)
        }
        Text(
            text,
            modifier = Modifier.padding(start = 8.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            letterSpacing = 0.25.sp,
        )
    }
}

@Composable
private fun WidgetStoryCountSelector(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = WidgetConfiguration.allowedStoryCounts.sorted()
    val colors = HarmonicFilterButtonColors(
        checkedBackground = HarmonicTheme.colors.onSurface.copy(alpha = 0.9f),
        checkedText = HarmonicTheme.colors.background,
        checkedStroke = Color.Transparent,
        uncheckedText = HarmonicTheme.colors.textPrimary,
        uncheckedStroke = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
        modifier.fillMaxWidth().height(48.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            HarmonicFilterButton(
                label = option.toString(),
                selected = option == selected,
                position = index,
                lastPosition = options.lastIndex,
                colors = colors,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
