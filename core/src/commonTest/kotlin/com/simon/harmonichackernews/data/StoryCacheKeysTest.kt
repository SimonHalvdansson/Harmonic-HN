package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryCacheKeysTest {
    @Test
    fun articleMetadataKeysPreserveLegacyNames() {
        assertEquals(
            "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL42",
            StoryCacheKeys.articleUrlKey(42),
        )
        assertEquals(
            "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET42",
            StoryCacheKeys.articleCharsetKey(42),
        )
    }
}
