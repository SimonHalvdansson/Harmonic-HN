package com.simon.harmonichackernews.presentation

enum class CommentsBackTarget {
    LINK_PREVIEW,
    COMMENT_ACTION,
    CUSTOM_WEB_CONTENT,
    READER_MODE,
    CLOSE_WEBSITE,
    WEB_HISTORY,
    NONE,
}

data class CommentsBackContext(
    val hostActive: Boolean = true,
    val linkPreviewVisible: Boolean,
    val commentActionVisible: Boolean,
    val customWebContentVisible: Boolean,
    val readerModeEnabled: Boolean,
    val websiteVisible: Boolean,
    val webHistoryAvailable: Boolean,
    val closeWebsiteOnBack: Boolean,
)

/** Back priority shared by native comments hosts, including predictive-back eligibility. */
object CommentsBackPolicy {
    fun target(context: CommentsBackContext): CommentsBackTarget = when {
        !context.hostActive -> CommentsBackTarget.NONE
        context.linkPreviewVisible -> CommentsBackTarget.LINK_PREVIEW
        context.commentActionVisible -> CommentsBackTarget.COMMENT_ACTION
        context.customWebContentVisible -> CommentsBackTarget.CUSTOM_WEB_CONTENT
        context.websiteVisible && context.readerModeEnabled -> CommentsBackTarget.READER_MODE
        context.websiteVisible && !context.webHistoryAvailable && context.closeWebsiteOnBack ->
            CommentsBackTarget.CLOSE_WEBSITE
        context.websiteVisible && context.webHistoryAvailable -> CommentsBackTarget.WEB_HISTORY
        else -> CommentsBackTarget.NONE
    }
}

sealed interface CommentsOverlayRestoration {
    data class Reference(val url: String, val fallbackTitle: String?) : CommentsOverlayRestoration
    data class Image(val url: String) : CommentsOverlayRestoration
}

data class CommentsHostRestoration(
    val sorting: String? = null,
    val commentActionId: Int = -1,
    val adBlockDisabled: Boolean = false,
    val overlay: CommentsOverlayRestoration? = null,
)
