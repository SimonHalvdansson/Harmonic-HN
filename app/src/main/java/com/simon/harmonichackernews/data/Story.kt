package com.simon.harmonichackernews.data

import android.os.Bundle
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.utils.Utils
class Story {
    var by: String? = null
    var descendants: Int = 0
    var id: Int = 0
    var score: Int = 0
    var time: Int = 0
    var title: String? = null
    var pdfTitle: String? = null
    var videoTitle: String? = null
    var url: String? = null

    @Transient
    private var cachedDomainUrl: String? = null

    @Transient
    private var cachedDomainName: String? = null

    @Transient
    private var cachedDomainNameWithoutTopLevelDomain: String? = null

    @Transient
    var previewImageUrl: String? = null

    @Transient
    var previewImageUrlLoaded: Boolean = false

    @Transient
    var previewImageUrlLoading: Boolean = false

    @Transient
    var previewImageLoaded: Boolean = false

    @Transient
    var previewImageLoading: Boolean = false

    @Transient
    var previewImageLoadFailed: Boolean = false

    @Transient
    var linkSummaryDescription: String? = null

    @Transient
    var linkSummaryLoaded: Boolean = false

    @Transient
    var linkSummaryLoading: Boolean = false

    @Transient
    var previewImageTintColor: Int = 0

    @Transient
    var previewImageTintColorLoaded: Boolean = false

    @Transient
    var previewImageTintSourceUrl: String? = null

    @Transient
    var previewImageTintBaseColor: Int = 0

    @Transient
    var previewImageTintMode: String? = null

    @Transient
    var faviconTintColor: Int = 0

    @Transient
    var faviconTintColorLoaded: Boolean = false

    @Transient
    var faviconTintColorLoading: Boolean = false

    @Transient
    var faviconTintColorLoadFailed: Boolean = false

    @Transient
    var faviconTintSourceUrl: String? = null

    @Transient
    var faviconTintBaseColor: Int = 0

    @Transient
    var faviconTintMode: String? = null
    var kids: IntArray? = null
    var pollOptions: IntArray? = null
    var pollOptionArrayList: ArrayList<PollOption>? = null
    var loaded: Boolean = false
    var clicked: Boolean = false
    var text: String? = null

    var gitLabInfo: GitLabInfo? = null
    var repoInfo: RepoInfo? = null
    var stackExchangeInfo: StackExchangeInfo? = null
    var arxivInfo: ArxivInfo? = null
    var wikiInfo: WikipediaInfo? = null
    var nitterInfo: NitterInfo? = null

    @Transient
    var linkPreviewLoading: Boolean = false

    var isLink: Boolean = false
    var isJob: Boolean = false
    var loadingFailed: Boolean = false
    var isComment: Boolean = false
    var isFrontpageLink: Boolean = false
    var commentMasterTitle: String? = null
    var commentMasterId: Int = 0
    var commentMasterUrl: String? = null
    var commentMasterBy: String? = null
    var commentMasterScore: Int = 0
    var commentMasterTime: Int = 0
    var commentMasterDescendants: Int = 0
    var commentMasterLoaded: Boolean = false
    var parentId: Int = 0 // Direct parent ID (for comments)
    var summary: String? = null

    @Transient
    var summaryGeneratedSuccessfully: Boolean = false

    constructor()

    constructor(title: String, id: Int, loaded: Boolean, clicked: Boolean) {
        this.title = title
        this.id = id
        this.loaded = loaded
        this.clicked = clicked
    }

    constructor(title: String, id: Int, loaded: Boolean, clicked: Boolean, time: Long) {
        this.title = title
        this.id = id
        this.loaded = loaded
        this.clicked = clicked
        this.time = (time / 1000).toInt()
    }

    fun update(by: String?, id: Int, score: Int, time: Int, title: String) {
        this.by = by
        this.id = id
        this.score = score
        this.time = time
        this.title = title
    }

    val timeFormatted: String
        get() = Utils.getTimeAgo(this.time.toLong())

    @Throws(Exception::class)
    fun getDisplayDomain(includeTopLevelDomain: Boolean): String? {
        val currentUrl = url
        if (currentUrl != null && currentUrl == cachedDomainUrl) {
            if (includeTopLevelDomain) {
                return cachedDomainName
            }
            if (cachedDomainNameWithoutTopLevelDomain == null) {
                cachedDomainNameWithoutTopLevelDomain =
                    Utils.formatDomainNameForDisplay(cachedDomainName, false)
            }
            return cachedDomainNameWithoutTopLevelDomain
        }

        if (currentUrl == null) return null
        val domainName = Utils.getDomainName(currentUrl)
        cachedDomainName = domainName
        cachedDomainNameWithoutTopLevelDomain = null
        cachedDomainUrl = currentUrl
        if (includeTopLevelDomain) {
            return domainName
        }
        cachedDomainNameWithoutTopLevelDomain =
            Utils.formatDomainNameForDisplay(domainName, false)
        return cachedDomainNameWithoutTopLevelDomain
    }

