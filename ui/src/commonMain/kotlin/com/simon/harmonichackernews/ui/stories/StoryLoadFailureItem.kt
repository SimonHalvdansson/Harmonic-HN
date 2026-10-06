package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_error
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

/** One retry target for the whole row, without unavailable story metadata or actions. */
@Composable
internal fun StoryLoadFailureItem(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HarmonicTheme.colors
    Surface(
        onClick = onRetry,
        color = lerp(colors.background, colors.accent, 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_error),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = "Loading failed",
                    color = colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "Tap to try again",
                    color = colors.textSecondary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
