package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.data.LinkPreviewFormatUtils.formatCount
import com.simon.harmonichackernews.data.LinkPreviewFormatUtils.kFormat
import com.simon.harmonichackernews.data.LinkPreviewFormatUtils.shortenUrl

class RepoInfo {
    var name: String? = null
    var owner: String? = null
    var avatarUrl: String? = null
    var about: String? = null
    var website: String? = null
    var license: String? = null
    var language: String? = null
    var stars: Int = 0
    var watching: Int = 0
    var forks: Int = 0

    fun formatStars(): String = formatCount(stars, "star", "stars")

    fun formatWatching(): String = "${kFormat(watching)} watching"

    fun formatForks(): String = formatCount(forks, "fork", "forks")

    val shortenedUrl: String?
        get() = shortenUrl(website)
}
