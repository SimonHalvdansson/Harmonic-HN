package com.simon.harmonichackernews.data

import android.os.Bundle
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract

/** Android persistence/intent encoding for the shared navigation model. */
fun StoryDestination.toBundle(): Bundle = Bundle().apply {
    putString(CommentsContract.EXTRA_TITLE, title)
    putString(CommentsContract.EXTRA_PDF_TITLE, pdfTitle)
    putString(CommentsContract.EXTRA_VIDEO_TITLE, videoTitle)
    putString(CommentsContract.EXTRA_BY, author)
    putString(CommentsContract.EXTRA_URL, url)
    putString(CommentsContract.EXTRA_PREVIEW_IMAGE_URL, previewImageUrl)
    putBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_URL_LOADED, previewImageUrlLoaded)
    putBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_LOAD_FAILED, previewImageLoadFailed)
    putBoolean(
        CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED,
        previewImageTintColorLoaded,
    )
    putInt(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR, previewImageTintColor)
    putString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL, previewImageTintSourceUrl)
    putInt(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR, previewImageTintBaseColor)
    putString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_MODE, previewImageTintMode)
    putBoolean(CommentsContract.EXTRA_FAVICON_TINT_COLOR_LOADED, faviconTintColorLoaded)
    putInt(CommentsContract.EXTRA_FAVICON_TINT_COLOR, faviconTintColor)
    putString(CommentsContract.EXTRA_FAVICON_TINT_SOURCE_URL, faviconTintSourceUrl)
    putInt(CommentsContract.EXTRA_FAVICON_TINT_BASE_COLOR, faviconTintBaseColor)
    putString(CommentsContract.EXTRA_FAVICON_TINT_MODE, faviconTintMode)
    putInt(CommentsContract.EXTRA_TIME, createdAtEpochSeconds)
    putIntArray(CommentsContract.EXTRA_KIDS, childIds.toIntArray())
    putIntArray(CommentsContract.EXTRA_POLL_OPTIONS, pollOptionIds.toIntArray())
    putInt(CommentsContract.EXTRA_DESCENDANTS, descendantCount)
    putInt(CommentsContract.EXTRA_ID, storyId)
    putInt(CommentsContract.EXTRA_SCORE, score)
    putString(CommentsContract.EXTRA_TEXT, text)
    putBoolean(CommentsContract.EXTRA_IS_LINK, isLink)
    putBoolean(CommentsContract.EXTRA_IS_COMMENT, isComment)
    putInt(CommentsContract.EXTRA_PARENT_ID, parentId)
    putInt(CommentsContract.EXTRA_COMMENT_MASTER_ID, commentMasterId)
    putString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE, commentMasterTitle)
    putString(CommentsContract.EXTRA_COMMENT_MASTER_URL, commentMasterUrl)
    putInt(CommentsContract.EXTRA_FORWARD, relativePosition)
    putBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, showWebsite)
    if (scrollToCommentId > 0) {
        putInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, scrollToCommentId)
    }
}

fun Story.toBundle(): Bundle = toDestination().toBundle()

fun Bundle.toStoryDestinationOrNull(): StoryDestination? {
    val storyId = getInt(CommentsContract.EXTRA_ID, -1)
    if (storyId <= 0) return null
    return StoryDestination(
        storyId = storyId,
        title = getString(CommentsContract.EXTRA_TITLE),
        pdfTitle = getString(CommentsContract.EXTRA_PDF_TITLE),
        videoTitle = getString(CommentsContract.EXTRA_VIDEO_TITLE),
        author = getString(CommentsContract.EXTRA_BY),
        url = getString(CommentsContract.EXTRA_URL),
        previewImageUrl = getString(CommentsContract.EXTRA_PREVIEW_IMAGE_URL),
        previewImageUrlLoaded = getBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_URL_LOADED),
        previewImageLoadFailed = getBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_LOAD_FAILED),
        previewImageTintColorLoaded =
            getBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED),
        previewImageTintColor = getInt(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR),
        previewImageTintSourceUrl =
            getString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL),
        previewImageTintBaseColor = getInt(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR),
        previewImageTintMode = getString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_MODE),
        faviconTintColorLoaded = getBoolean(CommentsContract.EXTRA_FAVICON_TINT_COLOR_LOADED),
        faviconTintColor = getInt(CommentsContract.EXTRA_FAVICON_TINT_COLOR),
        faviconTintSourceUrl = getString(CommentsContract.EXTRA_FAVICON_TINT_SOURCE_URL),
        faviconTintBaseColor = getInt(CommentsContract.EXTRA_FAVICON_TINT_BASE_COLOR),
        faviconTintMode = getString(CommentsContract.EXTRA_FAVICON_TINT_MODE),
        createdAtEpochSeconds = getInt(CommentsContract.EXTRA_TIME),
        childIds = getIntArray(CommentsContract.EXTRA_KIDS)?.toList().orEmpty(),
        pollOptionIds = getIntArray(CommentsContract.EXTRA_POLL_OPTIONS)?.toList().orEmpty(),
        descendantCount = getInt(CommentsContract.EXTRA_DESCENDANTS),
        score = getInt(CommentsContract.EXTRA_SCORE),
        text = getString(CommentsContract.EXTRA_TEXT),
        isLink = getBoolean(CommentsContract.EXTRA_IS_LINK),
        isComment = getBoolean(CommentsContract.EXTRA_IS_COMMENT),
        parentId = getInt(CommentsContract.EXTRA_PARENT_ID),
        commentMasterId = getInt(CommentsContract.EXTRA_COMMENT_MASTER_ID),
        commentMasterTitle = getString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE),
        commentMasterUrl = getString(CommentsContract.EXTRA_COMMENT_MASTER_URL),
        relativePosition = getInt(CommentsContract.EXTRA_FORWARD),
        showWebsite = getBoolean(CommentsContract.EXTRA_SHOW_WEBSITE),
        scrollToCommentId = getInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, -1),
    )
}

fun EditorDestination.toBundle(): Bundle = Bundle().apply {
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

fun Bundle.toEditorDestination(): EditorDestination = EditorDestination(
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
