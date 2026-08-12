package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.data.ArticleSnapshotPolicy
import com.simon.harmonichackernews.network.CachedDownloadService
import com.simon.harmonichackernews.network.DownloadCachePolicy
import com.simon.harmonichackernews.network.HttpMediaType
import com.simon.harmonichackernews.network.KtorTransferClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Android persistence adapter for shared bounded article-snapshot transfers. */
class ArticleSnapshotDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val service = CachedDownloadService(
        client = KtorTransferClient(AndroidAppComposition.get(appContext).network.httpClient),
        store = AndroidDownloadStore(
            root = Utils.getArticleCacheDir(appContext),
            fileNameForKey = { storyId -> "$storyId$HTML_FILE_SUFFIX" },
            targetSuffix = HTML_FILE_SUFFIX,
            onCommit = { storyId, metadata ->
                storyId.toIntOrNull()?.let { numericStoryId ->
                    AndroidStoryCacheRepositories.get(appContext).recordArticleMetadata(
                        storyId = numericStoryId,
                        sourceUrl = metadata.sourceUrl,
                        contentType = metadata.contentType,
                    )
                }
            },
            onRemove = { file -> removeMetadataFor(file) },
        ),
        policy = DownloadCachePolicy(
            maxFileBytes = ArticleSnapshotPolicy.MAX_BYTES,
            maxCacheBytes = MAX_ARTICLE_CACHE_BYTES,
            maxTemporaryAgeMillis = MAX_TEMP_FILE_AGE_MS,
        ),
        cacheLabel = "Article HTML",
    )

    suspend fun download(storyId: Int, articleUrl: String): Boolean = try {
        if (storyId <= 0 || articleUrl.isBlank()) return false
        service.download(
            url = articleUrl,
            accept = "text/html,application/xhtml+xml",
            key = storyId.toString(),
            nowMillis = System.currentTimeMillis(),
            reuseExisting = false,
            acceptsContentType = ::isHtml,
        )
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }

    private fun removeMetadataFor(file: File) {
        val storyId = file.name
            .takeIf { it.endsWith(HTML_FILE_SUFFIX) }
            ?.removeSuffix(HTML_FILE_SUFFIX)
            ?.toIntOrNull()
            ?: return
        AndroidStoryCacheRepositories.get(appContext).removeArticleMetadata(storyId)
    }

    private companion object {
        val MAX_ARTICLE_CACHE_BYTES = 200L * 1024L * 1024L
        val MAX_TEMP_FILE_AGE_MS = TimeUnit.DAYS.toMillis(1)
        const val HTML_FILE_SUFFIX = ".html"

        fun isHtml(contentType: HttpMediaType?): Boolean {
            if (contentType == null) return false
            val type = contentType.type.lowercase()
            val subtype = contentType.subtype.lowercase()
            return (type == "text" && subtype == "html") ||
                (type == "application" && subtype == "xhtml+xml")
        }
    }
}
