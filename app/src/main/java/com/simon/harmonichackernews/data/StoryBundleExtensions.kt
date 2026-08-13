package com.simon.harmonichackernews.data

import android.os.Bundle
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.StoryNavigationSeed
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract

/** Android persistence/intent encoding for the shared navigation model. */
fun StoryDestination.toBundle(): Bundle = Bundle().apply {
    putString(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA, AppDestinationCodec.encode(this@toBundle))
    putInt(CommentsContract.EXTRA_ID, storyId)
    seed?.let { initial ->
        val story = initial.story
        putString(CommentsContract.EXTRA_TITLE, story.title)
        putString(CommentsContract.EXTRA_PDF_TITLE, initial.pdfTitle)
        putString(CommentsContract.EXTRA_VIDEO_TITLE, initial.videoTitle)
        putString(CommentsContract.EXTRA_BY, story.author)
        putString(CommentsContract.EXTRA_URL, story.url)
        putInt(CommentsContract.EXTRA_TIME, story.createdAtEpochSeconds)
        putIntArray(CommentsContract.EXTRA_KIDS, story.childIds.toIntArray())
        putIntArray(CommentsContract.EXTRA_POLL_OPTIONS, story.pollOptionIds.toIntArray())
        putInt(CommentsContract.EXTRA_DESCENDANTS, story.descendantCount)
        putInt(CommentsContract.EXTRA_SCORE, story.score)
        putString(CommentsContract.EXTRA_TEXT, story.text)
        putBoolean(CommentsContract.EXTRA_IS_LINK, initial.isLink)
        putBoolean(CommentsContract.EXTRA_IS_COMMENT, story.isComment)
        putInt(CommentsContract.EXTRA_PARENT_ID, story.parentId)
        putInt(CommentsContract.EXTRA_COMMENT_MASTER_ID, initial.commentMasterId)
        putString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE, initial.commentMasterTitle)
        putString(CommentsContract.EXTRA_COMMENT_MASTER_URL, initial.commentMasterUrl)
    }
    putBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, showWebsite)
    if (scrollToCommentId > 0) {
        putInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, scrollToCommentId)
    }
}

fun Story.toBundle(): Bundle = toDestination().toBundle()

fun Bundle.toStoryDestinationOrNull(): StoryDestination? {
    (AppDestinationCodec.decode(getString(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA))
        as? StoryDestination)?.let { return it }
    val storyId = getInt(CommentsContract.EXTRA_ID, -1)
    if (storyId <= 0) return null
    val hasSeed = containsKey(CommentsContract.EXTRA_TITLE) ||
        containsKey(CommentsContract.EXTRA_BY) ||
        containsKey(CommentsContract.EXTRA_URL) ||
        containsKey(CommentsContract.EXTRA_TEXT) ||
        containsKey(CommentsContract.EXTRA_KIDS) ||
        containsKey(CommentsContract.EXTRA_POLL_OPTIONS)
    return StoryDestination(
        storyId = storyId,
        showWebsite = getBoolean(CommentsContract.EXTRA_SHOW_WEBSITE),
        scrollToCommentId = getInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, -1),
        seed = if (hasSeed) {
            StoryNavigationSeed(
                story = StorySnapshot(
                    id = storyId,
                    author = getString(CommentsContract.EXTRA_BY),
                    title = getString(CommentsContract.EXTRA_TITLE),
                    text = getString(CommentsContract.EXTRA_TEXT),
                    url = getString(CommentsContract.EXTRA_URL),
                    score = getInt(CommentsContract.EXTRA_SCORE),
                    descendantCount = getInt(CommentsContract.EXTRA_DESCENDANTS),
                    createdAtEpochSeconds = getInt(CommentsContract.EXTRA_TIME),
                    childIds = getIntArray(CommentsContract.EXTRA_KIDS)?.toList().orEmpty(),
                    pollOptionIds = getIntArray(CommentsContract.EXTRA_POLL_OPTIONS)
                        ?.toList().orEmpty(),
                    isComment = getBoolean(CommentsContract.EXTRA_IS_COMMENT),
                    parentId = getInt(CommentsContract.EXTRA_PARENT_ID),
                ),
                pdfTitle = getString(CommentsContract.EXTRA_PDF_TITLE),
                videoTitle = getString(CommentsContract.EXTRA_VIDEO_TITLE),
                isLink = getBoolean(CommentsContract.EXTRA_IS_LINK),
                commentMasterId = getInt(CommentsContract.EXTRA_COMMENT_MASTER_ID),
                commentMasterTitle = getString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE),
                commentMasterUrl = getString(CommentsContract.EXTRA_COMMENT_MASTER_URL),
            )
        } else {
            null
        },
    )
}

fun EditorDestination.toBundle(): Bundle = Bundle().apply {
    putString(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA, AppDestinationCodec.encode(this@toBundle))
    putInt(ComposeEditorContract.EXTRA_ID, itemId)
    putInt(
        ComposeEditorContract.EXTRA_TYPE,
        when (type) {
            EditorType.TOP_LEVEL_COMMENT -> ComposeEditorContract.TYPE_TOP_COMMENT
            EditorType.COMMENT_REPLY -> ComposeEditorContract.TYPE_COMMENT_REPLY
            EditorType.POST -> ComposeEditorContract.TYPE_POST
        },
    )
    putString(ComposeEditorContract.EXTRA_PARENT_TEXT, parentText)
    putString(ComposeEditorContract.EXTRA_POST_TITLE, postTitle)
    putString(ComposeEditorContract.EXTRA_USER, userName)
}

fun Bundle.toEditorDestination(): EditorDestination =
    (AppDestinationCodec.decode(getString(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA))
        as? EditorDestination) ?: EditorDestination(
    itemId = getInt(ComposeEditorContract.EXTRA_ID, -1),
    type = when (getInt(ComposeEditorContract.EXTRA_TYPE, ComposeEditorContract.TYPE_POST)) {
        ComposeEditorContract.TYPE_TOP_COMMENT -> EditorType.TOP_LEVEL_COMMENT
        ComposeEditorContract.TYPE_COMMENT_REPLY -> EditorType.COMMENT_REPLY
        else -> EditorType.POST
    },
    parentText = getString(ComposeEditorContract.EXTRA_PARENT_TEXT),
    postTitle = getString(ComposeEditorContract.EXTRA_POST_TITLE),
    userName = getString(ComposeEditorContract.EXTRA_USER),
    )
