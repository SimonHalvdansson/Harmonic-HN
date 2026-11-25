package com.simon.harmonichackernews.summary.local

import com.simon.harmonichackernews.summary.ExternalModelFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidExternalModelAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun validatesModelFromGrantedBytesWithOnlyAContentUri() {
        val uri = "content://documents/document/model"
        val bytes = "LITERTLM-test-model".encodeToByteArray()
        bytes.inputStream().asSource().buffered().use { source ->
            val file = ExternalModelFile.inspect(uri, "model.litertlm", bytes.size.toLong(), source)
            assertEquals(ExternalModelFile(uri, "model.litertlm", bytes.size.toLong()), file)
        }
    }

    @Test
    fun usesGrantedStreamWhenTheProviderFileCannotBeReopenedByName() = runBlocking {
        val cache = temporary.root.toPath()
        val identity = UUID.randomUUID()
        val model = "LITERTLM-test-model".encodeToByteArray()
        val runtimePath = withReadableModelPath(
            cache, identity, cache.resolve("unavailable-provider-path"), model.size.toLong(), model.inputStream(),
        ) { path ->
            assertTrue(path.endsWith(".litertlm"))
            Paths.get(path).also { assertArrayEquals(model, Files.readAllBytes(it)) }
        }
        assertFalse(Files.exists(runtimePath))
    }

    @Test
    fun discardsAnIncompleteCopyBeforeStartingInference() = runBlocking {
        val cache = temporary.root.toPath()
        val identity = UUID.randomUUID()
        val result = runCatching {
            withReadableModelPath(cache, identity, cache.resolve("unavailable-provider-path"), 100, byteArrayOf(1).inputStream()) {
                throw AssertionError("LiteRT received a partial file")
            }
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(Files.exists(cache.resolve("linked-models/$identity.litertlm")))
    }
}
