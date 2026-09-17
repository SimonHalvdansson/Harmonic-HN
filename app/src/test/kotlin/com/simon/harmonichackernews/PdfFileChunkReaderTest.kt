package com.simon.harmonichackernews

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfFileChunkReaderTest {
    @Test
    fun trustsOnlyTheConfiguredLocalViewerDocument() {
        val viewer = "file:///android_asset/composeResources/" +
            "com.simon.harmonichackernews.resources/files/web/pdf/index.html"

        assertEquals(true, isTrustedPdfViewerUrl(viewer, viewer))
        assertEquals(true, isTrustedPdfViewerUrl("$viewer#page=2", viewer))
        assertEquals(true, isTrustedPdfViewerUrl("$viewer?theme=dark#page=2", viewer))
        assertEquals(false, isTrustedPdfViewerUrl("$viewer.attacker", viewer))
        assertEquals(false, isTrustedPdfViewerUrl("https://example.com/index.html", viewer))
    }

    @Test
    fun validRangeReturnsExactlyTheRequestedBytes() = withPdfFile(
        byteArrayOf(10, 20, 30, 40, 50),
    ) { file ->
        PdfFileChunkReader(file).use { reader ->
            assertEquals(5L, reader.size())
            assertArrayEquals(byteArrayOf(20, 30, 40), reader.read(1, 4))
        }
    }

    @Test
    fun rejectsInvalidAndOverflowingRanges() = withPdfFile(ByteArray(16)) { file ->
        PdfFileChunkReader(file).use { reader ->
            assertNull(reader.read(-1, 1))
            assertNull(reader.read(4, 4))
            assertNull(reader.read(5, 4))
            assertNull(reader.read(0, 17))
            assertNull(reader.read(1, Long.MIN_VALUE))
            assertNull(reader.read(Long.MIN_VALUE, Long.MAX_VALUE))
        }
    }

    @Test
    fun rejectsChunksAboveTheConfiguredAllocationLimit() = withPdfFile(ByteArray(8)) { file ->
        PdfFileChunkReader(file, maxChunkBytes = 4).use { reader ->
            assertEquals(4, reader.read(0, 4)?.size)
            assertNull(reader.read(0, 5))
        }
    }

    @Test
    fun closedReaderCannotExposeFileMetadataOrBytes() = withPdfFile(byteArrayOf(1, 2, 3)) { file ->
        val reader = PdfFileChunkReader(file)
        reader.close()

        assertEquals(0L, reader.size())
        assertNull(reader.read(0, 1))
    }

    private inline fun withPdfFile(bytes: ByteArray, block: (File) -> Unit) {
        val file = File.createTempFile("harmonic-pdf-bridge", ".pdf")
        try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }
}
