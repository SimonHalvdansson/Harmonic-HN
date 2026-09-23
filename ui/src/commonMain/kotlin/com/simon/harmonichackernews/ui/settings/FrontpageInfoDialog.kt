package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_info
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun FrontpageInfoButton(type: StoryType, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(Res.drawable.ic_info),
            contentDescription = "About ${type.label}",
            modifier = Modifier.size(20.dp),
            tint = HarmonicTheme.colors.iconTint,
        )
    }
}

@Composable
internal fun FrontpageInfoDialog(type: StoryType, onDismiss: () -> Unit) {
    // HN's list definitions: https://news.ycombinator.com/lists
    val description = when (type) {
        StoryType.CLASSIC -> "An alternative Hacker News frontpage based on votes from its oldest accounts."
        StoryType.BEST_COMMENTS -> "The most-upvoted Hacker News comments from the last 48 hours."
        StoryType.HIGHLIGHTS -> "A curated collection of standout Hacker News comments and discussions from over the years."
        StoryType.ACTIVE -> "Stories with the most active discussions on Hacker News right now."
        StoryType.FRONT -> "Stories that appeared on the Hacker News frontpage on a particular day. Use the date controls to browse past days."
        StoryType.UNSLOP -> "Hacker News stories with AI-related posts filtered out by unslop.news."
        else -> return
    }
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = description,
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
