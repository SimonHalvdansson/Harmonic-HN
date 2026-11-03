package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Readable tag accents in both light and dark palettes. */
@Composable
fun userTagColor(color: UserTagColor): Color {
    val dark = HarmonicTheme.isDark
    return when (color) {
        UserTagColor.Theme -> MaterialTheme.colorScheme.primary
        UserTagColor.Red -> if (dark) Color(0xFFFFB4AB) else Color(0xFF9C211B)
        UserTagColor.Orange -> if (dark) Color(0xFFFFB87A) else Color(0xFF884B08)
        UserTagColor.Green -> if (dark) Color(0xFF9BD69B) else Color(0xFF28632B)
        UserTagColor.Blue -> if (dark) Color(0xFFA6C8FF) else Color(0xFF245EA5)
        UserTagColor.Purple -> if (dark) Color(0xFFDAB9FF) else Color(0xFF75419C)
    }
}
