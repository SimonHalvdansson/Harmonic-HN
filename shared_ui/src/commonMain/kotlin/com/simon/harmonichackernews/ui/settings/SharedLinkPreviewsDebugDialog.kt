package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.LinkPreviewData
import com.simon.harmonichackernews.network.applyTo
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.settings_section_debug_link_previews
import com.simon.harmonichackernews.settings.CommentPreferences
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatformProvider
import com.simon.harmonichackernews.ui.comments.LinkPreviewContent
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.HtmlTextUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class DebugLinkPreviewSample(
    val type: LinkPreviewType,
    val hnId: Int,
    val hnTitle: String,
    val targetUrl: String,
) {
    val hnUrl: String get() = "https://news.ycombinator.com/item?id=$hnId"
}

val DebugLinkPreviewSamples = listOf(
    DebugLinkPreviewSample(LinkPreviewType.GITHUB_REPOSITORY, 49070029, "Show HN: Ctxdiff", "https://github.com/salmanzafar949/ctxdiff"),
    DebugLinkPreviewSample(LinkPreviewType.GITHUB_ISSUE, 49133889, "RipGrep musl binaries occasionally segfault", "https://github.com/BurntSushi/ripgrep/issues/3494"),
    DebugLinkPreviewSample(LinkPreviewType.GITHUB_PULL_REQUEST, 49119063, "JEP 401: Value Objects merged", "https://github.com/openjdk/jdk/pull/31120"),
    DebugLinkPreviewSample(LinkPreviewType.GITHUB_FILE, 49166202, "FFmpeg 9.0", "https://github.com/FFmpeg/FFmpeg/blob/n9.0/RELEASE_NOTES"),
    DebugLinkPreviewSample(LinkPreviewType.GITHUB_RELEASE, 49088887, "Uv 0.12.0", "https://github.com/astral-sh/uv/releases/tag/0.12.0"),
    DebugLinkPreviewSample(LinkPreviewType.GITHUB_DISCUSSION, 49234137, "Transmission forked as ReTransmission", "https://github.com/transmission/transmission/discussions/9031"),
    DebugLinkPreviewSample(LinkPreviewType.GITLAB_PROJECT, 18798209, "Show HN: Snigl", "https://gitlab.com/sifoo/snigl"),
    DebugLinkPreviewSample(LinkPreviewType.HUGGING_FACE_MODEL, 49065752, "Kimi-K3 on Hugging Face", "https://huggingface.co/moonshotai/Kimi-K3"),
    DebugLinkPreviewSample(LinkPreviewType.HUGGING_FACE_DATASET, 49035766, "Forensic refusal dataset file", "https://huggingface.co/datasets/huggingface/forensic-refusal/blob/main/glm5.2.jsonl"),
    DebugLinkPreviewSample(LinkPreviewType.HUGGING_FACE_SPACE, 49041607, "Am I in the Stack?", "https://huggingface.co/spaces/HuggingFaceCode/in-the-stack"),
    DebugLinkPreviewSample(LinkPreviewType.HUGGING_FACE_PAPER, 49289516, "BDH-CQ recurrent latent reasoning", "https://huggingface.co/papers/2608.09888"),
    DebugLinkPreviewSample(LinkPreviewType.HUGGING_FACE_COLLECTION, 49299647, "Qwen3.8 collection", "https://huggingface.co/collections/Qwen/qwen38"),
    DebugLinkPreviewSample(LinkPreviewType.OPENROUTER_MODEL, 49337602, "GPT-5.6 Sol pricing cut", "https://openrouter.ai/openai/gpt-5.6-sol"),
    DebugLinkPreviewSample(LinkPreviewType.STACK_EXCHANGE, 21113344, "Stack Exchange moderator resignations", "https://meta.stackexchange.com/questions/333965/firing-mods-and-forced-slippery-relicensing-is-stack-exchange-still-interested"),
    DebugLinkPreviewSample(LinkPreviewType.ARXIV, 42788451, "Tensor Product Attention Is All You Need", "https://arxiv.org/abs/2501.06425"),
    DebugLinkPreviewSample(LinkPreviewType.CROSSREF_ARTICLE, 49254351, "The Water Footprint of AI", "https://doi.org/10.1016/j.watres.2026.125866"),
    DebugLinkPreviewSample(LinkPreviewType.WIKIPEDIA, 21699011, "Wikipedia Has Cancer", "https://en.wikipedia.org/wiki/User:Guy_Macon/Wikipedia_has_Cancer"),
    DebugLinkPreviewSample(LinkPreviewType.USGS_EARTHQUAKE, 49306577, "Magnitude 7.7 earthquake near Ende", "https://earthquake.usgs.gov/earthquakes/eventpage/us6000tkt2/executive"),
    DebugLinkPreviewSample(LinkPreviewType.MASTODON_POST, 49200439, "My phone thinks a run is a theft", "https://mastodon.gamedev.place/@rygorous/117047697255584965"),
    DebugLinkPreviewSample(LinkPreviewType.BLUESKY_POST, 49028406, "Microsoft deleted Nokia's ringtone archive", "https://bsky.app/profile/techprodbangers.bsky.social/post/3mr4askb6tk2i"),
    DebugLinkPreviewSample(LinkPreviewType.REDDIT_POST, 49140696, "Linux desktop market share over 10%", "https://old.reddit.com/r/linux/comments/1vcpk8i/linux_desktop_market_share_has_hit_over_10_in/"),
    DebugLinkPreviewSample(LinkPreviewType.TWITTER_X, 48012735, "Microsoft Edge passwords in memory", "https://twitter.com/L1v1ng0ffTh3L4N/status/2051308329880719730"),
    DebugLinkPreviewSample(LinkPreviewType.NPM_PACKAGE, 49185692, "Byline article portfolio backup", "https://www.npmjs.com/package/serpapi-byline"),
    DebugLinkPreviewSample(LinkPreviewType.PYPI_PACKAGE, 49309348, "TTSProof automated TTS QA", "https://pypi.org/project/ttsproof/"),
    DebugLinkPreviewSample(LinkPreviewType.CRATES_PACKAGE, 49294385, "SQawk", "https://crates.io/crates/sqawk"),
    DebugLinkPreviewSample(LinkPreviewType.GO_PACKAGE, 49057398, "Go Analysis Framework", "https://pkg.go.dev/golang.org/x/tools/go/analysis"),
    DebugLinkPreviewSample(LinkPreviewType.HOMEBREW_PACKAGE, 46707142, "Dependency-free traceroute", "https://formulae.brew.sh/formula/fastrace"),
    DebugLinkPreviewSample(LinkPreviewType.STATUS_PAGE, 49295947, "Issues reaching status.claude.com", "https://anthropic.statuspage.io/incidents/kmbpgrsszf72"),
    DebugLinkPreviewSample(LinkPreviewType.SUBSTACK_ARTICLE, 49022152, "Writing by hand is good for your brain", "https://nealstephenson.substack.com/p/writing-by-hand-is-good-for-your"),
)

