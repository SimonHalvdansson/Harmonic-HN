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
    val stars: Int? = null,
    val watching: Int? = null,
    val forks: Int? = null,
    val pageTitle: String? = null,
    val imageUrl: String? = null,
) {
    fun formatStars(): String? = stars?.let { LinkPreviewDisplayFormatter.formatCount(it, "star", "stars") }
    fun formatWatching(): String? = watching?.let { "${LinkPreviewDisplayFormatter.formatCompactCount(it)} watching" }
    fun formatForks(): String? = forks?.let { LinkPreviewDisplayFormatter.formatCount(it, "fork", "forks") }
    val shortenedUrl: String? get() = LinkPreviewDisplayFormatter.formatDisplayUrl(website)
}
