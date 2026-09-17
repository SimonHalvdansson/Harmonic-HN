package com.simon.harmonichackernews.data

import kotlinx.serialization.Serializable

@Serializable
data class GitLabInfo(
    val name: String? = null,
    val namespace: String? = null,
    val description: String? = null,
    val website: String? = null,
    val language: String? = null,
    val visibility: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
) {
    fun formatStars(): String = LinkPreviewFormatUtils.formatCount(stars, "star", "stars")
    fun formatForks(): String = LinkPreviewFormatUtils.formatCount(forks, "fork", "forks")
    fun formatVisibility(): String? = visibility?.replaceFirstChar { it.uppercase() }
    val shortenedUrl: String? get() = LinkPreviewFormatUtils.shortenUrl(website)
}
