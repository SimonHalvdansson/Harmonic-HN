package com.simon.harmonichackernews.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class PendingTextExportTest {
    @get:Rule val cache = TemporaryFolder()

    @Test
    fun restoredOwnerWritesStagedCsvAndDeletesSnapshot() {
        val csv = "hn_id,recorded_at_utc,hn_url\r\n42,1970-01-01T00:00:00.000Z,https://news.ycombinator.com/item?id=42\r\n"
        val savedName = HistoryExportFiles(cache.root).stage(csv)
        val destination = ByteArrayOutputStream()
        HistoryExportFiles(cache.root).complete(savedName) { destination }
        assertEquals(csv, destination.toString("UTF-8"))
        assertEquals(false, File(cache.root, savedName).exists())
    }

    @Test
    fun cancellingRestoredPickerDiscardsSnapshot() {
        val savedName = HistoryExportFiles(cache.root).stage("cancelled")
        HistoryExportFiles(cache.root).complete(savedName, null)
        assertEquals(false, File(cache.root, savedName).exists())
    }

    @Test
    fun takeClearsPendingContentForSuccessOrCancellation() {
        val pending = PendingTextExport().apply { replace("bookmark export") }

        assertEquals("bookmark export", pending.take())
        assertNull(pending.take())
    }

    @Test
    fun lifecycleClearDiscardsPendingContent() {
        val pending = PendingTextExport().apply { replace("bookmark export") }

        pending.clear()

        assertNull(pending.take())
    }
}
