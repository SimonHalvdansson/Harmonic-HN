package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.LinkPreviewRepository
import io.ktor.http.encodeURLParameter

/** Resolves every archive destination without Android URI or callback dependencies. */
class ArchiveUrlResolver(
    private val repository: LinkPreviewRepository,
) {
    suspend fun resolve(provider: ArchiveProvider, articleUrl: String): String {
        require(articleUrl.isNotBlank()) { "Missing URL" }
        return when (provider) {
            ArchiveProvider.ORG -> repository.getArchiveUrl(articleUrl)
            ArchiveProvider.IS -> "https://archive.is/newest/" +
                articleUrl.encodeURLParameter(spaceToPlus = false)
            ArchiveProvider.TODAY -> "https://archive.today/newest/" +
                articleUrl.encodeURLParameter(spaceToPlus = false)
            ArchiveProvider.PH -> "https://archive.ph/newest/" +
                articleUrl.encodeURLParameter(spaceToPlus = false)
        }
    }
}
