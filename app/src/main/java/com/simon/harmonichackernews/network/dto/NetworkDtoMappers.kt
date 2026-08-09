package com.simon.harmonichackernews.network.dto

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser

private const val HN_ITEM_URL = "https://news.ycombinator.com/item?id="

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
    story.url = linkUrl ?: "$HN_ITEM_URL$id"
    story.isLink = linkUrl != null
    text?.let { JSONParser.updateStoryText(story, it) }
    JSONParser.updateTitleBadgeProperties(story)
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
        comment.text = JSONParser.preprocessHtml(text).orEmpty()
        comment.children = kids.size
        comment.kidsIds = kids.takeIf(List<Int>::isNotEmpty)?.toIntArray()
    }
}

fun AlgoliaSearchHitDto.toStory(): Story? {
    val id = objectId.toIntOrNull() ?: return null
    val isComment = tags.firstOrNull() == "comment"
    val author = author ?: return null
    val displayTitle = if (isComment) storyTitle else title
    val itemUrl = url?.trim()?.takeUnless {
        it.isEmpty() || it.equals("null", ignoreCase = true)
    }
    val masterUrl = storyUrl?.trim()?.takeUnless {
        it.isEmpty() || it.equals("null", ignoreCase = true)
    }

    return Story().also { story ->
        story.id = id
        story.title = displayTitle
        story.score = points ?: 0
        story.by = author
        story.descendants = commentCount ?: 0
        story.time = createdAt ?: 0
        story.loaded = true
        story.loadingFailed = false
        story.clicked = false
        story.url = itemUrl ?: "$HN_ITEM_URL$id"
        story.isLink = itemUrl != null
        storyText?.takeUnless { it.equals("null", ignoreCase = true) }
            ?.let { JSONParser.updateStoryText(story, it) }

        if (isComment) {
            story.isComment = true
            JSONParser.updateStoryText(story, commentText.orEmpty())
            story.commentMasterTitle = storyTitle
            story.commentMasterId = storyId ?: 0
            story.parentId = parentId ?: 0
            story.commentMasterUrl = masterUrl
            story.isLink = masterUrl != null
            if (story.title.isNullOrEmpty() || story.title == "null") {
                story.title = "Comment by $author"
            }
        }
        JSONParser.updateTitleBadgeProperties(story)
    }
}
