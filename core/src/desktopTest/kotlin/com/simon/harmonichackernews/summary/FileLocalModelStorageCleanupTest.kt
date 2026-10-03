package com.simon.harmonichackernews.summary

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.files.Path

class FileLocalModelStorageCleanupTest {
    @Test
    fun cacheScansPreserveUnrelatedFilesAndMatchingDirectories() {
        val temporary = Files.createTempDirectory("harmonic-model-cache-scan-")
        try {
            val root = Files.createDirectory(temporary.resolve("models"))
            val cache = Files.createDirectory(temporary.resolve("cache"))
            val model = LocalModelCatalog.models.first(LocalModelDefinition::downloadable)
            val prefix = LocalModelFilePolicy.inferenceCachePrefixes(model).first()
            val owned = cache.resolve(prefix + "owned")
            Files.write(owned, ByteArray(25))
            val matchingDirectory = Files.createDirectory(cache.resolve(prefix + "directory"))
            val unrelated = List(40) { Files.write(cache.resolve("other-$it.cache"), byteArrayOf(1)) }
            val storage = FileLocalModelStorage(
                root = Path(root.toString()),
                usableSpaceBytes = { Long.MAX_VALUE },
                inferenceCacheRoot = Path(cache.toString()),
                models = listOf(model),
            )

            assertEquals(25L, storage.storedBytes())
            assertTrue(storage.prepareDownload(model) is LocalModelStoragePreparation.Ready)
            assertFalse(Files.exists(owned))
            assertTrue(Files.isDirectory(matchingDirectory))
            assertTrue(unrelated.all { Files.exists(it) })
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    @Test
    fun sizeAndClearCoverOnlyOwnedModelFilesAndCaches() {
        val root = Files.createTempDirectory("harmonic-models-")
        val cache = Files.createTempDirectory("harmonic-model-cache-")
        try {
            val model = LocalModelCatalog.models.first(LocalModelDefinition::downloadable)
            val modelDirectory = root.resolve(model.id)
            Files.createDirectories(modelDirectory.resolve("nested"))
            Files.write(modelDirectory.resolve(model.fileName), ByteArray(1_250))
            Files.write(modelDirectory.resolve("nested").resolve("partial"), ByteArray(2_000))
            val cacheFile = cache.resolve(
                LocalModelFilePolicy.inferenceCachePrefixes(model).first() + "test",
            )
            Files.write(cacheFile, ByteArray(500))
            val unrelatedRootFile = root.resolve("keep.txt")
            val unrelatedCacheFile = cache.resolve("keep.cache")
            Files.write(unrelatedRootFile, ByteArray(100))
            Files.write(unrelatedCacheFile, ByteArray(100))

            val storage = FileLocalModelStorage(
                root = Path(root.toString()),
                usableSpaceBytes = { Long.MAX_VALUE },
                inferenceCacheRoot = Path(cache.toString()),
                models = listOf(model),
            )

            assertEquals(3_750L, storage.storedBytes())
            assertTrue(storage.clearStoredModels())
            assertEquals(0L, storage.storedBytes())
            assertFalse(Files.exists(modelDirectory))
            assertFalse(Files.exists(cacheFile))
            assertTrue(Files.exists(unrelatedRootFile))
            assertTrue(Files.exists(unrelatedCacheFile))
        } finally {
            root.toFile().deleteRecursively()
            cache.toFile().deleteRecursively()
        }
    }
}
