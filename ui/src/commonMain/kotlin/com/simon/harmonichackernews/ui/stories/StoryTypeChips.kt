package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
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
    val colors = HarmonicTheme.colors
    val isDark = HarmonicTheme.isDark
    val chipBackground = lerp(colors.background, Color.White, if (isDark) 0.08f else 0.45f)
    val selectedBackground = if (isDark) {
        lerp(chipBackground, colors.secondaryContainer, 0.55f)
    } else {
        lerp(colors.secondaryContainer, colors.textPrimary, 0.10f)
    }
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
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().selectableGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels, key = { _, label -> label }) { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onSelected(index) },
                modifier = Modifier.heightIn(min = 40.dp),
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
                            tint = if (selected) colors.onSecondaryContainer else colors.iconTint,
                        )
                        Text(
                            text = label,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = chipBackground,
                    labelColor = colors.textPrimary,
                    iconColor = colors.iconTint,
                    selectedContainerColor = selectedBackground,
                    selectedLabelColor = colors.onSecondaryContainer,
                    selectedLeadingIconColor = colors.onSecondaryContainer,
                ),
                border = null,
            )
        }
    }
}
