package com.simon.harmonichackernews.ui.comments

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.prepareCommentHtml
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.ui.settings.linkPreviewIcon

@Composable
internal fun GitHubPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.gitHubRepoInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.owner} / ${info.name}",
            icon = Res.drawable.ic_link_preview_github,
            logoUrl = info.avatarUrl,
        )
        PreviewBody(info.about.orEmpty())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_star, info.formatStars())
                PreviewInfoRow(Res.drawable.ic_visibility, info.formatWatching())
                PreviewInfoRow(Res.drawable.ic_fork_right, info.formatForks())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_link, info.shortenedUrl) {
                    platform.openLink(info.website)
                }
                PreviewInfoRow(Res.drawable.ic_attribution, info.license)
                PreviewInfoRow(Res.drawable.ic_library_books, info.language)
            },
        )
    }
}

@Composable
internal fun GitLabPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.gitLabInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.namespace} / ${info.name}",
            icon = Res.drawable.ic_link_preview_gitlab,
        )
        PreviewBody(info.description.orEmpty())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_star, info.formatStars())
                PreviewInfoRow(Res.drawable.ic_fork_right, info.formatForks())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_link, info.shortenedUrl) {
                    platform.openLink(info.website)
                }
                PreviewInfoRow(Res.drawable.ic_visibility, info.formatVisibility())
                PreviewInfoRow(Res.drawable.ic_library_books, info.language)
            },
        )
    }
}

@Composable
internal fun HuggingFacePreview(story: StoryListItemSnapshot) {
    val info = story.huggingFaceInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.author} / ${info.name}",
            icon = Res.drawable.ic_link_preview_hugging_face,
            logoUrl = info.logoUrl,
            tintIcon = false,
        )
        PreviewBody(info.formatCapability())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_favorite, info.formatLikes())
                PreviewInfoRow(Res.drawable.ic_file_download, info.formatDownloads())
                PreviewInfoRow(Res.drawable.ic_deployed_code, info.formatParameters())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_attribution, info.formatLicense())
                PreviewInfoRow(Res.drawable.ic_schedule, info.formatUpdated())
            },
        )
    }
}

@Composable
internal fun OpenRouterPreview(story: StoryListItemSnapshot) {
    val info = story.openRouterInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.provider} / ${info.name}",
            icon = Res.drawable.ic_link_preview_openrouter,
            logoUrl = info.providerIconUrl,
            logoTint = HarmonicTheme.colors.iconTint,
        )
        PreviewBody(info.description.orEmpty(), maxLines = 12)
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_file_download, info.formatPromptPrice())
                PreviewInfoRow(Res.drawable.ic_arrow_upward, info.formatCompletionPrice())
                PreviewInfoRow(Res.drawable.ic_stacks, info.formatContext())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_perm_media, info.formatModalities())
                PreviewInfoRow(Res.drawable.ic_open_in_new, info.formatMaxOutput())
                PreviewInfoRow(Res.drawable.ic_calendar_today, info.formatKnowledgeCutoff())
            },
        )
    }
}

@Composable
internal fun StackExchangePreview(story: StoryListItemSnapshot) {
    val info = story.stackExchangeInfo ?: return
    Column {
        PreviewHeader("Stack Exchange:")
        PreviewBody(
            text = info.title.orEmpty(),
            bold = true,
            fontSize = 15f,
            lineHeight = 18f,
        )
        PreviewBody(info.formatBy().orEmpty(), maxLines = 20, topPadding = 0.dp)
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_star, info.formatScore())
                PreviewInfoRow(Res.drawable.ic_comment, info.formatAnswerCount())
                PreviewInfoRow(Res.drawable.ic_visibility, info.formatViewCount())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_check, info.formatAnswerState())
                PreviewInfoRow(Res.drawable.ic_library_books, info.formatTags())
                PreviewInfoRow(Res.drawable.ic_person, info.formatAuthor())
            },
        )
    }
}

