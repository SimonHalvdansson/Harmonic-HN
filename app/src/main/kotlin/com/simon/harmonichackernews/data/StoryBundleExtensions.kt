package com.simon.harmonichackernews.data

import android.os.Bundle
import com.simon.harmonichackernews.CommentsIntentExtras
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.StoryNavigationSeed
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract

/** Android persistence/intent encoding for the shared navigation model. */
fun StoryDestination.toBundle(): Bundle = Bundle().apply {
    putString(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA, AppDestinationCodec.encode(this@toBundle))
    putInt(CommentsIntentExtras.EXTRA_ID, storyId)
    seed?.let { initial ->
        val story = initial.story
        putString(CommentsIntentExtras.EXTRA_TITLE, story.title)
        putString(CommentsIntentExtras.EXTRA_PDF_TITLE, initial.pdfTitle)
        putString(CommentsIntentExtras.EXTRA_VIDEO_TITLE, initial.videoTitle)
        putString(CommentsIntentExtras.EXTRA_BY, story.author)
        putString(CommentsIntentExtras.EXTRA_URL, story.url)
        putInt(CommentsIntentExtras.EXTRA_TIME, story.createdAtEpochSeconds)
        putIntArray(CommentsIntentExtras.EXTRA_KIDS, story.childIds.toIntArray())
        putIntArray(CommentsIntentExtras.EXTRA_POLL_OPTIONS, story.pollOptionIds.toIntArray())
        putInt(CommentsIntentExtras.EXTRA_DESCENDANTS, story.descendantCount)
        putInt(CommentsIntentExtras.EXTRA_SCORE, story.score)
        putString(CommentsIntentExtras.EXTRA_TEXT, story.text)
        putBoolean(CommentsIntentExtras.EXTRA_IS_LINK, initial.isLink)
        putBoolean(CommentsIntentExtras.EXTRA_IS_COMMENT, story.isComment)
        putInt(CommentsIntentExtras.EXTRA_PARENT_ID, story.parentId)
        putInt(CommentsIntentExtras.EXTRA_COMMENT_MASTER_ID, initial.rootStoryId)
        putString(CommentsIntentExtras.EXTRA_COMMENT_MASTER_TITLE, initial.rootStoryTitle)
        putString(CommentsIntentExtras.EXTRA_COMMENT_MASTER_URL, initial.rootStoryUrl)
    }
    putBoolean(CommentsIntentExtras.EXTRA_SHOW_WEBSITE, showWebsite)
    if (scrollToCommentId > 0) {
        putInt(CommentsIntentExtras.EXTRA_SCROLL_TO_COMMENT, scrollToCommentId)
    }
}

fun Story.toBundle(): Bundle = toDestination().toBundle()

fun Bundle.toStoryDestinationOrNull(): StoryDestination? {
    (AppDestinationCodec.decode(getString(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA))
        as? StoryDestination)?.let { return it }
    val storyId = getInt(CommentsIntentExtras.EXTRA_ID, -1)
    if (storyId <= 0) return null
    val hasSeed = containsKey(CommentsIntentExtras.EXTRA_TITLE) ||
        containsKey(CommentsIntentExtras.EXTRA_BY) ||
        containsKey(CommentsIntentExtras.EXTRA_URL) ||
        containsKey(CommentsIntentExtras.EXTRA_TEXT) ||
        containsKey(CommentsIntentExtras.EXTRA_KIDS) ||
        containsKey(CommentsIntentExtras.EXTRA_POLL_OPTIONS)
    return StoryDestination(
        storyId = storyId,
        showWebsite = getBoolean(CommentsIntentExtras.EXTRA_SHOW_WEBSITE),
        scrollToCommentId = getInt(CommentsIntentExtras.EXTRA_SCROLL_TO_COMMENT, -1),
        seed = if (hasSeed) {
            StoryNavigationSeed(
                story = StorySnapshot(
                    id = storyId,
                    author = getString(CommentsIntentExtras.EXTRA_BY),
                    title = getString(CommentsIntentExtras.EXTRA_TITLE),
                    text = getString(CommentsIntentExtras.EXTRA_TEXT),
                    url = getString(CommentsIntentExtras.EXTRA_URL),
                    score = getInt(CommentsIntentExtras.EXTRA_SCORE),
                    descendantCount = getInt(CommentsIntentExtras.EXTRA_DESCENDANTS),
                    createdAtEpochSeconds = getInt(CommentsIntentExtras.EXTRA_TIME),
                    childIds = getIntArray(CommentsIntentExtras.EXTRA_KIDS)?.toList().orEmpty(),
                    pollOptionIds = getIntArray(CommentsIntentExtras.EXTRA_POLL_OPTIONS)
                        ?.toList().orEmpty(),
                    isComment = getBoolean(CommentsIntentExtras.EXTRA_IS_COMMENT),
                    parentId = getInt(CommentsIntentExtras.EXTRA_PARENT_ID),
                ),
                pdfTitle = getString(CommentsIntentExtras.EXTRA_PDF_TITLE),
                videoTitle = getString(CommentsIntentExtras.EXTRA_VIDEO_TITLE),
                isLink = getBoolean(CommentsIntentExtras.EXTRA_IS_LINK),
                rootStoryId = getInt(CommentsIntentExtras.EXTRA_COMMENT_MASTER_ID),
                rootStoryTitle = getString(CommentsIntentExtras.EXTRA_COMMENT_MASTER_TITLE),
                rootStoryUrl = getString(CommentsIntentExtras.EXTRA_COMMENT_MASTER_URL),
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
