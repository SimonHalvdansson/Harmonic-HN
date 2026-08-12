package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class TypedPreferenceValuesTest {
    @Test
    fun stableStoredValuesRoundTripAndUnknownValuesUseCompatibleDefaults() {
        assertEquals(DisplayStyle.CARD, DisplayStyle.fromStored("card"))
        assertEquals(DisplayStyle.STANDARD, DisplayStyle.fromStored("unsupported"))
        assertEquals(
            CommentSortingPreference.REPLY_COUNT,
            CommentSortingPreference.fromStored("Reply count"),
        )
        assertEquals(
            CommentSortingPreference.DEFAULT,
            CommentSortingPreference.fromStored("unsupported"),
        )
        assertEquals(CommentsProvider.ALGOLIA, CommentsProvider.fromStored(null))
        assertEquals(
            CommentVolumeNavigationMode.TOP_LEVEL,
            CommentVolumeNavigationMode.fromStored("top_level"),
        )
        assertEquals(WebViewPreloadMode.NEVER, WebViewPreloadMode.fromStored("unsupported"))
        assertEquals(AppFont.GOOGLE_SANS_FLEX_ROUNDED, AppFont.fromStored("unsupported"))
    }

    @Test
    fun preloadSummaryClampsBatteryUsingPortableRules() {
        assertEquals("Never", WebViewPreloadMode.NEVER.summary(75))
        assertEquals("Always, any battery level", WebViewPreloadMode.ALWAYS.summary(-1))
        assertEquals(
            "Only on WiFi, battery at least 100%",
            WebViewPreloadMode.WIFI_ONLY.summary(500),
        )
    }
}
