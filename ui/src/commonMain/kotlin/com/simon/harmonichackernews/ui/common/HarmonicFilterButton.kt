package com.simon.harmonichackernews.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Immutable
data class HarmonicFilterButtonColors(
    val checkedBackground: Color,
    val checkedText: Color,
    val checkedStroke: Color,
    val uncheckedText: Color,
    val uncheckedStroke: Color,
)

@Composable
fun harmonicFilterButtonColors(): HarmonicFilterButtonColors {
    val colors = MaterialTheme.colorScheme
    return HarmonicFilterButtonColors(
        checkedBackground = colors.secondaryContainer,
        checkedText = colors.onSecondaryContainer,
        checkedStroke = colors.secondaryContainer,
        uncheckedText = colors.onSurface,
        uncheckedStroke = colors.outlineVariant,
    )
}

@Composable
fun HarmonicFilterButton(
    label: String,
    selected: Boolean,
    position: Int,
    colors: HarmonicFilterButtonColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = ProductSansFontFamily,
    lastPosition: Int = 2,
    icon: DrawableResource? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (selected) colors.checkedBackground else Color.Transparent
    val contentColor = if (selected) colors.checkedText else colors.uncheckedText
    val strokeColor = if (selected) colors.checkedStroke else colors.uncheckedStroke
    val innerCorner by animateDpAsState(
        targetValue = when {
            isPressed -> 4.dp
            selected -> 24.dp
            else -> 8.dp
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "filter button corners",
    )
    val shape = RoundedCornerShape(
        topStart = if (position == 0) 24.dp else innerCorner,
        topEnd = if (position == lastPosition) 24.dp else innerCorner,
        bottomEnd = if (position == lastPosition) 24.dp else innerCorner,
        bottomStart = if (position == 0) 24.dp else innerCorner,
    )
    BoxWithConstraints(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(
                1.dp,
                strokeColor,
                shape,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val textStyle = LocalTextStyle.current.copy(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val labelWidth = rememberTextMeasurer().measure(label, textStyle, maxLines = 1).size.width
        val iconSpace = with(LocalDensity.current) { (18.dp + 7.dp + 16.dp).roundToPx() }
        // Prefer the complete label when equal-width segments become narrow. The icon is
        // decorative, and scaled text may grow the control vertically instead of being clipped.
        val showIcon = icon != null && labelWidth + iconSpace <= constraints.maxWidth
        val rowWidth = with(LocalDensity.current) {
            minOf(constraints.maxWidth, labelWidth + if (showIcon) iconSpace else 16.dp.roundToPx()).toDp()
        }
        Row(
            modifier = Modifier.width(rowWidth).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) icon.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = contentColor,
                style = textStyle,
            )
        }
    }
}
