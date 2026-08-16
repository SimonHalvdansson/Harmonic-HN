package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

/** A fixed, host-positioned escape hatch from comments back to the stories destination. */
@Composable
fun SharedCommentsUpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonicTheme.colors
    val shape = RoundedCornerShape(percent = 50)

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = colors.surfaceContainerHigh.copy(alpha = 0.85f),
        contentColor = colors.onSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 14.dp,
                end = 18.dp,
                bottom = 14.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(colors.drawable),
            )
            Text(
                text = "Back",
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