@Composable
internal fun ArxivPreview(story: StoryListItemSnapshot, settings: CommentDisplaySettings) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.arxivInfo ?: return
    val typography = rememberContentTypography(preferredFont = settings.font)
    val abstractTextSize = if (TextPreferences.sanitizeFont(settings.font) == "googlesansflexrounded") {
        13.5f
    } else {
        14f
    }
    Column {
        PreviewHeader("Abstract:")
        com.simon.harmonichackernews.ui.content.MathPreviewText(
            text = info.arxivAbstract.orEmpty(),
            color = HarmonicTheme.colors.contentPrimary,
            fontFamily = typography.family,
            fontSize = abstractTextSize.sp,
            lineHeight = 18.sp,
            style = platform.textStyle,
        )
        PreviewInfoRow(Res.drawable.ic_calendar_today, runCatching(info::formatDate).getOrNull())
        PreviewInfoRow(
            when (info.authors.size) {
                1 -> Res.drawable.ic_person
                2 -> Res.drawable.ic_group
                else -> Res.drawable.ic_groups
            },
            runCatching(info::concatNames).getOrNull(),
        )
        PreviewInfoRow(Res.drawable.ic_library_books, runCatching(info::formatSubjects).getOrNull())
        val actionColors = ButtonDefaults.buttonColors(
            containerColor = HarmonicTheme.colors.secondaryContainer,
            contentColor = HarmonicTheme.colors.onSecondaryContainer,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            info.htmlUrl?.let { htmlUrl ->
                Button(
                    onClick = { platform.openLink(htmlUrl) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp,
                    ),
                    colors = actionColors,
                ) {
                    ArxivActionLabel("HTML")
                }
            }
            Button(
                onClick = { platform.downloadPdf(info.pDFURL) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = if (info.htmlUrl != null) {
                    RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp,
                    )
                } else {
                    RoundedCornerShape(24.dp)
                },
                colors = actionColors,
            ) {
                ArxivActionLabel("PDF")
            }
        }
    }
}

