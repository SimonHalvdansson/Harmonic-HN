package com.simon.harmonichackernews.data

import kotlinx.serialization.Serializable

@Serializable
data class RepoInfo(
    val name: String? = null,
    val owner: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val website: String? = null,
    val license: String? = null,
    val language: String? = null,
    val stars: Int = 0,
    val watching: Int = 0,
    val forks: Int = 0,
) {
    fun formatStars(): String = LinkPreviewFormatUtils.formatCount(stars, "star", "stars")
    fun formatWatching(): String = "${LinkPreviewFormatUtils.kFormat(watching)} watching"
    fun formatForks(): String = LinkPreviewFormatUtils.formatCount(forks, "fork", "forks")
    val shortenedUrl: String? get() = LinkPreviewFormatUtils.shortenUrl(website)
}
