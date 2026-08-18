package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class DebugSettingsFixturesTest {
    @Test
    fun includesOpenRouterSolPreview() {
        val fixture = DebugPreviewLinks.single { it.title == "OpenRouter · GPT-5.6 Sol" }

        assertEquals("https://news.ycombinator.com/item?id=49337602", fixture.url)
    }
}