@Composable
private fun ArxivActionLabel(text: String) {
    Text(
        text = text,
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}

@Composable
internal fun WikipediaPreview(story: StoryListItemSnapshot) {
    val info = story.wikiInfo ?: return
    val summary = remember(info.summary) { prepareCommentHtml(info.summary.orEmpty()).text }
    Column {
        PreviewHeader("Wikipedia summary:")
        PreviewBody(
            summary,
            maxLines = 40,
            topPadding = 0.dp,
            bottomPadding = 3.dp,
            fontSize = 15f,
            lineHeight = 18f,
        )
    }
}

@Composable
internal fun RichLinkPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.linkPreviewInfo ?: return
    val details = remember(info.details) { splitRichPreviewDetails(info.details) }
    val isRelease = info.type == LinkPreviewType.GITHUB_RELEASE
    Column {
        PreviewHeader(
            text = info.title,
            icon = info.type.linkPreviewIcon(),
            logoUrl = info.imageUrl.takeUnless { isRelease },
        )
        PreviewBody(
            text = info.subtitle.orEmpty(),
            bold = true,
            topPadding = 5.dp,
            bottomPadding = 0.dp,
        )
        if (isRelease) {
            ReleaseMarkdownContent(
                markdown = info.description.orEmpty(),
                pageUrl = info.url,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else if (info.type.hasMarkdownDescription()) {
            SummaryMarkdownText(
                markdown = info.description.orEmpty(),
                baseUrl = info.url,
                onOpenLink = platform.openLink,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                color = HarmonicTheme.colors.contentPrimary,
                linkColor = HarmonicTheme.colors.link,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            PreviewBody(
                text = info.description.orEmpty(),
                maxLines = 12,
                topPadding = 4.dp,
            )
        }
        PreviewInfoColumns(
            left = {
                details.left.forEach { detail ->
                    RichPreviewDetail(detail, info.type)
                }
            },
            right = {
                details.right.forEach { detail ->
                    RichPreviewDetail(detail, info.type)
                }
            },
        )
    }
}

private fun LinkPreviewType.hasMarkdownDescription(): Boolean = when (this) {
    LinkPreviewType.GITHUB_ISSUE,
    LinkPreviewType.GITHUB_PULL_REQUEST,
    LinkPreviewType.GITHUB_RELEASE,
    LinkPreviewType.GITHUB_DISCUSSION,
    -> true
    else -> false
}

@Composable
private fun RichPreviewDetail(detail: LinkPreviewDetail, type: LinkPreviewType) {
    val platform = LocalCommentsPreviewPlatform.current
    val projectUrl = detail.value.takeIf {
        detail.label == "Project URL" && (it.startsWith("https://") || it.startsWith("http://"))
    }
    PreviewInfoRow(
        icon = when (detail.label.lowercase()) {
            "project url" -> Res.drawable.ic_link
            "magnitude" -> Res.drawable.ic_earthquake
            "type" -> if (type == LinkPreviewType.USGS_EARTHQUAKE) Res.drawable.ic_earthquake else Res.drawable.ic_subject
            "depth" -> Res.drawable.ic_vertical_align_bottom
            "significance" -> Res.drawable.ic_priority_high
            "tsunami information" -> Res.drawable.ic_tsunami
            "author", "authors" -> Res.drawable.ic_person
            "published", "updated", "started" -> Res.drawable.ic_calendar_today
            "likes", "favourites", "upvotes" -> Res.drawable.ic_favorite
            "comments", "replies" -> Res.drawable.ic_comment
            "downloads", "recent downloads", "installs (30d)" -> Res.drawable.ic_file_download
            "license" -> Res.drawable.ic_attribution
            "version", "revision" -> Res.drawable.ic_tag
            "state", "status", "impact", "access" -> Res.drawable.ic_info
            "files", "items", "dependencies" -> Res.drawable.ic_library_books
            else -> Res.drawable.ic_subject
        },
        text = projectUrl ?: detail.displayText ?: "${detail.label}: ${detail.value}",
        onClick = projectUrl?.let { url -> { platform.openLink(url) } },
    )
}

@Composable
internal fun NitterPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.nitterInfo ?: return
    Column {
        PreviewHeader("${info.userName.orEmpty()} ${info.userTag.orEmpty()}")
        PreviewBody(
            platform.plainText(info.text.orEmpty()),
            topPadding = 0.dp,
            fontSize = 15f,
            lineHeight = 18f,
        )
        if (!info.imgSrc.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { platform.openCustomTab(story.url) },
                        onLongClick = null,
                    ),
            ) {
                AsyncImage(
                    model = info.imgSrc,
                    contentDescription = if (info.hasVideo) "Tweet video" else "Tweet image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                if (info.hasVideo) {
                    Text(
                        "VIDEO",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                Row {
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_calendar_today,
                        text = info.date,
                        iconWidth = 14.dp,
                    )
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_reply,
                        text = info.replyCount,
                        startPadding = 1.dp,
                        endPadding = 7.dp,
                    )
                }
                Row {
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_action_retweet,
                        text = info.reposts,
                        startPadding = 1.dp,
                    )
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_thumb_up,
                        text = info.likes,
                        iconWidth = 12.dp,
                        endPadding = 4.dp,
                    )
                }
            }
            Button(
                onClick = { platform.openCustomTab(story.url) },
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ),
            ) {
                Icon(painterResource(Res.drawable.ic_link_preview_x), contentDescription = null)
                Text(
                    "Open on X",
                    modifier = Modifier.padding(start = 8.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PreviewCompactInfo(
    icon: DrawableResource,
    text: String?,
    iconWidth: Dp = 15.dp,
    startPadding: Dp = 2.dp,
    endPadding: Dp = 8.dp,
) {
    if (text.isNullOrBlank()) return
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(width = iconWidth, height = 16.dp),
        tint = HarmonicTheme.colors.iconTint,
    )
    Text(
        text,
        modifier = Modifier.padding(start = startPadding, end = endPadding),
        color = HarmonicTheme.colors.contentPrimary,
        fontFamily = ProductSansFontFamily,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        style = LocalCommentsPreviewPlatform.current.textStyle,
    )
}
