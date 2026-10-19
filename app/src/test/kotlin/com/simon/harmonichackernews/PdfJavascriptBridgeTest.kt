package com.simon.harmonichackernews

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfJavascriptBridgeTest {
    @Test
    fun revocationReturnsWhileReaderIsBusyAndEventuallyClosesItsFile() {
        val file = File.createTempFile("harmonic-pdf-bridge", ".pdf")
        val reader = PdfFileChunkReader(file)
        val workers = Executors.newFixedThreadPool(2)
        val readerLocked = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val closeFinished = CountDownLatch(1)
        val closeCount = AtomicInteger()
        val bridge = AndroidPdfWebViewSession.PdfJavascriptBridge(reader) { command ->
            closeCount.incrementAndGet()
            workers.execute {
                closeStarted.countDown()
                try { command.run() } finally { closeFinished.countDown() }
            }
        }
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(3L, bridge.size) // Open the descriptor before simulating a stalled read.
            val reading = workers.submit {
                synchronized(reader) {
                    readerLocked.countDown()
                    check(releaseReader.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(readerLocked.await(5, TimeUnit.SECONDS))

            bridge.close()
            bridge.close()

            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertEquals(1, closeCount.get())
            assertEquals(1L, closeFinished.count)
            assertEquals(0L, bridge.size)
            assertEquals("", bridge.getChunk(0, 1))

            releaseReader.countDown()
            reading.get(5, TimeUnit.SECONDS)
            assertTrue(closeFinished.await(5, TimeUnit.SECONDS))
            assertEquals(0L, reader.size())
            assertNull(reader.read(0, 1))
        } finally {
            releaseReader.countDown()
            workers.shutdown()
            workers.awaitTermination(5, TimeUnit.SECONDS)
            reader.close()
            file.delete()
        }
    }
}
