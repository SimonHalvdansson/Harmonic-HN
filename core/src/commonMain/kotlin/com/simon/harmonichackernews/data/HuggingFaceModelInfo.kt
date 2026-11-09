package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.data.LinkPreviewDisplayFormatter.formatDisplayUrl
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HuggingFaceModelInfo(
    val author: String? = null,
    val name: String? = null,
    val website: String? = null,
    val logoUrl: String? = null,
    val pipelineTag: String? = null,
    val libraryName: String? = null,
    val quantization: String? = null,
    val licenseName: String? = null,
    val lastModified: String? = null,
    val likes: Long = 0,
    val downloads: Long = 0,
    val parameterCount: Long = 0,
) {
    fun formatCapability(): String = listOfNotNull(
        pipelineTag?.toPreviewLabel(),
        libraryName?.toPreviewLabel(),
        quantization,
    ).joinToString(" · ")

    fun formatLikes(): String = formatCount(likes, "like", "likes")

    fun formatDownloads(): String = formatCount(downloads, "download", "downloads")

    fun formatParameters(): String? = parameterCount
        .takeIf { it > 0 }
        ?.let { formatCount(it, "parameter", "parameters") }

    fun formatLicense(): String? = licenseName
        ?.split('-')
        ?.joinToString("-") { it.replaceFirstChar(Char::uppercase) }
        ?.let { "$it license" }

    fun formatUpdated(): String? {
        val date = lastModified?.take(10) ?: return null
        return runCatching { Instant.parse("${date}T00:00:00Z") }.getOrNull()?.let { "Updated: $date" }
    }

    val shortenedUrl: String?
        get() = formatDisplayUrl(website)

    private fun String.toPreviewLabel(): String = replaceFirstChar(Char::uppercase)

    private fun formatCount(count: Long, singular: String, plural: String): String =
        if (count == 1L) "1 $singular" else "${LinkPreviewDisplayFormatter.formatCompactCount(count)} $plural"
}
