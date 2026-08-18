package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.RelativeTimeFormatter
import com.simon.harmonichackernews.utils.ArxivResolver
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
    val linkSummaryLoading: Boolean = false,
    val commentMaster: CommentMasterSnapshot? = null,
    val summary: String? = null,
    val summaryGeneratedSuccessfully: Boolean = false,
    val pollOptions: List<PollOptionSnapshot> = emptyList(),
    val repoInfo: RepoInfoSnapshot? = null,
    val gitLabInfo: GitLabInfoSnapshot? = null,
    val huggingFaceInfo: HuggingFaceModelInfoSnapshot? = null,
    val openRouterInfo: OpenRouterModelInfoSnapshot? = null,
    val stackExchangeInfo: StackExchangeInfoSnapshot? = null,
    val arxivInfo: ArxivInfoSnapshot? = null,
    val wikiInfo: WikipediaInfoSnapshot? = null,
    val nitterInfo: NitterInfoSnapshot? = null,
    val linkPreviewLoading: Boolean = false,
)

@Serializable
data class PollOptionSnapshot(
    val loaded: Boolean,
    val loadFailed: Boolean,
    val text: String?,
    val points: Int,
    val id: Int,
)

@Serializable
data class RepoInfoSnapshot(
    val name: String?,
    val owner: String?,
    val avatarUrl: String? = null,
    val about: String?,
    val website: String?,
    val license: String?,
    val language: String?,
    val stars: Int,
    val watching: Int,
    val forks: Int,
) {
    fun formatStars(): String = LinkPreviewFormatUtils.formatCount(stars, "star", "stars")
    fun formatWatching(): String = "${LinkPreviewFormatUtils.kFormat(watching)} watching"
    fun formatForks(): String = LinkPreviewFormatUtils.formatCount(forks, "fork", "forks")
    val shortenedUrl: String? get() = LinkPreviewFormatUtils.shortenUrl(website)
}

@Serializable
data class GitLabInfoSnapshot(
    val name: String?,
    val namespace: String?,
    val description: String?,
    val website: String?,
    val language: String?,
    val visibility: String?,
    val stars: Int,
    val forks: Int,
) {
    fun formatStars(): String = LinkPreviewFormatUtils.formatCount(stars, "star", "stars")
    fun formatForks(): String = LinkPreviewFormatUtils.formatCount(forks, "fork", "forks")
    fun formatVisibility(): String? = visibility?.replaceFirstChar { it.uppercase() }
    val shortenedUrl: String? get() = LinkPreviewFormatUtils.shortenUrl(website)
}

@Serializable
data class HuggingFaceModelInfoSnapshot(
    val author: String?,
    val name: String?,
    val website: String?,
    val logoUrl: String?,
    val pipelineTag: String?,
    val libraryName: String?,
    val quantization: String?,
    val licenseName: String?,
    val lastModified: String?,
    val likes: Long,
    val downloads: Long,
    val parameterCount: Long,
) {
    private fun model() = HuggingFaceModelInfo().also {
        it.author = author
        it.name = name
        it.website = website
        it.logoUrl = logoUrl
        it.pipelineTag = pipelineTag
        it.libraryName = libraryName
        it.quantization = quantization
        it.licenseName = licenseName
        it.lastModified = lastModified
        it.likes = likes
        it.downloads = downloads
        it.parameterCount = parameterCount
    }

    fun formatCapability(): String = model().formatCapability()
    fun formatLikes(): String = model().formatLikes()
    fun formatDownloads(): String = model().formatDownloads()
    fun formatParameters(): String? = model().formatParameters()
    fun formatLicense(): String? = model().formatLicense()
    fun formatUpdated(): String? = model().formatUpdated()
    val shortenedUrl: String? get() = LinkPreviewFormatUtils.shortenUrl(website)
}

@Serializable
data class OpenRouterModelInfoSnapshot(
    val provider: String?,
    val name: String?,
    val website: String?,
    val providerIconUrl: String?,
    val description: String?,
    val promptPricePerToken: String?,
    val completionPricePerToken: String?,
    val contextLength: Long,
    val maxCompletionTokens: Long,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val knowledgeCutoff: String?,
) {
    private fun model() = OpenRouterModelInfo().also {
        it.provider = provider
        it.name = name
        it.website = website
        it.providerIconUrl = providerIconUrl
        it.description = description
        it.promptPricePerToken = promptPricePerToken
        it.completionPricePerToken = completionPricePerToken
        it.contextLength = contextLength
        it.maxCompletionTokens = maxCompletionTokens
        it.inputModalities = inputModalities
        it.outputModalities = outputModalities
        it.knowledgeCutoff = knowledgeCutoff
    }

    fun formatPromptPrice(): String? = model().formatPromptPrice()
    fun formatCompletionPrice(): String? = model().formatCompletionPrice()
    fun formatContext(): String? = model().formatContext()
    fun formatMaxOutput(): String? = model().formatMaxOutput()
    fun formatModalities(): String? = model().formatModalities()
    fun formatKnowledgeCutoff(): String? = model().formatKnowledgeCutoff()
}

