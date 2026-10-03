package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.NewStoriesFilter
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
internal fun NewStoriesFilterDialog(
    initial: NewStoriesFilter,
    onDismiss: () -> Unit,
    onApply: (NewStoriesFilter) -> Unit,
) {
    var points by rememberSaveable(initial) { mutableIntStateOf(initial.minimumPoints) }
    var comments by rememberSaveable(initial) { mutableIntStateOf(initial.minimumComments) }
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Filter new stories") },
        scrollableContent = true,
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    "Show stories that meet both minimums. Only applies to New stories.",
                    color = HarmonicTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ProductSansFontFamily,
                )
                ThresholdSlider("Minimum points", points, { points = it })
                ThresholdSlider("Minimum comments", comments, { comments = it })
                TextButton(
                    onClick = { onApply(NewStoriesFilter()) },
                    enabled = initial.active || points > 0 || comments > 0,
                ) {
                    Icon(painterResource(Res.drawable.ic_close), null, Modifier.size(18.dp))
                    Text("Clear filters", Modifier.padding(start = 8.dp), fontFamily = ProductSansFontFamily)
                }
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = ProductSansFontFamily)
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { onApply(NewStoriesFilter(points, comments)) }) {
                Text("Apply", fontFamily = ProductSansFontFamily, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun ThresholdSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    val thresholds = NewStoriesFilter.thresholds
    val valueLabel = if (value == 0) "Any" else "$value+"
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, Modifier.weight(1f), color = HarmonicTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium, fontFamily = ProductSansFontFamily,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    valueLabel, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ProductSansFontFamily, fontWeight = FontWeight.Bold,
                )
            }
        }
        Slider(
            value = thresholds.indexOf(value).coerceAtLeast(0).toFloat(),
            onValueChange = { onValueChange(thresholds[it.roundToInt()]) },
            valueRange = 0f..thresholds.lastIndex.toFloat(),
            steps = thresholds.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeTickColor = MaterialTheme.colorScheme.surface,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = if (value == 0) "Any" else "At least $value"
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Any", style = MaterialTheme.typography.labelSmall, color = HarmonicTheme.colors.textSecondary)
            Text("${thresholds.last()}+", style = MaterialTheme.typography.labelSmall, color = HarmonicTheme.colors.textSecondary)
        }
    }
}

@Composable
internal fun NewStoriesFilterIndicator(
    filter: NewStoriesFilter,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = buildList {
        if (filter.minimumPoints > 0) add("${filter.minimumPoints}+ points")
        if (filter.minimumComments > 0) add("${filter.minimumComments}+ comments")
    }.joinToString(" · ")
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = HarmonicTheme.colors.contentCardBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f, fill = false).clickable(onClickLabel = "Edit filters", onClick = onEdit)
                    .padding(start = 12.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_filter_list), null, Modifier.size(18.dp))
                Text(summary, style = MaterialTheme.typography.labelLarge, fontFamily = ProductSansFontFamily)
            }
            IconButton(onClick = onClear) {
                Icon(painterResource(Res.drawable.ic_close), "Clear new story filters", Modifier.size(18.dp))
            }
        }
    }
}
