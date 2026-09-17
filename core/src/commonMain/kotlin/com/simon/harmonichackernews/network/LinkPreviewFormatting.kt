package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.http.encodeURLPathPart

internal fun details(
    vararg values: Pair<String, String?>,
    displayText: Map<String, String> = emptyMap(),
): List<LinkPreviewDetail> = values
    .mapNotNull { (label, value) ->
        value?.trim()?.takeIf { it.isNotEmpty() && it != "0" }?.let {
            LinkPreviewDetail(label, it, displayText[label])
        }
    }

internal fun String.requiredPreviewTitle(type: LinkPreviewType): String =
    takeIf(String::isNotBlank) ?: throw LinkPreviewException("${type.title} data not found")

internal fun String.dateOnly(): String = take(10)

internal fun String.titleCase(): String = humanize().replaceFirstChar(Char::uppercase)

internal fun String.humanize(): String = replace('_', ' ')

internal fun JsonObject.nonBlankString(key: String): String? =
    (opt(key) as? String)?.takeUnless(String::isBlank)

internal fun apiPath(vararg parts: String): String =
    parts.joinToString("/") { it.encodeURLPathPart() }
