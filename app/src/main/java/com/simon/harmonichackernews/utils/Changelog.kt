package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.resources.Res

object Changelog {
    private const val CHANGELOG_RESOURCE = "files/changelog.md"
    private const val FALLBACK_CHANGELOG = "Changelog unavailable."
    private var cachedMarkdown: String? = null

    suspend fun getMarkdown(): String {
        cachedMarkdown?.let { return it }
        return runCatching {
            Res.readBytes(CHANGELOG_RESOURCE)
                .decodeToString()
                .removePrefix("\uFEFF")
        }.getOrElse { error ->
            Utils.log("Failed to read changelog: $error")
            FALLBACK_CHANGELOG
        }.also { markdown ->
            cachedMarkdown = markdown
        }
    }
}
