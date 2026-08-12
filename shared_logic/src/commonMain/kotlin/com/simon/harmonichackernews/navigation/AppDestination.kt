package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.applySnapshot
import com.simon.harmonichackernews.data.toSnapshot
import kotlinx.serialization.Serializable

sealed interface AppDestination

/** Stable navigation identity. Story content is hydrated by the destination feature. */
@Serializable
data class StoryRoute(
    val storyId: Int,
    val showWebsite: Boolean = false,
    val scrollToCommentId: Int = -1,
) {
    init {
        require(storyId > 0) { "A positive Hacker News item ID is required" }
    }
}

/** Optional immutable content used for an immediate header while the route hydrates by item ID. */
@Serializable
data class StoryNavigationSeed(
    val story: StorySnapshot,
    val pdfTitle: String? = null,
    val videoTitle: String? = null,
    val isLink: Boolean = false,
    val commentMasterId: Int = 0,
    val commentMasterTitle: String? = null,
    val commentMasterUrl: String? = null,
)

/** A stable route with an optional transient domain seed; no rendering/resource state is routed. */
data class StoryDestination(
    val storyId: Int,
    val showWebsite: Boolean = false,
    val scrollToCommentId: Int = -1,
    val seed: StoryNavigationSeed? = null,
) : AppDestination {
    init {
        require(storyId > 0) { "A positive Hacker News item ID is required" }
    }

    val route: StoryRoute
        get() = StoryRoute(storyId, showWebsite, scrollToCommentId)
}

fun StoryRoute.toDestination(): StoryDestination = StoryDestination(
    storyId = storyId,
    showWebsite = showWebsite,
    scrollToCommentId = scrollToCommentId,
)

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
    showWebsite: Boolean = false,
    scrollToCommentId: Int = -1,
): StoryDestination = StoryDestination(
    storyId = id,
    showWebsite = showWebsite,
    scrollToCommentId = scrollToCommentId,
    seed = StoryNavigationSeed(
        story = toSnapshot(),
        pdfTitle = pdfTitle,
        videoTitle = videoTitle,
        isLink = isLink,
        commentMasterId = commentMasterId,
        commentMasterTitle = commentMasterTitle,
        commentMasterUrl = commentMasterUrl,
    ),
)

/** Rebuilds the mutable compatibility model at the Android rendering boundary. */
fun StoryDestination.toStory(): Story = Story().also { story ->
    story.id = storyId
    seed?.let { initial ->
        story.applySnapshot(initial.story)
        story.pdfTitle = initial.pdfTitle
        story.videoTitle = initial.videoTitle
        story.isLink = initial.isLink
        story.commentMasterId = initial.commentMasterId
        story.commentMasterTitle = initial.commentMasterTitle
        story.commentMasterUrl = initial.commentMasterUrl
        story.loaded = true
    }
}
