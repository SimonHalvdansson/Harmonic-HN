package com.simon.harmonichackernews.data

import android.os.Bundle
import com.simon.harmonichackernews.CommentsContract

/** Android navigation representation kept outside the shared domain model. */
fun Story.toBundle(): Bundle = Bundle().apply {
    putString(CommentsContract.EXTRA_TITLE, title)
    putString(CommentsContract.EXTRA_PDF_TITLE, pdfTitle)
    putString(CommentsContract.EXTRA_VIDEO_TITLE, videoTitle)
    putString(CommentsContract.EXTRA_BY, by)
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
    putInt(CommentsContract.EXTRA_TIME, time)
    putIntArray(CommentsContract.EXTRA_KIDS, kids)
    putIntArray(CommentsContract.EXTRA_POLL_OPTIONS, pollOptions)
    putInt(CommentsContract.EXTRA_DESCENDANTS, descendants)
    putInt(CommentsContract.EXTRA_ID, id)
    putInt(CommentsContract.EXTRA_SCORE, score)
    putString(CommentsContract.EXTRA_TEXT, text)
    putBoolean(CommentsContract.EXTRA_IS_LINK, isLink)
    putBoolean(CommentsContract.EXTRA_IS_COMMENT, isComment)
    putInt(CommentsContract.EXTRA_PARENT_ID, parentId)
    putInt(CommentsContract.EXTRA_COMMENT_MASTER_ID, commentMasterId)
    putString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE, commentMasterTitle)
    putString(CommentsContract.EXTRA_COMMENT_MASTER_URL, commentMasterUrl)
}
