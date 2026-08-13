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
    val previewImage: ResourceLoadSnapshot = ResourceLoadSnapshot(),
    val favicon: ResourceLoadSnapshot = ResourceLoadSnapshot(),
    val summary: String? = null,
    val summaryGeneratedSuccessfully: Boolean = false,
)

@Serializable
data class ResourceLoadSnapshot(
    val url: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failed: Boolean = false,
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
    summary = summary,
    summaryGeneratedSuccessfully = summaryGeneratedSuccessfully,
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
