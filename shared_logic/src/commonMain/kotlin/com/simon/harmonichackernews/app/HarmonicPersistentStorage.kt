package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.FileStoryCacheStore
import com.simon.harmonichackernews.data.KeyValueStoryCacheMetadataStore
import com.simon.harmonichackernews.data.StoryCacheKeys
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.network.FileDownloadStore
import com.simon.harmonichackernews.network.StableHash
import com.simon.harmonichackernews.platform.FileAccessTimeStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.time.Clock
import kotlinx.io.files.Path

/** Native directory choices used to construct the portable persistent-storage graph. */
data class HarmonicStorageRoots(
    val files: Path,
    val pdfCache: Path,
)

/** Repositories and download stores whose implementations are shared by every production host. */
data class HarmonicPersistentStorage(
    val storyCacheRepository: StoryCacheRepository,
    val articleSnapshotStore: DownloadStore,
    val pdfDownloadStore: DownloadStore,
)

/**
 * Constructs the canonical file-backed storage graph.
 *
 * Platforms choose durable files/cache directories and provide their key-value adapters. File
 * layout, metadata callbacks, stable PDF names, access times and clocks stay identical across
 * Android, iOS and desktop.
 */
object HarmonicPersistentStorageFactory {
    fun create(
        roots: HarmonicStorageRoots,
        appDataStore: KeyValueStore,
        fileAccessStore: KeyValueStore,
        nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): HarmonicPersistentStorage {
        val accessTimes = FileAccessTimeStore(fileAccessStore)
        val storyCache = StoryCacheRepository(
            files = FileStoryCacheStore(roots.files, accessTimes),
            metadata = KeyValueStoryCacheMetadataStore(appDataStore),
        )
        return HarmonicPersistentStorage(
            storyCacheRepository = storyCache,
            articleSnapshotStore = FileDownloadStore(
                root = Path(roots.files, StoryCacheKeys.ARTICLE_NAMESPACE),
                fileNameForKey = { storyId -> StoryCacheKeys.articleFile(storyId.toInt()) },
                targetSuffix = ".html",
                accessTimes = accessTimes,
                nowMillis = nowMillis,
                onCommit = { storyId, metadata ->
                    storyCache.recordArticleMetadata(
                        storyId = storyId.toInt(),
                        sourceUrl = metadata.sourceUrl,
                        contentType = metadata.contentType,
                    )
                },
                onRemove = { reference ->
                    Path(reference).name.removeSuffix(".html").toIntOrNull()?.let(
                        storyCache::removeArticleMetadata,
                    )
                },
            ),
            pdfDownloadStore = FileDownloadStore(
                root = roots.pdfCache,
                fileNameForKey = { url -> StableHash.sha256Hex(url) + ".pdf" },
                targetSuffix = ".pdf",
                accessTimes = accessTimes,
                nowMillis = nowMillis,
            ),
        )
    }
}
