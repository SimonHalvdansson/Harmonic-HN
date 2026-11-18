package com.simon.harmonichackernews.ui.stories

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import com.simon.harmonichackernews.ui.content.HarmonicSelectableMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import org.jetbrains.compose.resources.painterResource

/** Shared by the Stories title and the widget's frontpage preference. */
@Composable
internal fun StoryTypeDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    types: List<StoryType>,
    selectedType: StoryType,
    onSelected: (StoryType) -> Unit,
    fontFamily: FontFamily = AppUiFontFamily,
    fontSize: TextUnit = 16.sp,
) {
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss, modifier = Modifier.width(240.dp)) {
        types.forEach { type ->
            HarmonicSelectableMenuItem(
                selected = type == selectedType,
                contentPadding = PaddingValues(horizontal = 8.dp),
                text = {
                    HarmonicMenuText(type.label, color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = fontSize)
                },
                onClick = { onSelected(type) },
                leadingIcon = {
                    Icon(painterResource(type.menuIcon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
    }
}
