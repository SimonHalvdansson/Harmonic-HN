package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.data.LinkPreviewFormatUtils.shortenUrl
import kotlin.math.round

class HuggingFaceModelInfo {
    var author: String? = null
    var name: String? = null
    var website: String? = null
    var logoUrl: String? = null
    var pipelineTag: String? = null
    var libraryName: String? = null
    var quantization: String? = null
    var licenseName: String? = null
    var lastModified: String? = null
    var likes: Long = 0
    var downloads: Long = 0
    var parameterCount: Long = 0

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
        val parts = lastModified?.take(10)?.split('-') ?: return null
        if (parts.size != 3) return null
        val month = parts[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return "Updated $month $day"
    }

    val shortenedUrl: String?
        get() = shortenUrl(website)

    private fun String.toPreviewLabel(): String = replaceFirstChar(Char::uppercase)

    private fun formatCount(count: Long, singular: String, plural: String): String =
        if (count == 1L) "1 $singular" else "${compactFormat(count)} $plural"

    private fun compactFormat(number: Long): String {
        val (divisor, suffix) = when {
            number >= 1_000_000_000_000L -> 1_000_000_000_000L to "T"
            number >= 1_000_000_000L -> 1_000_000_000L to "B"
            number >= 1_000_000L -> 1_000_000L to "M"
            number >= 1_000L -> 1_000L to "K"
            else -> return number.toString()
        }
        val scaled = number.toDouble() / divisor
        val factor = when {
            scaled >= 100 -> 1.0
            scaled >= 10 -> 10.0
            else -> 100.0
        }
        val value = (round(scaled * factor) / factor).toString().removeSuffix(".0")
        return "$value$suffix"
    }

    private companion object {
        val MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
    }
}
