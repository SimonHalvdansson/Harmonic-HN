package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.RelativeTimeFormatter
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** Immutable, serializable Hacker News item content, independent of loading and rendering state. */
@Serializable
data class StorySnapshot(
    val id: Int,
    val author: String? = null,
    val title: String? = null,
    val text: String? = null,
    val url: String? = null,
    val score: Int = 0,
    val descendantCount: Int = 0,
    val createdAtEpochSeconds: Int = 0,
    val childIds: List<Int> = emptyList(),
    val pollOptionIds: List<Int> = emptyList(),
    val isJob: Boolean = false,
    val isComment: Boolean = false,
    val parentId: Int = 0,
)

/** Immutable UI enrichment state kept separate from the Hacker News item itself. */
@Serializable
data class StoryPresentationSnapshot(
    val loaded: Boolean = false,
    val clicked: Boolean = false,
    val loadingFailed: Boolean = false,
    val isLink: Boolean = false,
    val isFrontpageLink: Boolean = false,
    val pdfTitle: String? = null,
    val videoTitle: String? = null,
    val previewImage: ResourceLoadSnapshot = ResourceLoadSnapshot(),
    val favicon: ResourceLoadSnapshot = ResourceLoadSnapshot(),
    val previewTint: ResourceTintSnapshot? = null,
    val faviconTint: ResourceTintSnapshot? = null,
    val linkSummaryDescription: String? = null,
    val linkSummaryLoaded: Boolean = false,
    val commentMaster: CommentMasterSnapshot? = null,
    val summary: String? = null,
    val summaryGeneratedSuccessfully: Boolean = false,
    val pollOptions: List<PollOptionSnapshot> = emptyList(),
    override val repoInfo: RepoInfo? = null,
    override val gitLabInfo: GitLabInfo? = null,
    override val huggingFaceInfo: HuggingFaceModelInfo? = null,
    override val openRouterInfo: OpenRouterModelInfo? = null,
    override val stackExchangeInfo: StackExchangeInfo? = null,
    override val arxivInfo: ArxivInfo? = null,
    override val wikiInfo: WikipediaInfo? = null,
    override val nitterInfo: NitterInfo? = null,
    override val linkPreviewInfo: LinkPreviewInfo? = null,
    val linkPreviewLoading: Boolean = false,
) : LinkPreviewState

@Serializable
data class PollOptionSnapshot(
    val loaded: Boolean,
    val loadFailed: Boolean,
    val text: String?,
    val points: Int,
    val id: Int,
)

