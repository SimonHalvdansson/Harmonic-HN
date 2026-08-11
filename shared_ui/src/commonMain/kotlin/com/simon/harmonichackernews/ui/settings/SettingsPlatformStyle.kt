package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SettingsPlatformStyle(
    val topBarHeight: Dp = 64.dp,
    val topBarNavigationHeight: Dp = 56.dp,
    val topBarNavigationInset: Dp = 0.dp,
    val textStyle: TextStyle = TextStyle.Default,
)

val LocalSettingsPlatformStyle = staticCompositionLocalOf { SettingsPlatformStyle() }

@Composable
fun ProvideSettingsPlatformStyle(
    style: SettingsPlatformStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingsPlatformStyle provides style, content = content)
}
