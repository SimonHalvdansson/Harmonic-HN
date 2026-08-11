package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Composable
internal fun HarmonicFilterButton(
    label: String,
    selected: Boolean,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = ProductSansFontFamily,
    lastPosition: Int = 2,
) {
    SharedHarmonicFilterButton(
        label = label,
        selected = selected,
        position = position,
        colors = rememberHarmonicFilterColors(),
        onClick = onClick,
        modifier = modifier,
        fontFamily = fontFamily,
        lastPosition = lastPosition,
    )
}

@Composable
internal fun rememberHarmonicFilterColors(): HarmonicFilterButtonColors {
    val context = LocalContext.current
    val fallback = HarmonicTheme.colors
    return remember(context, fallback) {
        val button = MaterialButton(
            context,
            null,
            MaterialR.attr.materialButtonOutlinedStyle,
        ).apply { isCheckable = true }
        val checkedState = intArrayOf(
            android.R.attr.state_enabled,
            android.R.attr.state_checkable,
            android.R.attr.state_checked,
        )
        val uncheckedState = intArrayOf(
            android.R.attr.state_enabled,
            android.R.attr.state_checkable,
            -android.R.attr.state_checked,
        )
        fun android.content.res.ColorStateList?.colorFor(
            state: IntArray,
            default: Color,
        ): Color = Color(this?.getColorForState(state, default.toArgb()) ?: default.toArgb())

        HarmonicFilterButtonColors(
            checkedBackground = button.backgroundTintList.colorFor(
                checkedState,
                fallback.storyNormal,
            ),
            checkedText = button.textColors.colorFor(checkedState, fallback.background),
            checkedStroke = button.strokeColor.colorFor(checkedState, fallback.storyNormal),
            uncheckedText = button.textColors.colorFor(uncheckedState, fallback.storyNormal),
            uncheckedStroke = button.strokeColor.colorFor(uncheckedState, fallback.outlineVariant),
        )
    }
}
