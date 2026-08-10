package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface OpenRouterProviderIcon {
    data class RemoteUrl(val url: String) : OpenRouterProviderIcon
    data class SvgBytes(val bytes: ByteArray) : OpenRouterProviderIcon
}

data class OpenRouterProviderIconResult(
    val providerSlug: String,
    val icon: OpenRouterProviderIcon?,
)

interface OpenRouterProviderIconRepository {
    suspend fun resolve(providerSlug: String?): OpenRouterProviderIconResult
}

/**
 * Resolves and caches provider artwork exposed by OpenRouter provider pages.
 * UI-thread delivery and image rendering remain platform responsibilities.
 */
class KtorOpenRouterProviderIconRepository(
    private val client: KtorHttpClient,
) : OpenRouterProviderIconRepository {
    private val requestMutex = Mutex()
    private val cache = mutableMapOf<String, OpenRouterProviderIcon>()

    override suspend fun resolve(providerSlug: String?): OpenRouterProviderIconResult {
        val normalizedSlug = OpenRouterProviderIconParser.normalizeSlug(providerSlug)
        if (normalizedSlug.isEmpty()) return OpenRouterProviderIconResult(normalizedSlug, null)

        val icon = requestMutex.withLock {
            cache[normalizedSlug]?.let { return@withLock it }
            val resolved = resolveRemote(normalizedSlug)
            if (resolved != null) cache[normalizedSlug] = resolved
            delay(REQUEST_SPACING_MS)
            resolved
        }
        return OpenRouterProviderIconResult(normalizedSlug, icon)
    }

    private suspend fun resolveRemote(providerSlug: String): OpenRouterProviderIcon? {
        val providerUrl = OPENROUTER_URL.toNetworkUrl().newBuilder()
            .addPathSegment(providerSlug)
            .build()
            .toString()

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val response = client.execute(HttpRequest.Builder().url(providerUrl).get().build())
                val outcome = try {
                    if (response.isSuccessful) {
                        val iconUrl = OpenRouterProviderIconParser.findIconUrl(
                            response.body.readText(),
                            providerUrl,
                            providerSlug,
                        ) ?: return null
                        if (OpenRouterProviderIconParser.isSvgUrl(iconUrl)) {
                            fetchSvg(iconUrl)
                        } else {
                            OpenRouterProviderIcon.RemoteUrl(iconUrl)
                        }
                    } else if (response.code.isRetryableProviderStatus()) {
                        null
                    } else {
                        return null
                    }
                } finally {
                    response.close()
                }
                if (outcome != null) return outcome
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Provider initials remain visible when OpenRouter changes or rejects the page.
            }
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }
        return null
    }

    private suspend fun fetchSvg(iconUrl: String): OpenRouterProviderIcon {
        return try {
            val response = client.execute(HttpRequest.Builder().url(iconUrl).get().build())
            try {
                if (!response.isSuccessful) return OpenRouterProviderIcon.RemoteUrl(iconUrl)
                OpenRouterProviderIcon.SvgBytes(
                    OpenRouterProviderIconParser.sanitizeSvg(response.body.readBytes()),
                )
            } finally {
                response.close()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            OpenRouterProviderIcon.RemoteUrl(iconUrl)
        }
    }

    private fun Int.isRetryableProviderStatus(): Boolean =
        this == 403 || this == 408 || this == 429 || this >= 500

    private companion object {
        const val OPENROUTER_URL = "https://openrouter.ai"
        const val REQUEST_SPACING_MS = 120L
        const val RETRY_DELAY_MS = 750L
        const val MAX_ATTEMPTS = 3
    }
}

object OpenRouterProviderIconParser {
    private val unsupportedDisplayP3Style = Regex(
        "\\sstyle=(\"[^\"]*color\\(display-p3[^\"]*\"|'[^']*color\\(display-p3[^']*')",
        RegexOption.IGNORE_CASE,
    )

    fun normalizeSlug(providerSlug: String?): String =
        providerSlug.orEmpty().trim().lowercase()

    fun findIconUrl(html: String, baseUri: String, providerSlug: String): String? =
        findIconUrl(Ksoup.parse(html, baseUri = baseUri), providerSlug)

    fun isSvgUrl(iconUrl: String): Boolean = iconUrl.lowercase().contains(".svg")

    fun sanitizeSvg(svg: ByteArray): ByteArray =
        unsupportedDisplayP3Style.replace(svg.decodeToString(), "").encodeToByteArray()

    private fun findIconUrl(page: Document, providerSlug: String): String? {
        val expectedAlt = "Favicon for $providerSlug"
        for (image in page.select("img[alt][src]")) {
            if (!expectedAlt.equals(image.attr("alt").trim(), ignoreCase = true)) continue
            val iconUrl = image.absUrl("src")
            val parsed = iconUrl.toNetworkUrlOrNull() ?: return null
            return parsed.toString().takeIf { parsed.scheme == "https" || parsed.scheme == "http" }
        }
        return null
    }
}