@Serializable
data class ResourceLoadSnapshot(
    val url: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

@Serializable
data class ResourceTintSnapshot(
    val colorArgb: Int,
    val loaded: Boolean,
    val sourceUrl: String? = null,
    val baseColorArgb: Int = 0,
    val mode: String? = null,
)

@Serializable
data class CommentMasterSnapshot(
    val id: Int = 0,
    val title: String? = null,
    val url: String? = null,
    val author: String? = null,
    val score: Int = 0,
    val createdAtEpochSeconds: Int = 0,
    val descendantCount: Int = 0,
    val loaded: Boolean = false,
)

/** Immutable, serializable comment content; expansion and tree layout remain presentation state. */
@Serializable
data class CommentSnapshot(
    val id: Int,
    val author: String? = null,
    val parentId: Int = 0,
    val text: String? = null,
    val createdAtEpochSeconds: Int = 0,
    val childIds: List<Int> = emptyList(),
    val expandedAnchorText: String? = null,
)

@Serializable
data class CommentPresentationSnapshot(
    val expanded: Boolean = false,
    val depth: Int = 0,
    val childCount: Int = 0,
    val totalReplies: Int = 0,
    val sortOrder: Int = 0,
)

fun Story.toSnapshot(): StorySnapshot = StorySnapshot(
    id = id,
    author = by,
    title = title,
    text = text,
    url = url,
    score = score,
    descendantCount = descendants,
    createdAtEpochSeconds = time,
    childIds = kids?.toList().orEmpty(),
    pollOptionIds = pollOptions?.toList().orEmpty(),
    isJob = isJob,
    isComment = isComment,
    parentId = parentId,
)

fun Story.presentationSnapshot(): StoryPresentationSnapshot = StoryPresentationSnapshot(
    loaded = loaded,
    clicked = clicked,
    loadingFailed = loadingFailed,
    isLink = isLink,
    isFrontpageLink = isFrontpageLink,
    pdfTitle = pdfTitle,
    videoTitle = videoTitle,
    previewImage = ResourceLoadSnapshot(
        url = previewImageUrl,
        loaded = previewImageUrlLoaded,
    ),
    favicon = ResourceLoadSnapshot(
        url = faviconTintSourceUrl,
        loaded = faviconTintColorLoaded,
    ),
    previewTint = ResourceTintSnapshot(
        colorArgb = previewImageTintColor,
        loaded = previewImageTintColorLoaded,
        sourceUrl = previewImageTintSourceUrl,
        baseColorArgb = previewImageTintBaseColor,
        mode = previewImageTintMode,
    ),
    faviconTint = ResourceTintSnapshot(
        colorArgb = faviconTintColor,
        loaded = faviconTintColorLoaded,
        sourceUrl = faviconTintSourceUrl,
        baseColorArgb = faviconTintBaseColor,
        mode = faviconTintMode,
    ),
    linkSummaryDescription = linkSummaryDescription,
    linkSummaryLoaded = linkSummaryLoaded,
    commentMaster = CommentMasterSnapshot(
        id = commentMasterId,
        title = commentMasterTitle,
        url = commentMasterUrl,
        author = commentMasterBy,
        score = commentMasterScore,
        createdAtEpochSeconds = commentMasterTime,
        descendantCount = commentMasterDescendants,
        loaded = commentMasterLoaded,
    ),
    summary = summary,
    summaryGeneratedSuccessfully = summaryGeneratedSuccessfully,
    pollOptions = pollOptionArrayList.orEmpty().map {
        PollOptionSnapshot(it.loaded, it.loadFailed, it.text, it.points, it.id)
    },
    repoInfo = repoInfo,
    gitLabInfo = gitLabInfo,
    huggingFaceInfo = huggingFaceInfo,
    openRouterInfo = openRouterInfo,
    stackExchangeInfo = stackExchangeInfo,
    arxivInfo = arxivInfo,
    wikiInfo = wikiInfo,
    nitterInfo = nitterInfo,
    linkPreviewInfo = linkPreviewInfo,
    linkPreviewLoading = linkPreviewLoading,
)

fun Story.applySnapshot(snapshot: StorySnapshot): Story = apply {
    id = snapshot.id
    by = snapshot.author
    title = snapshot.title
    text = snapshot.text
    url = snapshot.url
    score = snapshot.score
    descendants = snapshot.descendantCount
    time = snapshot.createdAtEpochSeconds
    kids = snapshot.childIds.takeIf(List<Int>::isNotEmpty)?.toIntArray()
    pollOptions = snapshot.pollOptionIds.takeIf(List<Int>::isNotEmpty)?.toIntArray()
    isJob = snapshot.isJob
    isComment = snapshot.isComment
    parentId = snapshot.parentId
}

fun Comment.toSnapshot(): CommentSnapshot = CommentSnapshot(
    id = id,
    author = by,
    parentId = parent,
    text = text,
    createdAtEpochSeconds = time,
    childIds = kidsIds?.toList().orEmpty(),
    expandedAnchorText = expandedAnchorText,
)

fun Comment.presentationSnapshot(): CommentPresentationSnapshot = CommentPresentationSnapshot(
    expanded = expanded,
    depth = depth,
    childCount = children,
    totalReplies = totalReplies,
    sortOrder = sortOrder,
)

fun Comment.applySnapshot(snapshot: CommentSnapshot): Comment = apply {
    id = snapshot.id
    by = snapshot.author
    parent = snapshot.parentId
    text = snapshot.text
    time = snapshot.createdAtEpochSeconds
    kidsIds = snapshot.childIds.takeIf(List<Int>::isNotEmpty)?.toIntArray()
}

object ItemTimeFormatter {
    fun format(createdAtEpochSeconds: Int, nowMillis: Long): String =
        RelativeTimeFormatter.format(createdAtEpochSeconds.toLong(), nowMillis)

    fun formatNow(createdAtEpochSeconds: Int): String =
        format(createdAtEpochSeconds, Clock.System.now().toEpochMilliseconds())
}
