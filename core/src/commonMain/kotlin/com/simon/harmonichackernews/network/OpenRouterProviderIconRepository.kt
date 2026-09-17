package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
class KtorOpenRouterProviderIconRepository private constructor(
    private val client: KtorHttpClient?,
    private val producerScope: CoroutineScope,
    private val remoteResolver: (suspend (String) -> OpenRouterProviderIcon?)?,
) : OpenRouterProviderIconRepository {
    constructor(client: KtorHttpClient, producerScope: CoroutineScope) : this(
        client = client,
        producerScope = producerScope,
        remoteResolver = null,
    )

    internal constructor(
        producerScope: CoroutineScope,
        remoteResolver: suspend (String) -> OpenRouterProviderIcon?,
    ) : this(client = null, producerScope = producerScope, remoteResolver = remoteResolver)

    private val stateMutex = Mutex()
    private val requestStartMutex = Mutex()
    private val requestSlots = Semaphore(MAX_CONCURRENT_RESOLUTIONS)
    private val cache = mutableMapOf<String, CacheEntry>()
    private val cacheOrder = ArrayDeque<String>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<OpenRouterProviderIcon?>>()
    private var hasStartedRequest = false

    override suspend fun resolve(providerSlug: String?): OpenRouterProviderIconResult {
        val normalizedSlug = OpenRouterProviderIconParser.normalizeSlug(providerSlug)
        if (normalizedSlug.isEmpty()) return OpenRouterProviderIconResult(normalizedSlug, null)

        val lookup = stateMutex.withLock {
            cachedLocked(normalizedSlug)?.let { return@withLock Lookup.Cached(it.icon) }
            inFlight[normalizedSlug]?.let { return@withLock Lookup.Pending(it, start = false) }
            val deferred = CompletableDeferred<OpenRouterProviderIcon?>()
            inFlight[normalizedSlug] = deferred
            Lookup.Pending(deferred, start = true)
        }
        val icon = when (lookup) {
            is Lookup.Cached -> lookup.icon
            is Lookup.Pending -> {
                if (lookup.start) {
                    producerScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        produceResolution(normalizedSlug, lookup.deferred)
                    }
                }
                lookup.deferred.await()
            }
        }
        return OpenRouterProviderIconResult(normalizedSlug, icon)
    }

    private suspend fun produceResolution(
        providerSlug: String,
        deferred: CompletableDeferred<OpenRouterProviderIcon?>,
    ) {
        try {
            val resolved = requestSlots.withPermit {
                remoteResolver?.invoke(providerSlug) ?: resolveRemote(providerSlug)
            }
            stateMutex.withLock {
                rememberLocked(providerSlug, resolved)
                if (inFlight[providerSlug] === deferred) inFlight.remove(providerSlug)
            }
            deferred.complete(resolved)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                stateMutex.withLock {
                    if (inFlight[providerSlug] === deferred) inFlight.remove(providerSlug)
                }
                deferred.cancel(error)
            }
            throw error
        } catch (_: Throwable) {
            stateMutex.withLock {
                rememberLocked(providerSlug, null)
                if (inFlight[providerSlug] === deferred) inFlight.remove(providerSlug)
            }
            deferred.complete(null)
        }
    }

    private fun cachedLocked(providerSlug: String): CacheEntry? {
        val entry = cache[providerSlug] ?: return null
        if (entry.icon == null && entry.savedAt.elapsedNow() >= NEGATIVE_CACHE_TTL) {
            cache.remove(providerSlug)
            cacheOrder.remove(providerSlug)
            return null
        }
        cacheOrder.remove(providerSlug)
        cacheOrder.addLast(providerSlug)
        return entry
    }

    private fun rememberLocked(providerSlug: String, icon: OpenRouterProviderIcon?) {
        cache[providerSlug] = CacheEntry(icon, TimeSource.Monotonic.markNow())
        cacheOrder.remove(providerSlug)
        cacheOrder.addLast(providerSlug)
        while (cacheOrder.size > MAX_CACHE_ENTRIES) cache.remove(cacheOrder.removeFirst())
    }

    private suspend fun resolveRemote(providerSlug: String): OpenRouterProviderIcon? {
        val httpClient = checkNotNull(client)
        val providerUrl = OPENROUTER_URL.toNetworkUrl().newBuilder()
            .addPathSegment(providerSlug)
            .build()
            .toString()

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val response = executeRateLimited(
                    httpClient,
                    HttpRequest.Builder().url(providerUrl).get().build(),
                )
                val outcome = try {
                    if (response.isSuccessful) {
                        if (!response.hasContentType("text/html", "application/xhtml+xml")) {
                            return null
                        }
                        val iconUrl = OpenRouterProviderIconParser.findIconUrl(
                            response.body.readText(MAX_PROVIDER_PAGE_BYTES),
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
        val httpClient = checkNotNull(client)
        return try {
            val response = executeRateLimited(
                httpClient,
                HttpRequest.Builder().url(iconUrl).get().build(),
            )
            try {
                if (!response.isSuccessful) return OpenRouterProviderIcon.RemoteUrl(iconUrl)
                if (!response.hasContentType("image/svg+xml", "application/xml", "text/xml")) {
                    return OpenRouterProviderIcon.RemoteUrl(iconUrl)
                }
                OpenRouterProviderIcon.SvgBytes(
                    OpenRouterProviderIconParser.sanitizeSvg(
                        response.body.readBytes(MAX_SVG_BYTES),
                    ),
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

    private suspend fun executeRateLimited(
        client: KtorHttpClient,
        request: HttpRequest,
    ): HttpResponse {
        requestStartMutex.withLock {
            if (hasStartedRequest) delay(REQUEST_SPACING_MS)
            hasStartedRequest = true
        }
        return client.execute(request)
    }

    private fun HttpResponse.hasContentType(vararg allowed: String): Boolean {
        val value = body.contentType()?.toString()?.substringBefore(';')?.trim()?.lowercase()
            ?: return true
        return allowed.any(value::equals)
    }

    private fun Int.isRetryableProviderStatus(): Boolean =
        this == 403 || this == 408 || this == 429 || this >= 500

    private data class CacheEntry(
        val icon: OpenRouterProviderIcon?,
        val savedAt: TimeMark,
    )

    private sealed interface Lookup {
        data class Cached(val icon: OpenRouterProviderIcon?) : Lookup
        data class Pending(
            val deferred: CompletableDeferred<OpenRouterProviderIcon?>,
            val start: Boolean,
        ) : Lookup
    }

    private companion object {
        const val OPENROUTER_URL = "https://openrouter.ai"
        const val REQUEST_SPACING_MS = 120L
        const val RETRY_DELAY_MS = 750L
        const val MAX_ATTEMPTS = 3
        const val MAX_CONCURRENT_RESOLUTIONS = 3
        const val MAX_CACHE_ENTRIES = 64
        const val MAX_PROVIDER_PAGE_BYTES = 2 * 1024 * 1024
        const val MAX_SVG_BYTES = 512 * 1024
        val NEGATIVE_CACHE_TTL = 5.minutes
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
