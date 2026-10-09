package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Shared by all GitHub previews in the network graph, including reference-link previews. */
internal class GitHubPreviewLoader(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    // Serialize API attempts so queued previews observe a rate limit before sending another call.
    private val mutex = Mutex()
    private var apiBlockedUntil = 0L
    private val cache = linkedMapOf<GitHubPreviewTarget, LinkPreviewData>()

    suspend fun load(client: HttpClient, type: LinkPreviewType, url: String): LinkPreviewData = mutex.withLock {
        val target = GitHubLinkPreview.githubTarget(url)?.takeIf { it.type == type }
            ?: throw LinkPreviewException("Invalid ${type.title} URL")
        if (nowMillis() >= apiBlockedUntil) {
            try {
                val preview = if (type == LinkPreviewType.GITHUB_REPOSITORY) {
                    LinkPreviewData.GitHub(client.loadGitHubInfo(url))
                } else {
                    LinkPreviewData.Rich(client.loadGitHubPreview(type, url))
                }
                remember(target, preview)
                return@withLock preview
            } catch (error: HttpStatusException) {
                val until = error.gitHubRateLimitDeadline(nowMillis()) ?: throw error
                apiBlockedUntil = maxOf(apiBlockedUntil, until)
            }
        }

        cache.remove(target)?.let { cached ->
            cache[target] = cached
            return@withLock cached
        }
        val page = GitHubLinkPreview.parseGitHubPage(type, client.getTextOrThrow(url), target, url)
        val preview = if (type == LinkPreviewType.GITHUB_REPOSITORY) {
            LinkPreviewData.GitHub(RepoInfo(
                name = target.repository,
                owner = target.owner,
                about = page.description,
                pageTitle = page.title,
                imageUrl = page.imageUrl,
            ))
        } else {
            LinkPreviewData.Rich(page)
        }
        remember(target, preview)
        preview
    }

    private fun remember(target: GitHubPreviewTarget, preview: LinkPreviewData) {
        cache.remove(target)
        cache[target] = preview
        if (cache.size > 100) cache.remove(cache.keys.first())
    }
}

private fun HttpStatusException.gitHubRateLimitDeadline(now: Long): Long? {
    if (statusCode != 403 && statusCode != 429) return null
    val retryAfter = headers[HttpHeaders.RetryAfter]?.trim()
    val remaining = headers["x-ratelimit-remaining"]?.trim()
    val message = runCatching { JsonObject(responseBody.orEmpty()).optString("message") }
        .getOrDefault("").lowercase()
    val retryDeadline = retryAfter?.toLongOrNull()?.takeIf { it >= 0 && it <= (Long.MAX_VALUE - now) / 1_000 }
        ?.let { now + it * 1_000 }
        ?: retryAfter?.let { runCatching { it.fromHttpToGmtDate().timestamp }.getOrNull() }
    val confirmed = statusCode == 429 || remaining == "0" || retryDeadline != null ||
        "api rate limit exceeded" in message || "secondary rate limit" in message
    if (!confirmed) return null
    val reset = headers["x-ratelimit-reset"]?.trim()?.toLongOrNull()
        ?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1_000 }?.times(1_000)
    // Missing/expired timing still needs a cooldown; never immediately retry a confirmed limit.
    return listOfNotNull(retryDeadline, reset).filter { it > now }.maxOrNull() ?: (now + 60_000)
}
