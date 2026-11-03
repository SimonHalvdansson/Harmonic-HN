package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.DomainFilterRule
import com.simon.harmonichackernews.settings.TitleFilterMatchMode
import com.simon.harmonichackernews.settings.TitleFilterRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterRulePreviewTest {
    @Test
    fun illustrativeTitleExamplesUseTheSameModeAsActualStoryMatching() {
        assertEquals(listOf(true, true, false), titleFilterPreviewExamples(TitleFilterRule("AI")).map { it.hidden })
        assertEquals(listOf(true, true, false), titleFilterPreviewExamples(
            TitleFilterRule("AI", TitleFilterMatchMode.CONTAINS_TEXT),
        ).map { it.hidden })
    }

    @Test
    fun arbitraryTitleRulesHaveAnIllustrativeNonmatch() {
        for (text in listOf("A different", "a", "12345", "t", "C++", "研究", "café")) {
            for (mode in TitleFilterMatchMode.entries) {
                val examples = titleFilterPreviewExamples(TitleFilterRule(text, mode))
                assertTrue(examples.any { it.hidden }, "$text / $mode must have a matching example")
                assertTrue(examples.any { !it.hidden }, "$text / $mode must have a nonmatching example")
            }
        }
    }

    @Test
    fun illustrativeDomainsDemonstrateExactLabelBoundariesAndTheSubdomainSwitch() {
        assertEquals(listOf(true, true, false), domainFilterPreviewExamples(DomainFilterRule("example.com")).map { it.hidden })
        assertEquals(listOf(true, false, false), domainFilterPreviewExamples(
            DomainFilterRule("example.com", includeSubdomains = false),
        ).map { it.hidden })
    }
}
