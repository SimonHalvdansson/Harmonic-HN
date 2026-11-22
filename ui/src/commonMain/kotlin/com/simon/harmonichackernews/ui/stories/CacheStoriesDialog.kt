package com.simon.harmonichackernews.ui.stories

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.settings.StoryCachePreferences
import kotlin.math.roundToInt

/**
 * Compose replacement for the former `cache_stories_dialog.xml` hierarchy. The selected value
 * is owned by the dialog until confirmation so cancelling has the same behavior as the legacy
 * Material dialog.
 */
@Composable
fun CacheStoriesDialog(
    initialStoryCount: Int,
    integratedWebView: Boolean,
    initialDownloadWebViewContents: Boolean = integratedWebView,
    availableStoryCount: Int = StoryCachePreferences.MAX_COUNT,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit,
) {
    val maximum = availableStoryCount.coerceIn(0, StoryCachePreferences.MAX_COUNT)
    val choices = remember(maximum) {
        (StoryCachePreferences.MIN_COUNT until maximum step StoryCachePreferences.STEP).toList() + maximum
    }
    var choice by rememberSaveable(initialStoryCount, maximum) {
        mutableIntStateOf(choices.indexOfLast { it <= initialStoryCount }.coerceAtLeast(0))
    }
    var downloadWebViewContents by rememberSaveable(
        integratedWebView,
        initialDownloadWebViewContents,
    ) { mutableStateOf(integratedWebView && initialDownloadWebViewContents) }
    val storyCount = choices[choice]
    val sliderDescription = stringResource(Res.string.cache_stories_slider_content_description)

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(stringResource(Res.string.cache_stories_title)) },
        edgeToEdgeContent = true,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = HarmonicDimens.cache_stories_explanation_top_padding,
                        bottom = 8.dp,
                    ),
            ) {
                Text(
                    text = stringResource(Res.string.cache_stories_explanation),
                    modifier = Modifier.padding(
                        start = HarmonicDimens.compose_settings_dialog_content_padding,
                        end = HarmonicDimens.compose_settings_dialog_content_padding,
                        bottom = HarmonicDimens.cache_stories_explanation_bottom_padding,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontFamily = AppUiFontFamily,
                )
                if (integratedWebView) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                downloadWebViewContents = !downloadWebViewContents
                            }
                            .padding(
                                start = HarmonicDimens.compose_settings_dialog_content_padding,
                                top = 12.dp,
                                end = HarmonicDimens.compose_settings_dialog_content_padding,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = downloadWebViewContents,
                            onCheckedChange = null,
                        )
                        Text(
                            text = stringResource(Res.string.cache_stories_webview_contents),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            fontFamily = AppUiFontFamily,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = HarmonicDimens.compose_settings_dialog_content_padding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.cache_stories_count_label),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontFamily = AppUiFontFamily,
                    )
                    Spacer(
                        Modifier.width(
                            HarmonicDimens.cache_stories_value_start_spacing,
                        ),
                    )
                    Text(
                        text = storyCount.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontFamily = AppUiFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (choices.size > 1) Slider(
                    value = choice.toFloat(),
                    onValueChange = { choice = it.roundToInt() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = HarmonicDimens.compose_settings_dialog_content_padding,
                            top = HarmonicDimens.cache_stories_slider_top_padding,
                            end = HarmonicDimens.compose_settings_dialog_content_padding,
                        )
                        .semantics { contentDescription = sliderDescription },
                    valueRange = 0f..choices.lastIndex.toFloat(),
                    steps = choices.size - 2,
                )
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.common_cancel),
                    fontFamily = AppUiFontFamily,
                )
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                enabled = storyCount > 0,
                onClick = {
                    onConfirm(
                        storyCount,
                        integratedWebView && downloadWebViewContents,
                    )
                },
            ) {
                Text(
                    text = stringResource(Res.string.cache_stories_action),
                    fontFamily = AppUiFontFamily,
                )
            }
        },
    )
}
