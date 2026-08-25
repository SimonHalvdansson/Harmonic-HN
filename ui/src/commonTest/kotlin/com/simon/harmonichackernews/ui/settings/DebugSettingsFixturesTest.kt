package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugSettingsFixturesTest {
    @Test
    fun includesExactlyOneSampleForEveryPreviewType() {
        assertEquals(LinkPreviewType.entries.size, DebugLinkPreviewSamples.size)
        assertEquals(
            LinkPreviewType.entries.toSet(),
            DebugLinkPreviewSamples.map { it.type }.toSet(),
        )
        assertEquals(
            DebugLinkPreviewSamples.size,
            DebugLinkPreviewSamples.map { it.hnId }.toSet().size,
        )
        assertTrue(DebugLinkPreviewSamples.all { it.targetUrl.startsWith("https://") })
    }

    @Test
    fun includesOpenRouterSolPreview() {
        val fixture = DebugLinkPreviewSamples.single {
            it.type == LinkPreviewType.OPENROUTER_MODEL
        }

        assertEquals("https://news.ycombinator.com/item?id=49337602", fixture.hnUrl)
    }
}
