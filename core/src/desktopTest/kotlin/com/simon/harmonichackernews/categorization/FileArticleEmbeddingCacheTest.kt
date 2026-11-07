package com.simon.harmonichackernews.categorization

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.test.*

class FileArticleEmbeddingCacheTest {
    @Test fun vectorsSurviveRestartAndRevisionChangesDiscardOldSpace() = runTest {
        val directory = Files.createTempDirectory("harmonic-embedding-cache").toFile()
        try {
            val root = Path(directory.absolutePath)
            val first = FileArticleEmbeddingCache(root)
            first.select("revision-1")
            first.put("a headline", floatArrayOf(1f, 0.25f, -0.75f))
            val restarted = FileArticleEmbeddingCache(root)
            restarted.select("revision-1")
            assertContentEquals(floatArrayOf(1f, 0.25f, -0.75f), restarted.get("a headline"))
            assertNull(restarted.get("another headline"))
            // A partial/corrupt vector is treated as missing and can be regenerated.
            directory.walkTopDown().first { it.isFile }.writeBytes(byteArrayOf(1, 2, 3))
            assertNull(restarted.get("a headline"))
            restarted.put("a headline", floatArrayOf(1f, 0.25f, -0.75f))
            restarted.select("revision-2")
            assertNull(restarted.get("a headline"))
            assertEquals(1, directory.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }
}
