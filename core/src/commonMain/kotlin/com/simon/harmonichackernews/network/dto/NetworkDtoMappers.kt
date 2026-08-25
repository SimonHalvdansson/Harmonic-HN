package com.simon.harmonichackernews.network.dto

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryTextProcessor
import com.simon.harmonichackernews.utils.HackerNewsLinks

fun HackerNewsItemDto.toStory(): Story? = Story().takeIf { story -> applyTo(story) }

fun AlgoliaSearchHitDto.toStory(): Story? {
    val id = objectId.toIntOrNull() ?: return null
    val isComment = tags.firstOrNull() == "comment"
    val author = author ?: return null
    val itemUrl = url?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) }
    val masterUrl = storyUrl?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) }
    return Story().also { story ->
        story.id = id
        story.by = author
        story.title = (if (isComment) storyTitle else title)
            ?.takeUnless { it == "null" }
            ?: if (isComment) "Comment by $author" else null
        story.text = StoryTextProcessor.preprocessHtml(
            if (isComment) commentText.orEmpty() else storyText,
        )
        story.url = itemUrl ?: HackerNewsLinks.itemUrl(id)
        story.score = points ?: 0
        story.descendants = commentCount ?: 0
        story.time = createdAt ?: 0
        story.parentId = parentId ?: 0
        story.isLink = if (isComment) masterUrl != null else itemUrl != null
        story.isComment = isComment
        if (isComment && (storyId ?: 0) > 0) {
            story.commentMasterId = storyId ?: 0
            story.commentMasterTitle = storyTitle
            story.commentMasterUrl = masterUrl
            story.commentMasterLoaded = false
        }
        story.loaded = true
        story.loadingFailed = false
        StoryTextProcessor.applyTitleBadges(story)
    }
}

fun HackerNewsItemDto.applyTo(story: Story, preserveTime: Boolean = false): Boolean {
    val author = by ?: return false
    if (id <= 0 || deleted) return false

    story.by = author
    story.id = id
    story.score = score
    if (!preserveTime) story.time = time
    story.title = title
    story.isComment = type == "comment"
    story.descendants = if (story.isComment) kids.size else descendants
    story.parentId = parent
    story.isJob = type == "job"
    story.pollOptions = parts.takeIf(List<Int>::isNotEmpty)?.toIntArray()
    story.kids = kids.takeIf(List<Int>::isNotEmpty)?.toIntArray()

    if (story.isComment && story.title.isNullOrEmpty()) {
        story.title = "Comment by $author"
    } else if (story.title.isNullOrEmpty() && dead) {
        story.title = "[deleted]"
    }

    val linkUrl = url?.takeUnless(String::isBlank)
    story.url = linkUrl ?: HackerNewsLinks.itemUrl(id)
    story.isLink = linkUrl != null
    text?.let { story.text = StoryTextProcessor.preprocessHtml(it) }
    StoryTextProcessor.applyTitleBadges(story)
    story.loaded = true
    story.loadingFailed = false
    return true
}

fun HackerNewsItemDto.toComment(): Comment? {
    if (deleted || id <= 0) return null
    return Comment().also { comment ->
        comment.id = id
        comment.by = by.orEmpty()
        comment.time = time
        comment.parent = parent
        comment.expanded = true
        comment.text = StoryTextProcessor.preprocessHtml(text).orEmpty()
        comment.children = kids.size
        comment.kidsIds = kids.takeIf(List<Int>::isNotEmpty)?.toIntArray()
    }
}