@Composable
fun SharedLinkPreviewsDebugScreen(
    comments: CommentPreferences,
    loadPreview: suspend (LinkPreviewType, String) -> LinkPreviewData,
    onOpenLink: (String) -> Unit,
    onBack: () -> Unit,
) {
    val previewStates = remember {
        mutableStateMapOf<LinkPreviewType, DebugPreviewLoadState>()
    }
    val scope = rememberCoroutineScope()
    val requestPreview: (DebugLinkPreviewSample) -> Unit = remember(scope, loadPreview) {
        { sample ->
            if (sample.type !in previewStates) {
                previewStates[sample.type] = DebugPreviewLoadState.Loading
                scope.launch {
                    previewStates[sample.type] = if (sample.type == LinkPreviewType.TWITTER_X) {
                        DebugPreviewLoadState.Loaded(debugTwitterStory(sample))
                    } else {
                        try {
                            DebugPreviewLoadState.Loaded(
                                loadPreview(sample.type, sample.targetUrl).debugStory(sample),
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            DebugPreviewLoadState.Failed(error.message ?: "Preview failed")
                        }
                    }
                }
            }
        }
    }
    val displaySettings = remember(comments) {
        CommentDisplaySettings.from(
            preferences = comments,
            showInvert = false,
            isTablet = false,
            hasAccountDetails = false,
            canProvideSummary = false,
        )
    }
    val previewPlatform = remember(onOpenLink) {
        CommentsPreviewPlatform(
            textStyle = TextStyle.Default,
            openLink = { it?.let(onOpenLink) },
            downloadPdf = { it?.let(onOpenLink) },
            openCustomTab = { it?.let(onOpenLink) },
            plainText = HtmlTextUtils::plainText,
            annotatedHtml = { html, _, _ -> AnnotatedString(html) },
        )
    }
    CommentsPreviewPlatformProvider(previewPlatform) {
        SettingsPage(
            title = stringResource(Res.string.settings_section_debug_link_previews),
            showNavigation = true,
            onBack = onBack,
        ) {
            items(DebugLinkPreviewSamples, key = { it.type }) { sample ->
                DebugLinkPreviewSampleRow(
                    sample = sample,
                    state = previewStates[sample.type] ?: DebugPreviewLoadState.Loading,
                    displaySettings = displaySettings,
                    onLoadRequested = { requestPreview(sample) },
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

@Composable
private fun DebugLinkPreviewSampleRow(
    sample: DebugLinkPreviewSample,
    state: DebugPreviewLoadState,
    displaySettings: CommentDisplaySettings,
    onLoadRequested: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    LaunchedEffect(sample) {
        onLoadRequested()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Text(
            text = sample.type.title,
            modifier = Modifier.padding(horizontal = 24.dp),
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 21.sp,
        )
        Text(
            text = sample.hnTitle,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLink(sample.hnUrl) }
                .padding(horizontal = 24.dp, vertical = 5.dp),
            color = HarmonicTheme.colors.link,
            fontFamily = ProductSansFontFamily,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textDecoration = TextDecoration.Underline,
        )
        when (val current = state) {
            DebugPreviewLoadState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                HarmonicLoadingIndicator(Modifier.size(42.dp))
            }
            is DebugPreviewLoadState.Loaded -> LinkPreviewContent(
                story = current.story,
                contentVersion = current.story.hashCode(),
                settings = displaySettings,
            )
            is DebugPreviewLoadState.Failed -> Text(
                text = current.message,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(14.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
            )
        }
    }
}

private sealed interface DebugPreviewLoadState {
    data object Loading : DebugPreviewLoadState
    data class Loaded(val story: StoryListItemSnapshot) : DebugPreviewLoadState
    data class Failed(val message: String) : DebugPreviewLoadState
}

private fun LinkPreviewData.debugStory(sample: DebugLinkPreviewSample): StoryListItemSnapshot {
    val story = debugStoryBase(sample)
    applyTo(story)
    return StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot())
}

private fun debugTwitterStory(sample: DebugLinkPreviewSample): StoryListItemSnapshot {
    val story = debugStoryBase(sample)
    story.nitterInfo = NitterInfo().apply {
        userName = "Living Off The Land"
        userTag = "@L1v1ng0ffTh3L4N"
        text = "Microsoft Edge stores passwords in memory in clear text, even when they are not in use."
        date = "2026-03-30"
        replyCount = "Replies"
        reposts = "Reposts"
        likes = "Likes"
    }
    return StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot())
}

private fun debugStoryBase(sample: DebugLinkPreviewSample) = Story().apply {
    id = sample.hnId
    title = sample.hnTitle
    url = sample.targetUrl
    loaded = true
    isLink = true
}
