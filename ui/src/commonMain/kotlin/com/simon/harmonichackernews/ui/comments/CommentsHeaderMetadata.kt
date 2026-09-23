@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments


import com.simon.harmonichackernews.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.ContentTypography
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun CommentsHeaderMetadata(
    story: StoryListItemSnapshot,
    settings: CommentDisplaySettings,
    storyPosterTag: String = "",
    textStyle: TextStyle,
) {
    if (!story.loaded) return
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(settings.font)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!story.isComment) {
                HeaderMetaItem(Res.drawable.ic_thumb_up, story.score.toString(), typography, textStyle)
            }
            HeaderMetaItem(Res.drawable.ic_comment, story.descendants.toString(), typography, textStyle)
            HeaderMetaItem(Res.drawable.ic_schedule, story.timeFormatted, typography, textStyle)
            val posterLabel = buildString {
                append(story.by.orEmpty())
                if (storyPosterTag.isNotBlank()) {
                    append(" (").append(storyPosterTag).append(')')
                }
            }
            HeaderMetaItem(Res.drawable.ic_account_circle, posterLabel, typography, textStyle)
        }
        if (story.isLink) {
            Icon(
                painterResource(Res.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = HarmonicTheme.colors.iconTint,
            )
        }
    }
}

@Composable
private fun HeaderMetaItem(
    icon: DrawableResource,
    label: String,
    typography: ContentTypography,
    textStyle: TextStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = HarmonicTheme.colors.iconTint,
        )
        Text(
            label,
            modifier = Modifier.padding(start = 3.dp),
            color = HarmonicTheme.colors.mutedText,
            fontFamily = typography.family,
            fontSize = typography.commentsHeaderMetaSize.sp,
            style = textStyle,
        )
    }
}
