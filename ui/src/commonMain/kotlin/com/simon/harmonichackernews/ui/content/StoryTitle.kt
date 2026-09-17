package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_action_pdf
import com.simon.harmonichackernews.resources.ic_action_video
import org.jetbrains.compose.resources.painterResource

enum class StoryTitleBadge {
    PDF,
    VIDEO,
}

data class StoryTitlePresentation(
    val text: String,
    val badge: StoryTitleBadge? = null,
)

/** Uses the cleaned parser title when a legacy title badge was detected. */
fun storyTitlePresentation(
    title: String?,
    pdfTitle: String?,
    videoTitle: String?,
): StoryTitlePresentation {
    val nonEmptyPdfTitle = pdfTitle?.takeUnless(String::isEmpty)
    if (nonEmptyPdfTitle != null) {
        return StoryTitlePresentation(nonEmptyPdfTitle, StoryTitleBadge.PDF)
    }

    val nonEmptyVideoTitle = videoTitle?.takeUnless(String::isEmpty)
    if (nonEmptyVideoTitle != null) {
        return StoryTitlePresentation(nonEmptyVideoTitle, StoryTitleBadge.VIDEO)
    }

    return StoryTitlePresentation(title.orEmpty())
}

@Composable
fun StoryTitleText(
    text: String,
    badge: StoryTitleBadge? = null,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (badge == null) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = color,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val inlineContent = remember(badge, color) {
        mapOf(
            StoryTitleBadgeInlineId to InlineTextContent(
                placeholder = Placeholder(
                    width = 1.5.em,
                    height = 1.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
                children = {
                    Icon(
                        painter = painterResource(badge.icon),
                        contentDescription = badge.contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        tint = color,
                    )
                },
            ),
        )
    }
    val annotatedText = remember(text, badge) {
        buildAnnotatedString {
            append(text)
            append(' ')
            appendInlineContent(StoryTitleBadgeInlineId)
        }
    }

    androidx.compose.material3.Text(
        text = annotatedText,
        modifier = modifier,
        inlineContent = inlineContent,
        color = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private const val StoryTitleBadgeInlineId = "story-title-badge"

private val StoryTitleBadge.icon
    get() = when (this) {
        StoryTitleBadge.PDF -> Res.drawable.ic_action_pdf
        StoryTitleBadge.VIDEO -> Res.drawable.ic_action_video
    }

private val StoryTitleBadge.contentDescription: String
    get() = when (this) {
        StoryTitleBadge.PDF -> "PDF"
        StoryTitleBadge.VIDEO -> "Video"
    }
