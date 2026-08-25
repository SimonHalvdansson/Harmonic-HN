package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Immutable
data class HarmonicFilterButtonColors(
    val checkedBackground: Color,
    val checkedText: Color,
    val checkedStroke: Color,
    val uncheckedText: Color,
    val uncheckedStroke: Color,
)

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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val innerCorner by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "filter button corners",
    )
    val shape = if (selected) {
        RoundedCornerShape(if (isPressed) 12.dp else 24.dp)
    } else {
        when (position) {
            0 -> RoundedCornerShape(
                topStart = 24.dp,
                topEnd = innerCorner,
                bottomEnd = innerCorner,
                bottomStart = 24.dp,
            )
            lastPosition -> RoundedCornerShape(
                topStart = innerCorner,
                topEnd = 24.dp,
                bottomEnd = 24.dp,
                bottomStart = innerCorner,
            )
            else -> RoundedCornerShape(innerCorner)
        }
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) colors.checkedBackground else Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.checkedStroke else colors.uncheckedStroke,
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
        Text(
            text = label,
            color = if (selected) colors.checkedText else colors.uncheckedText,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}
