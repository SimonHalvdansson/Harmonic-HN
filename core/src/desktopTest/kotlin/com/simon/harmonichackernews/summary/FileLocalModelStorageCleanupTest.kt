package com.simon.harmonichackernews.summary

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.files.Path

class FileLocalModelStorageCleanupTest {
    @Test
    fun nativeResumeDataSurvivesRetryButIsRemovedByCancelAndClear() {
        val temporary = Files.createTempDirectory("harmonic-resume-data-")
        try {
            val model = LocalModelCatalog.models.first(LocalModelDefinition::downloadable)
            Files.createDirectories(temporary.resolve(model.id))
            val resume = java.nio.file.Path.of(LocalModelFilePolicy.resumeDataPath(Path(temporary.toString()), model).toString())
            val storage = FileLocalModelStorage(Path(temporary.toString()), { Long.MAX_VALUE })
            Files.write(resume, byteArrayOf(1, 2, 3))
            assertTrue(storage.prepareDownload(model) is LocalModelStoragePreparation.Ready)
            assertTrue(Files.exists(resume))
            storage.remove(model, includeFinalFile = false)
            assertFalse(Files.exists(resume))
            Files.createDirectories(resume.parent)
            Files.write(resume, byteArrayOf(1))
            assertTrue(storage.clearStoredModels())
            assertFalse(Files.exists(resume))
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    @Test
    fun retiredDownloadsRemainCountedAndCanBeExplicitlyCleared() {
        val temporary = Files.createTempDirectory("harmonic-retired-models-")
        try {
            val root = Files.createDirectory(temporary.resolve("models"))
            val cache = Files.createDirectory(temporary.resolve("cache"))
            val retired = Files.createDirectory(root.resolve("bonsai-1.7b"))
            val oldFile = Files.write(retired.resolve("Bonsai-1.7B-Q1_0.gguf"), ByteArray(20))
            val oldCache = Files.write(
                cache.resolve("Qwen3.5-0.8B-hybrid-exact-c2048.litertlm.xnnpack_cache_test"), ByteArray(10),
            )
            val retiredLfm = Files.createDirectory(root.resolve("lfm-2.5-1.2b"))
            val oldLfm = Files.write(retiredLfm.resolve("LFM2.5-1.2B-Instruct_int4_gpu.litertlm"), ByteArray(15))
            val oldLfmCache = Files.write(
                cache.resolve("LFM2.5-1.2B-Instruct_int4_gpu.litertlm.xnnpack_cache_test"), ByteArray(5),
            )
            val unrelated = Files.write(root.resolve("keep.txt"), ByteArray(5))
            val storage = FileLocalModelStorage(Path(root.toString()), { Long.MAX_VALUE }, Path(cache.toString()))

            assertEquals(50L, storage.storedBytes())
            assertTrue(Files.exists(oldFile))
            assertTrue(Files.exists(oldLfm))
            assertTrue(storage.clearStoredModels())
            assertFalse(Files.exists(oldFile))
            assertFalse(Files.exists(oldCache))
            assertFalse(Files.exists(oldLfm))
            assertFalse(Files.exists(oldLfmCache))
            assertTrue(Files.exists(unrelated))
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    @Test
    fun replacementDownloadRemovesOldGgufButPreservesItsNewPartial() {
        val temporary = Files.createTempDirectory("harmonic-replaced-model-")
        try {
            val model = LocalModelCatalog.models.first { it.id == LocalModelCatalog.MODEL_MINISTRAL_3B }
            val directory = Files.createDirectory(temporary.resolve(model.id))
            val obsolete = Files.write(directory.resolve("Ministral-3-3B-Instruct-2512-Q4_K_M.gguf"), ByteArray(20))
            val partial = Files.write(directory.resolve(model.fileName + LocalModelFilePolicy.PARTIAL_FILE_SUFFIX), ByteArray(10))
            val storage = FileLocalModelStorage(Path(temporary.toString()), { Long.MAX_VALUE })

            assertEquals(null, storage.snapshot(model).finalFileBytes)
            assertTrue(storage.prepareDownload(model) is LocalModelStoragePreparation.Ready)
            assertFalse(Files.exists(obsolete))
            assertTrue(Files.exists(partial))
            assertEquals(10L, storage.snapshot(model).partialFileBytes)
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

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
