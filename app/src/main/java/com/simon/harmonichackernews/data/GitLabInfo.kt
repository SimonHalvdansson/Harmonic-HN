package com.simon.harmonichackernews.data

import java.util.Locale

class GitLabInfo {
    var name: String? = null
    var namespace: String? = null
    var description: String? = null
    var website: String? = null
    var language: String? = null
    var visibility: String? = null
    var stars: Int = 0
    var forks: Int = 0

    fun formatStars(): String {
        return LinkPreviewFormatUtils.formatCount(stars, "star", "stars")
    }

    fun formatForks(): String {
        return LinkPreviewFormatUtils.formatCount(forks, "fork", "forks")
    }

    fun formatVisibility(): String? {
        if (visibility == null) {
            return null
        }

        return visibility!!.substring(0, 1).uppercase(Locale.getDefault()) + visibility!!.substring(
            1
        )
    }

    val shortenedUrl: String?
        get() = LinkPreviewFormatUtils.shortenUrl(website)
}
