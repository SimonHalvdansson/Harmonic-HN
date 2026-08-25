package com.simon.harmonichackernews.data

class GitLabInfo {
    var name: String? = null
    var namespace: String? = null
    var description: String? = null
    var website: String? = null
    var language: String? = null
    var visibility: String? = null
    var stars: Int = 0
    var forks: Int = 0

    fun formatStars(): String = LinkPreviewFormatUtils.formatCount(stars, "star", "stars")

    fun formatForks(): String = LinkPreviewFormatUtils.formatCount(forks, "fork", "forks")

    fun formatVisibility(): String? {
        val currentVisibility = visibility ?: return null

        return currentVisibility.replaceFirstChar { it.uppercase() }
    }

    val shortenedUrl: String?
        get() = LinkPreviewFormatUtils.shortenUrl(website)
}
