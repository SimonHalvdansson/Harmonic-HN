package com.simon.harmonichackernews.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingTextExportTest {
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
