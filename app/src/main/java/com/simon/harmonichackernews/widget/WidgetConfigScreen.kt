@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.widget

import androidx.annotation.DimenRes
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.Utils

private data class WidgetFeedOption(
    val label: String,
    val feedUrl: String,
    val feedName: String,
)

/**
 * Compose host for [WidgetConfigActivity]. The activity continues to own result delivery and
 * preference persistence for the Compose-only configuration host.
 */
object WidgetConfigComposeHost {
    fun interface Listener {
        fun onConfirm(feedUrl: String, feedName: String, storyCount: Int)
    }

    @JvmStatic
    fun install(
        activity: ComponentActivity,
        initialStoryCount: Int,
        listener: Listener,
    ) {
        val composeView = ComposeView(activity).apply {
            id = R.id.widget_config_compose
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                HarmonicTheme {
                    WidgetConfigScreen(
                        initialStoryCount = initialStoryCount,
                        onConfirm = listener::onConfirm,
                    )
                }
            }
        }
        activity.setContentView(composeView)
    }
}

/**
 * The content and button containers use two columns that consume the same responsive side margin.
 */
@Composable
private fun WidgetConfigScreen(
    initialStoryCount: Int,
    onConfirm: (feedUrl: String, feedName: String, storyCount: Int) -> Unit,
) {
    val feedOptions = listOf(
        WidgetFeedOption(
            label = stringResource(R.string.widget_config_top_stories),
            feedUrl = Utils.URL_TOP,
            feedName = "Top stories",
        ),
        WidgetFeedOption(
            label = stringResource(R.string.widget_config_new_stories),
            feedUrl = Utils.URL_NEW,
            feedName = "New stories",
        ),
        WidgetFeedOption(
            label = stringResource(R.string.widget_config_best_stories),
            feedUrl = Utils.URL_BEST,
            feedName = "Best stories",
        ),
        WidgetFeedOption(
            label = stringResource(R.string.widget_config_ask_hn),
            feedUrl = Utils.URL_ASK,
            feedName = "Ask HN",
        ),
        WidgetFeedOption(
            label = stringResource(R.string.widget_config_show_hn),
            feedUrl = Utils.URL_SHOW,
            feedName = "Show HN",
        ),
        WidgetFeedOption(
            label = stringResource(R.string.widget_config_jobs),
            feedUrl = Utils.URL_JOBS,
            feedName = "Jobs",
        ),
    )
    var selectedFeedIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedStoryCount by rememberSaveable(initialStoryCount) {
        mutableIntStateOf(initialStoryCount)
    }
    val horizontalInset = dimensionResource(R.dimen.single_view_side_margin) +
        dimensionResource(R.dimen.widget_config_horizontal_margin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalInset)
                .padding(top = dimensionResource(R.dimen.widget_config_content_top_padding)),
        ) {
            Text(
                text = stringResource(R.string.widget_config_title),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = textSizeResource(R.dimen.widget_config_title_text_size),
                letterSpacing = textSizeResource(R.dimen.widget_config_title_letter_spacing),
                style = IncludeFontPaddingStyle,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(R.dimen.widget_config_feed_top_margin))
                    .selectableGroup(),
            ) {
                feedOptions.forEachIndexed { index, option ->
                    WidgetFeedRow(
                        text = option.label,
                        selected = selectedFeedIndex == index,
                        onClick = { selectedFeedIndex = index },
                    )
                }
            }

            Text(
                text = stringResource(R.string.widget_config_story_count_label),
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.widget_config_count_label_top_margin),
                ),
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = textSizeResource(R.dimen.widget_config_label_text_size),
                letterSpacing = textSizeResource(R.dimen.widget_config_label_letter_spacing),
                style = IncludeFontPaddingStyle,
            )

            WidgetStoryCountSelector(
                selected = selectedStoryCount,
                onSelected = { selectedStoryCount = it },
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.widget_config_count_group_top_margin),
                ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalInset,
                    top = dimensionResource(R.dimen.widget_config_confirm_top_margin),
                    end = horizontalInset,
                    bottom = dimensionResource(R.dimen.widget_config_confirm_bottom_margin),
                )
                .height(dimensionResource(R.dimen.widget_config_confirm_height)),
        ) {
            val confirmShape = RoundedCornerShape(
                dimensionResource(R.dimen.widget_config_confirm_corner_radius),
            )
            Button(
                onClick = {
                    val selectedFeed = feedOptions[selectedFeedIndex]
                    onConfirm(
                        selectedFeed.feedUrl,
                        selectedFeed.feedName,
                        selectedStoryCount,
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        vertical = dimensionResource(
                            R.dimen.widget_config_button_vertical_inset,
                        ),
                    ),
                shapes = ButtonDefaults.shapes(shape = confirmShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = dimensionResource(R.dimen.widget_config_confirm_elevation),
                    pressedElevation = dimensionResource(R.dimen.widget_config_confirm_elevation),
                    focusedElevation = dimensionResource(R.dimen.widget_config_confirm_elevation),
                    hoveredElevation = dimensionResource(R.dimen.widget_config_confirm_elevation),
                ),
                contentPadding = PaddingValues(),
            ) {
                Text(
                    text = stringResource(R.string.widget_config_confirm),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textSizeResource(R.dimen.widget_config_button_text_size),
                    style = IncludeFontPaddingStyle,
                )
            }
        }
    }
}

@Composable
private fun WidgetFeedRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.widget_config_feed_row_height))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(
                dimensionResource(R.dimen.widget_config_feed_control_size),
            ),
            contentAlignment = Alignment.Center,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
        Text(
            text = text,
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.widget_config_feed_text_gap),
            ),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = textSizeResource(R.dimen.widget_config_feed_text_size),
            letterSpacing = textSizeResource(R.dimen.widget_config_feed_letter_spacing),
            style = IncludeFontPaddingStyle,
        )
    }
}

@Composable
private fun WidgetStoryCountSelector(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(8, 16, 24)
    val gap = dimensionResource(R.dimen.widget_config_count_button_gap)
    val defaultCornerRadius = dimensionResource(
        R.dimen.widget_config_count_button_corner_radius,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.widget_config_count_button_height))
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val cornerRadius by animateDpAsState(
                targetValue = if (isPressed) defaultCornerRadius / 2 else defaultCornerRadius,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 800f,
                ),
                label = "widget story count button corners",
            )
            val shape = RoundedCornerShape(cornerRadius)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        onClick = { onSelected(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val selectedContainer = HarmonicTheme.colors.onSurface.copy(alpha = 0.9f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            vertical = dimensionResource(
                                R.dimen.widget_config_button_vertical_inset,
                            ),
                        )
                        .background(
                            color = if (isSelected) selectedContainer else Color.Transparent,
                            shape = shape,
                        )
                        .border(
                            width = dimensionResource(
                                R.dimen.widget_config_count_button_stroke,
                            ),
                            color = if (isSelected) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = shape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.toString(),
                        color = if (isSelected) {
                            HarmonicTheme.colors.background
                        } else {
                            HarmonicTheme.colors.textPrimary
                        },
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = textSizeResource(R.dimen.widget_config_button_text_size),
                        style = IncludeFontPaddingStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun textSizeResource(@DimenRes resourceId: Int): TextUnit = with(LocalDensity.current) {
    dimensionResource(resourceId).toSp()
}

private val IncludeFontPaddingStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun WidgetConfigScreenPreview() {
    HarmonicTheme {
        WidgetConfigScreen(
            initialStoryCount = 16,
            onConfirm = { _, _, _ -> },
        )
    }
}
