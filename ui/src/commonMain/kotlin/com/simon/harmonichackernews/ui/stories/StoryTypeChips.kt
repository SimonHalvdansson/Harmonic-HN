package com.simon.harmonichackernews.ui.stories

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun StoryTypeChips(
    labels: List<String>,
    selectedIndex: Int,
    fontFamily: FontFamily,
    contentPadding: PaddingValues,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0)),
    )
    val colors = MaterialTheme.colorScheme
    val chipBackground = colors.surfaceContainerLow
    val selectedBackground = colors.secondaryContainer
    LaunchedEffect(selectedIndex, labels) {
        if (selectedIndex !in labels.indices) return@LaunchedEffect
        val layout = listState.layoutInfo
        val selected = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (selected == null || selected.offset < layout.viewportStartOffset + layout.beforeContentPadding ||
            selected.offset + selected.size > layout.viewportEndOffset - layout.afterContentPadding
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val direction = LocalLayoutDirection.current
        val maximumChipWidth = (maxWidth - contentPadding.calculateLeftPadding(direction) -
            contentPadding.calculateRightPadding(direction)).coerceAtLeast(48.dp)
        HeaderChipRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(labels, key = { _, label -> label }, contentType = { _, _ -> "story-type-chip" }) { index, label ->
                val selected = index == selectedIndex
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onSelected(index) },
                    modifier = Modifier.widthIn(max = maximumChipWidth).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                painter = painterResource(StoryType.fromLabel(label).menuIcon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                            )
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f, fill = false),
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = chipBackground,
                        labelColor = colors.onSurface,
                        iconColor = colors.onSurfaceVariant,
                        selectedContainerColor = selectedBackground,
                        selectedLabelColor = colors.onSecondaryContainer,
                        selectedLeadingIconColor = colors.onSecondaryContainer,
                    ),
                    border = null,
                )
            }
        }
    }
}
