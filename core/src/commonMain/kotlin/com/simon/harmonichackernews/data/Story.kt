package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.DomainNamePolicy
import com.simon.harmonichackernews.utils.HackerNewsLinks

class Story : LinkPreviewState {
    var by: String? = null
    var descendants: Int = 0
    var id: Int = 0
    var score: Int = 0
    var createdAtEpochSeconds: Int = 0
    var title: String? = null
    var pdfTitle: String? = null
    var videoTitle: String? = null
    var url: String? = null

    private var cachedDomainUrl: String? = null

    private var cachedDomainName: String? = null

    private var cachedDomainNameWithoutTopLevelDomain: String? = null

    var previewImageUrl: String? = null

    var previewImageUrlResolved: Boolean = false

    var linkSummaryDescription: String? = null

    var linkSummaryLoaded: Boolean = false

    var previewImageTintColor: Int = 0

    var previewImageTintColorLoaded: Boolean = false

    var previewImageTintSourceUrl: String? = null

    var previewImageTintBaseColor: Int = 0

    var previewImageTintMode: String? = null

    var faviconTintColor: Int = 0

    var faviconTintColorLoaded: Boolean = false

    var faviconTintSourceUrl: String? = null

    var faviconTintBaseColor: Int = 0

    var faviconTintMode: String? = null
    var kids: IntArray? = null
    var pollOptionIds: IntArray? = null
    var pollOptions: ArrayList<PollOption>? = null
    var loaded: Boolean = false
    var isRead: Boolean = false
    var text: String? = null

    override var gitLabInfo: GitLabInfo? = null
    override var huggingFaceInfo: HuggingFaceModelInfo? = null
    override var openRouterInfo: OpenRouterModelInfo? = null
    override var gitHubRepoInfo: RepoInfo? = null
    override var stackExchangeInfo: StackExchangeInfo? = null
    override var arxivInfo: ArxivInfo? = null
    override var wikiInfo: WikipediaInfo? = null
    override var nitterInfo: NitterInfo? = null
    override var linkPreviewInfo: LinkPreviewInfo? = null

    var linkPreviewLoading: Boolean = false

    var isLink: Boolean = false
    var isJob: Boolean = false
    var loadingFailed: Boolean = false
    var isComment: Boolean = false
    var isFrontpageLink: Boolean = false
    var rootStoryTitle: String? = null
    var rootStoryId: Int = 0
    var rootStoryUrl: String? = null
    var rootStoryAuthor: String? = null
    var rootStoryScore: Int = 0
    var rootStoryCreatedAtEpochSeconds: Int = 0
    var rootStoryDescendantCount: Int = 0
    var rootStoryLoaded: Boolean = false
    var parentId: Int = 0 // Direct parent ID (for comments)
    var aiSummaryText: String? = null

    var summaryGeneratedSuccessfully: Boolean = false

    constructor()

    constructor(title: String, id: Int, loaded: Boolean, isRead: Boolean) {
        this.title = title
        this.id = id
        this.loaded = loaded
        this.isRead = isRead
    }

    constructor(title: String, id: Int, loaded: Boolean, isRead: Boolean, createdAtEpochMillis: Long) {
        this.title = title
        this.id = id
        this.loaded = loaded
        this.isRead = isRead
        this.createdAtEpochSeconds = (createdAtEpochMillis / 1000).toInt()
    }

    fun update(by: String?, id: Int, score: Int, createdAtEpochSeconds: Int, title: String) {
        this.by = by
        this.id = id
        this.score = score
        this.createdAtEpochSeconds = createdAtEpochSeconds
        this.title = title
    }

    val timeFormatted: String
        get() = ItemTimeFormatter.formatNow(createdAtEpochSeconds)

    fun formatTime(nowMillis: Long): String = ItemTimeFormatter.format(createdAtEpochSeconds, nowMillis)

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

    fun toRootStory(): Story? {
        val targetId = if (rootStoryId > 0) rootStoryId else parentId
        if (targetId <= 0) {
            return null
        }

        val rootStory = Story()
        rootStory.id = targetId
        rootStory.title = if (hasText(rootStoryTitle)) rootStoryTitle else title
        val hasRootStoryUrl = hasText(rootStoryUrl)
        val resolvedRootStoryUrl = if (hasRootStoryUrl)
            rootStoryUrl
        else
            HackerNewsLinks.itemUrl(targetId)
        rootStory.url = resolvedRootStoryUrl
        rootStory.isLink = hasRootStoryUrl &&
            !HackerNewsLinks.isItemUrl(resolvedRootStoryUrl)
        rootStory.by = rootStoryAuthor
        rootStory.score = rootStoryScore
        rootStory.createdAtEpochSeconds = rootStoryCreatedAtEpochSeconds
        rootStory.descendants = rootStoryDescendantCount
        rootStory.loaded = rootStoryLoaded && hasText(rootStoryAuthor)
        return rootStory
    }

    fun updateRootStoryFrom(root: Story): Boolean {
        if (root.id <= 0 || root.isComment) return false
        rootStoryId = root.id
        rootStoryTitle = root.title
        rootStoryAuthor = root.by
        rootStoryScore = root.score
        rootStoryCreatedAtEpochSeconds = root.createdAtEpochSeconds
        rootStoryDescendantCount = root.descendants
        rootStoryUrl = root.url
        rootStoryLoaded = root.loaded
        return true
    }

    fun hasLoadedLinkPreview(): Boolean = loadedLinkPreviewType() != null

    companion object {
        private fun hasText(value: String?): Boolean {
            return !value.isNullOrEmpty()
        }

    }
}
