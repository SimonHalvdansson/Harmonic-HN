package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NitterPreviewTest {
    @Test
    fun convertibleUrlRecognizesOnlySupportedStatusPaths() {
        assertTrue(NitterPreview.isConvertibleUrl("https://x.com/example/status/123"))
        assertTrue(
            NitterPreview.isConvertibleUrl(
                "https://mobile.twitter.com/i/web/status/456/photo/1",
            ),
        )

        assertFalse(NitterPreview.isConvertibleUrl("https://example.com/example/status/123"))
        assertFalse(NitterPreview.isConvertibleUrl("https://x.com/example/status/not-a-number"))
        assertFalse(NitterPreview.isConvertibleUrl("https://x.com/name_is_too_long/status/123"))
    }
}
