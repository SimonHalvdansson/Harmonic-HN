package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.DomainNamePolicy

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

    private var cachedDomainUrl: String? = null

    private var cachedDomainName: String? = null

    private var cachedDomainNameWithoutTopLevelDomain: String? = null

    var previewImageUrl: String? = null

    var previewImageUrlLoaded: Boolean = false

    var previewImageUrlLoading: Boolean = false

    var previewImageLoaded: Boolean = false

    var previewImageLoading: Boolean = false

    var previewImageLoadFailed: Boolean = false

    var linkSummaryDescription: String? = null

    var linkSummaryLoaded: Boolean = false

    var linkSummaryLoading: Boolean = false

    var previewImageTintColor: Int = 0

    var previewImageTintColorLoaded: Boolean = false

    var previewImageTintSourceUrl: String? = null

    var previewImageTintBaseColor: Int = 0

    var previewImageTintMode: String? = null

    var faviconTintColor: Int = 0

    var faviconTintColorLoaded: Boolean = false

    var faviconTintColorLoading: Boolean = false

    var faviconTintColorLoadFailed: Boolean = false

    var faviconTintSourceUrl: String? = null

    var faviconTintBaseColor: Int = 0

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
        get() = ItemTimeFormatter.formatNow(time)

    fun formatTime(nowMillis: Long): String = ItemTimeFormatter.format(time, nowMillis)

    @Throws(Exception::class)
    fun getDisplayDomain(includeTopLevelDomain: Boolean): String? {
        val currentUrl = url
        if (currentUrl != null && currentUrl == cachedDomainUrl) {
            if (includeTopLevelDomain) {
                return cachedDomainName
            }
            if (cachedDomainNameWithoutTopLevelDomain == null) {
                cachedDomainNameWithoutTopLevelDomain =
                    DomainNamePolicy.formatForDisplay(cachedDomainName, false)
            }
            return cachedDomainNameWithoutTopLevelDomain
        }

        if (currentUrl == null) return null
        val domainName = requireNotNull(DomainNamePolicy.fromUrl(currentUrl)) {
            "Invalid story URL: $currentUrl"
        }
        cachedDomainName = domainName
        cachedDomainNameWithoutTopLevelDomain = null
        cachedDomainUrl = currentUrl
        if (includeTopLevelDomain) {
            return domainName
        }
        cachedDomainNameWithoutTopLevelDomain =
            DomainNamePolicy.formatForDisplay(domainName, false)
        return cachedDomainNameWithoutTopLevelDomain
    }

    override fun toString(): String {
        return title.orEmpty()
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

    fun updateCommentMasterFrom(master: Story): Boolean {
        if (master.id <= 0 || master.isComment) return false
        commentMasterId = master.id
        commentMasterTitle = master.title
        commentMasterBy = master.by
        commentMasterScore = master.score
        commentMasterTime = master.time
        commentMasterDescendants = master.descendants
        commentMasterUrl = master.url
        commentMasterLoaded = master.loaded
        return true
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
