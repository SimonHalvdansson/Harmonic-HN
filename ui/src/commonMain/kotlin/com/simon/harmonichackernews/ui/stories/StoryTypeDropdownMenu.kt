package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

/** Shared by the Stories title and the widget's frontpage preference. */
@Composable
internal fun StoryTypeDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    types: List<StoryType>,
    selectedType: StoryType,
    onSelected: (StoryType) -> Unit,
    fontFamily: FontFamily = ProductSansFontFamily,
    fontSize: TextUnit = 16.sp,
) {
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss, modifier = Modifier.width(240.dp)) {
        types.forEach { type ->
            val isSelected = type == selectedType
            DropdownMenuItem(
                modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) HarmonicTheme.colors.accent.copy(alpha = 0.08f) else Color.Transparent)
                    .semantics { selected = isSelected },
                contentPadding = PaddingValues(horizontal = 8.dp),
                text = {
                    HarmonicMenuText(type.label, color = HarmonicTheme.colors.storyNormal,
                        fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = fontSize)
                },
                onClick = { onSelected(type) },
                leadingIcon = {
                    Icon(painterResource(type.menuIcon), null, Modifier.size(24.dp), tint = HarmonicTheme.colors.drawable)
                },
            )
        }
    }
}
