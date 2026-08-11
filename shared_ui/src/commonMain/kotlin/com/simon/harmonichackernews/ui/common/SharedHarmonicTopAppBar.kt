package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

/** Platform-neutral app bar used by settings and informational screens. */
@Composable
fun SharedHarmonicTopAppBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    navigationContentDescription: String = "Navigate up",
    toolbarHeight: Dp = 64.dp,
    navigationHeight: Dp = 56.dp,
    navigationInset: Dp = 0.dp,
    platformTextStyle: TextStyle = TextStyle.Default,
) {
    val colors = HarmonicTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(toolbarHeight)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Spacer(modifier = Modifier.width(navigationInset))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(navigationHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        indication = ripple(bounded = false, radius = 24.dp),
                        onClick = onBack,
                    )
                    .clearAndSetSemantics {
                        contentDescription = navigationContentDescription
                        role = Role.Button
                        onClick {
                            onBack()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(colors.drawable),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        Text(
            text = title,
            modifier = Modifier.padding(top = 1.dp),
            color = colors.onSurface,
            fontFamily = FontFamily.SansSerif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            style = platformTextStyle,
        )
    }
}
