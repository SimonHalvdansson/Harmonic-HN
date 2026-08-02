package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.SettingsUtils

/**
 * Compose replacement for the former `cache_stories_dialog.xml` hierarchy. The selected value
 * is owned by the dialog until confirmation so cancelling has the same behavior as the legacy
 * Material dialog.
 */
@Composable
fun CacheStoriesDialog(
    initialStoryCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val minimum = SettingsUtils.MIN_STORIES_TO_CACHE
    val maximum = SettingsUtils.MAX_STORIES_TO_CACHE
    val step = SettingsUtils.STORIES_TO_CACHE_STEP
    var storyCount by rememberSaveable(initialStoryCount) {
        mutableFloatStateOf(SettingsUtils.sanitizeStoriesToCache(initialStoryCount).toFloat())
    }
    val sanitizedStoryCount = SettingsUtils.sanitizeStoriesToCache(storyCount.toInt())
    val sliderDescription = stringResource(R.string.cache_stories_slider_content_description)

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(stringResource(R.string.cache_stories_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = dimensionResource(R.dimen.cache_stories_explanation_top_padding),
                    ),
            ) {
                Text(
                    text = stringResource(R.string.cache_stories_explanation),
                    modifier = Modifier.padding(
                        bottom = dimensionResource(
                            R.dimen.cache_stories_explanation_bottom_padding,
                        ),
                    ),
                    color = HarmonicTheme.colors.textPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontFamily = ProductSansFontFamily,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.cache_stories_count_label),
                        modifier = Modifier.weight(1f),
                        color = HarmonicTheme.colors.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontFamily = ProductSansFontFamily,
                    )
                    Spacer(
                        Modifier.width(
                            dimensionResource(R.dimen.cache_stories_value_start_spacing),
                        ),
                    )
                    Text(
                        text = sanitizedStoryCount.toString(),
                        color = HarmonicTheme.colors.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = storyCount,
                    onValueChange = {
                        storyCount = SettingsUtils.sanitizeStoriesToCache(it.toInt()).toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = dimensionResource(R.dimen.cache_stories_slider_top_padding),
                        )
                        .semantics { contentDescription = sliderDescription },
                    valueRange = minimum.toFloat()..maximum.toFloat(),
                    steps = ((maximum - minimum) / step) - 1,
                )
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = { onConfirm(sanitizedStoryCount) },
            ) {
                Text(stringResource(R.string.cache_stories_action))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CacheStoriesDialogPreview() {
    HarmonicTheme {
        CacheStoriesDialog(
            initialStoryCount = SettingsUtils.DEFAULT_STORIES_TO_CACHE,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