@Serializable
data class StackExchangeInfoSnapshot(
    val title: String?,
    val author: String?,
    val questionText: String?,
    val tags: List<String?>,
    val site: String?,
    val score: Int,
    val answerCount: Int,
    val viewCount: Int,
    val isAnswered: Boolean,
    val hasAcceptedAnswer: Boolean,
) {
    fun formatScore(): String = LinkPreviewFormatUtils.formatCount(score, "point", "points")
    fun formatAnswerCount(): String =
        LinkPreviewFormatUtils.formatCount(answerCount, "answer", "answers")
    fun formatViewCount(): String = LinkPreviewFormatUtils.formatCount(viewCount, "view", "views")
    fun formatAnswerState(): String = when {
        hasAcceptedAnswer -> "Accepted answer"
        isAnswered -> "Answered"
        else -> "Unanswered"
    }
    fun formatTags(): String? = tags.takeIf(List<String?>::isNotEmpty)?.joinToString(", ")
    fun formatBy(): String? = questionText ?: author?.let { "$it on $site" } ?: site
    fun formatAuthor(): String? = author ?: site
}

@Serializable
data class ArxivInfoSnapshot(
    val arxivAbstract: String?,
    val authors: List<String?>,
    val primaryCategory: String?,
    val arxivID: String?,
    val secondaryCategories: List<String?>,
    val publishedDate: String?,
) {
    fun concatNames(): String = authors.joinToString(", ")
    fun formatDate(): String = publishedDate.orEmpty().take(10)
    fun formatSubjects(): String = buildString {
        append(ArxivResolver.resolveFull(primaryCategory))
        secondaryCategories.forEach { append("; "); append(ArxivResolver.resolveFull(it)) }
    }
    val pDFURL: String get() = "https://arxiv.org/pdf/$arxivID.pdf"
}

@Serializable data class WikipediaInfoSnapshot(val summary: String?)

@Serializable
data class NitterInfoSnapshot(
    val text: String?,
    val userName: String?,
    val userTag: String?,
    val date: String?,
    val replyCount: String?,
    val reposts: String?,
    val likes: String?,
    val imgSrc: String?,
    val hasVideo: Boolean,
    val beforeUserName: String?,
    val beforeUserTag: String?,
    val beforeText: String?,
    val beforeDate: String?,
    val beforeImgSrc: String?,
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
        loading = previewImageUrlLoading || previewImageLoading,
        loaded = previewImageUrlLoaded && previewImageLoaded,
        failed = previewImageLoadFailed,
    ),
    favicon = ResourceLoadSnapshot(
        url = faviconTintSourceUrl,
        loading = faviconTintColorLoading,
        loaded = faviconTintColorLoaded,
        failed = faviconTintColorLoadFailed,
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
    linkSummaryLoading = linkSummaryLoading,
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
    repoInfo = repoInfo?.let {
        RepoInfoSnapshot(
            it.name, it.owner, it.avatarUrl, it.about, it.website, it.license, it.language,
            it.stars, it.watching, it.forks,
        )
    },
    gitLabInfo = gitLabInfo?.let {
        GitLabInfoSnapshot(
            it.name, it.namespace, it.description, it.website, it.language, it.visibility,
            it.stars, it.forks,
        )
    },
    huggingFaceInfo = huggingFaceInfo?.let {
        HuggingFaceModelInfoSnapshot(
            it.author, it.name, it.website, it.logoUrl, it.pipelineTag, it.libraryName,
            it.quantization, it.licenseName, it.lastModified, it.likes, it.downloads,
            it.parameterCount,
        )
    },
    openRouterInfo = openRouterInfo?.let {
        OpenRouterModelInfoSnapshot(
            it.provider, it.name, it.website, it.providerIconUrl, it.description,
            it.promptPricePerToken, it.completionPricePerToken, it.contextLength,
            it.maxCompletionTokens, it.inputModalities, it.outputModalities,
            it.knowledgeCutoff,
        )
    },
    stackExchangeInfo = stackExchangeInfo?.let {
        StackExchangeInfoSnapshot(
            it.title, it.author, it.questionText, it.tags?.toList().orEmpty(), it.site,
            it.score, it.answerCount, it.viewCount, it.isAnswered, it.hasAcceptedAnswer,
        )
    },
    arxivInfo = arxivInfo?.let {
        ArxivInfoSnapshot(
            it.arxivAbstract, it.authors.toList(), it.primaryCategory, it.arxivID,
            it.secondaryCategories.toList(), it.publishedDate,
        )
    },
    wikiInfo = wikiInfo?.let { WikipediaInfoSnapshot(it.summary) },
    nitterInfo = nitterInfo?.let {
        NitterInfoSnapshot(
            it.text, it.userName, it.userTag, it.date, it.replyCount, it.reposts, it.likes,
            it.imgSrc, it.hasVideo, it.beforeUserName, it.beforeUserTag, it.beforeText,
            it.beforeDate, it.beforeImgSrc,
        )
    },
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
