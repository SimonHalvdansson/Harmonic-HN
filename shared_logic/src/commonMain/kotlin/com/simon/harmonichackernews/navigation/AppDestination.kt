package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.data.Story

sealed interface AppDestination

/** Platform-neutral request to display an HN item and its optional article surface. */
data class StoryDestination(
    val storyId: Int,
    val title: String? = null,
    val pdfTitle: String? = null,
    val videoTitle: String? = null,
    val author: String? = null,
    val url: String? = null,
    val previewImageUrl: String? = null,
    val previewImageUrlLoaded: Boolean = false,
    val previewImageLoadFailed: Boolean = false,
    val previewImageTintColorLoaded: Boolean = false,
    val previewImageTintColor: Int = 0,
    val previewImageTintSourceUrl: String? = null,
    val previewImageTintBaseColor: Int = 0,
    val previewImageTintMode: String? = null,
    val faviconTintColorLoaded: Boolean = false,
    val faviconTintColor: Int = 0,
    val faviconTintSourceUrl: String? = null,
    val faviconTintBaseColor: Int = 0,
    val faviconTintMode: String? = null,
    val createdAtEpochSeconds: Int = 0,
    val childIds: List<Int> = emptyList(),
    val pollOptionIds: List<Int> = emptyList(),
    val descendantCount: Int = 0,
    val score: Int = 0,
    val text: String? = null,
    val isLink: Boolean = false,
    val isComment: Boolean = false,
    val parentId: Int = 0,
    val commentMasterId: Int = 0,
    val commentMasterTitle: String? = null,
    val commentMasterUrl: String? = null,
    val relativePosition: Int = 0,
    val showWebsite: Boolean = false,
    val scrollToCommentId: Int = -1,
) : AppDestination {
    init {
        require(storyId > 0) { "A positive Hacker News item ID is required" }
    }
}

enum class EditorType {
    POST,
    TOP_LEVEL_COMMENT,
    COMMENT_REPLY,
}

data class EditorDestination(
    val type: EditorType = EditorType.POST,
    val itemId: Int = -1,
    val parentText: String? = null,
    val postTitle: String? = null,
    val userName: String? = null,
) : AppDestination {
    val isValid: Boolean
        get() = type == EditorType.POST || itemId > 0
}

data class SubmissionsDestination(val userName: String) : AppDestination {
    init {
        require(userName.isNotBlank()) { "A username is required" }
    }
}

fun Story.toDestination(
    relativePosition: Int = 0,
    showWebsite: Boolean = false,
    scrollToCommentId: Int = -1,
): StoryDestination = StoryDestination(
    storyId = id,
    title = title,
    pdfTitle = pdfTitle,
    videoTitle = videoTitle,
    author = by,
    url = url,
    previewImageUrl = previewImageUrl,
    previewImageUrlLoaded = previewImageUrlLoaded,
    previewImageLoadFailed = previewImageLoadFailed,
    previewImageTintColorLoaded = previewImageTintColorLoaded,
    previewImageTintColor = previewImageTintColor,
    previewImageTintSourceUrl = previewImageTintSourceUrl,
    previewImageTintBaseColor = previewImageTintBaseColor,
    previewImageTintMode = previewImageTintMode,
    faviconTintColorLoaded = faviconTintColorLoaded,
    faviconTintColor = faviconTintColor,
    faviconTintSourceUrl = faviconTintSourceUrl,
    faviconTintBaseColor = faviconTintBaseColor,
    faviconTintMode = faviconTintMode,
    createdAtEpochSeconds = time,
    childIds = kids?.toList().orEmpty(),
    pollOptionIds = pollOptions?.toList().orEmpty(),
    descendantCount = descendants,
    score = score,
    text = text,
    isLink = isLink,
    isComment = isComment,
    parentId = parentId,
    commentMasterId = commentMasterId,
    commentMasterTitle = commentMasterTitle,
    commentMasterUrl = commentMasterUrl,
    relativePosition = relativePosition,
    showWebsite = showWebsite,
    scrollToCommentId = scrollToCommentId,
)
