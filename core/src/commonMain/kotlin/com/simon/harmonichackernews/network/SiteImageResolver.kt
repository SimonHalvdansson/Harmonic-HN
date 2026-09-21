package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** A site's article data can supply an image missing from its initial HTML. */
internal interface SiteImageResolver {
    fun requestUrl(pageUrl: NetworkUrl): String?
    fun extractImage(response: String, pageUrl: NetworkUrl): String?
}

internal object SiteImageResolvers {
    // Add providers here; ordinary pages never make an additional request.
    private val providers: List<SiteImageResolver> = listOf(QwenImageResolver)

    suspend fun resolve(
        pageUrl: String,
        fetchJson: suspend (String) -> String,
    ): String? {
        val page = pageUrl.toNetworkUrlOrNull() ?: return null
        for (provider in providers) {
            val requestUrl = provider.requestUrl(page) ?: continue
            val image = try {
                withTimeoutOrNull(5_000) {
                    provider.extractImage(fetchJson(requestUrl), page)
                        ?.let(LinkSummaryParser::normalizeHttpUrl)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Provider outages or format changes must not prevent the HTML preview.
                null
            }
            if (image != null) return image
        }
        return null
    }
}
