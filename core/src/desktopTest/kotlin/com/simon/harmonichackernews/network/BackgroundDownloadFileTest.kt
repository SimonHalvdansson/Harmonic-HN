package com.simon.harmonichackernews.network

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

class BackgroundDownloadFileTest {
    @Test
    fun fullResponseReplacesPartialAndPromotesAtomically() = withFiles { completed, partial, received ->
        Files.write(partial, byteArrayOf(9, 9))
        Files.write(received, byteArrayOf(1, 2, 3, 4))
        destination(completed, partial).importDownloadedFile(Path(received.toString()), 4)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(completed))
        assertFalse(Files.exists(partial))
        assertFalse(Files.exists(received))
    }

    @Test
    fun rangeResponseAppendsExistingForegroundPartial() = withFiles { completed, partial, received ->
        Files.write(partial, byteArrayOf(1, 2))
        Files.write(received, byteArrayOf(3, 4))
        destination(completed, partial).importDownloadedFile(Path(received.toString()), 4, 2)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(completed))
        assertFalse(Files.exists(partial))
    }

    @Test
    fun truncatedOrOversizedResponsePreservesExistingPartial() = withFiles { completed, partial, received ->
        Files.write(partial, byteArrayOf(1, 2))
        for (size in listOf(1, 3)) {
            Files.write(received, ByteArray(size))
            assertFailsWith<IllegalStateException> {
                destination(completed, partial).importDownloadedFile(Path(received.toString()), 4, 2)
            }
            assertFalse(Files.exists(completed))
            assertContentEquals(byteArrayOf(1, 2), Files.readAllBytes(partial))
        }
    }

    @Test
    fun changedPartialCannotBeInstalled() = withFiles { completed, partial, received ->
        Files.write(partial, byteArrayOf(1))
        Files.write(received, byteArrayOf(3, 4))
        assertFailsWith<IllegalStateException> {
            destination(completed, partial).importDownloadedFile(Path(received.toString()), 4, 2)
        }
        assertFalse(Files.exists(completed))
        assertContentEquals(byteArrayOf(1), Files.readAllBytes(partial))
    }

    private fun destination(completed: java.nio.file.Path, partial: java.nio.file.Path) =
        FileResumableDownloadDestination(Path(completed.toString()), Path(partial.toString()))

    private fun withFiles(block: suspend (java.nio.file.Path, java.nio.file.Path, java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("harmonic-background-download-")
        try {
            runBlocking { block(root.resolve("model"), root.resolve("model.download"), root.resolve("received")) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