    override fun toString(): String {
        return title.orEmpty()
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(CommentsContract.EXTRA_TITLE, title)
        bundle.putString(CommentsContract.EXTRA_PDF_TITLE, pdfTitle)
        bundle.putString(CommentsContract.EXTRA_VIDEO_TITLE, videoTitle)
        bundle.putString(CommentsContract.EXTRA_BY, by)
        bundle.putString(CommentsContract.EXTRA_URL, url)
        bundle.putString(CommentsContract.EXTRA_PREVIEW_IMAGE_URL, previewImageUrl)
        bundle.putBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_URL_LOADED, previewImageUrlLoaded)
        bundle.putBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_LOAD_FAILED, previewImageLoadFailed)
        bundle.putBoolean(
            CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED,
            previewImageTintColorLoaded
        )
        bundle.putInt(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR, previewImageTintColor)
        bundle.putString(
            CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL,
            previewImageTintSourceUrl
        )
        bundle.putInt(
            CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR,
            previewImageTintBaseColor
        )
        bundle.putString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_MODE, previewImageTintMode)
        bundle.putBoolean(CommentsContract.EXTRA_FAVICON_TINT_COLOR_LOADED, faviconTintColorLoaded)
        bundle.putInt(CommentsContract.EXTRA_FAVICON_TINT_COLOR, faviconTintColor)
        bundle.putString(CommentsContract.EXTRA_FAVICON_TINT_SOURCE_URL, faviconTintSourceUrl)
        bundle.putInt(CommentsContract.EXTRA_FAVICON_TINT_BASE_COLOR, faviconTintBaseColor)
        bundle.putString(CommentsContract.EXTRA_FAVICON_TINT_MODE, faviconTintMode)
        bundle.putInt(CommentsContract.EXTRA_TIME, time)
        bundle.putIntArray(CommentsContract.EXTRA_KIDS, kids)
        bundle.putIntArray(CommentsContract.EXTRA_POLL_OPTIONS, pollOptions)
        bundle.putInt(CommentsContract.EXTRA_DESCENDANTS, descendants)
        bundle.putInt(CommentsContract.EXTRA_ID, id)
        bundle.putInt(CommentsContract.EXTRA_SCORE, score)
        bundle.putString(CommentsContract.EXTRA_TEXT, text)
        bundle.putBoolean(CommentsContract.EXTRA_IS_LINK, isLink)
        bundle.putBoolean(CommentsContract.EXTRA_IS_COMMENT, isComment)
        bundle.putInt(CommentsContract.EXTRA_PARENT_ID, parentId)
        bundle.putInt(CommentsContract.EXTRA_COMMENT_MASTER_ID, commentMasterId)
        bundle.putString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE, commentMasterTitle)
        bundle.putString(CommentsContract.EXTRA_COMMENT_MASTER_URL, commentMasterUrl)

        return bundle
    }

    fun toCommentMasterStory(): Story? {
        val targetId = if (commentMasterId > 0) commentMasterId else parentId
        if (targetId <= 0) {
            return null
        }

        val masterStory = Story()
        masterStory.id = targetId
        masterStory.title = if (hasText(commentMasterTitle)) commentMasterTitle else title
        val hasMasterUrl = hasText(commentMasterUrl)
        val masterUrl = if (hasMasterUrl)
            commentMasterUrl
        else
            "https://news.ycombinator.com/item?id=" + targetId
        masterStory.url = masterUrl
        masterStory.isLink = hasMasterUrl &&
            !masterUrl.orEmpty().startsWith("https://news.ycombinator.com/item?id=")
        masterStory.by = commentMasterBy
        masterStory.score = commentMasterScore
        masterStory.time = commentMasterTime
        masterStory.descendants = commentMasterDescendants
        masterStory.loaded = commentMasterLoaded && hasText(commentMasterBy)
        return masterStory
    }

    fun hasExtraInfo(): Boolean = linkPreviewLoading || hasLoadedLinkPreview()

    fun hasLoadedLinkPreview(): Boolean {
        return arxivInfo != null || gitLabInfo != null || repoInfo != null || stackExchangeInfo != null || wikiInfo != null || nitterInfo != null
    }

    companion object {
        private fun hasText(value: String?): Boolean {
            return !value.isNullOrEmpty()
        }
    }
}
