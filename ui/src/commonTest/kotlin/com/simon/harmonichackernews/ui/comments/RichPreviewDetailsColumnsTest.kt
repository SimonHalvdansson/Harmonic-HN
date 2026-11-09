package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.LinkPreviewDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RichPreviewDetailsColumnsTest {
    @Test
    fun largeCountsAreCompactWithoutChangingIdentifiersOrExplicitDisplayText() {
        assertEquals("GitHub stars: 10.9K", LinkPreviewDetail("GitHub stars", "10902").previewText())
        assertEquals("Followers: 115K", LinkPreviewDetail("Followers", "114888").previewText())
        assertEquals("Revision: 123456", LinkPreviewDetail("Revision", "123456").previewText())
        assertEquals("special", LinkPreviewDetail("Followers", "114888", "special").previewText())
    }

    @Test
    fun preservesAlternatingOrderAcrossColumns() {
        val details = List(5) { index ->
            LinkPreviewDetail(label = "label-$index", value = "value-$index")
        }

        val columns = splitRichPreviewDetails(details)

        assertEquals(listOf("value-0", "value-2", "value-4"), columns.left.map { it.value })
        assertEquals(listOf("value-1", "value-3"), columns.right.map { it.value })
    }

    @Test
    fun givesNamesAndLinksFullWidthWithoutLeavingGapsInCompactColumns() {
        val details = listOf(
            LinkPreviewDetail("Authors", "Ada, Grace"),
            LinkPreviewDetail("Published", "2026-08-04"),
            LinkPreviewDetail("Project URL", "https://example.com/project"),
            LinkPreviewDetail("License", "MIT"),
            LinkPreviewDetail("Dependencies", "3"),
            LinkPreviewDetail("Publisher", "National Academy of Sciences"),
            LinkPreviewDetail("DOI", "10.1073/pnas.2420092122"),
        )
        val columns = splitRichPreviewDetails(details)
        assertEquals(listOf("Published", "Dependencies"), columns.left.map { it.label })
        assertEquals(listOf("License"), columns.right.map { it.label })
        assertEquals(listOf("Authors", "Project URL", "Publisher", "DOI"), columns.fullWidth.map { it.label })
        assertEquals(listOf(details[1]), splitRichPreviewDetails(listOf(details[1])).fullWidth)
    }

    @Test
    fun compactTextPreservesExplicitOverridesAndAmbiguousDateLabels() {
        assertEquals("Ada", LinkPreviewDetail("Author", "Ada").previewText())
        assertEquals("Published: 2026-08-04", LinkPreviewDetail("Published", "2026-08-04").previewText())
        assertEquals("Updated: 2026-08-04", LinkPreviewDetail("Updated", "2026-08-04").previewText())
        assertEquals("u/ada", LinkPreviewDetail("Author", "ada", "u/ada").previewText())
    }

    @Test
    fun projectLinksKeepDestinationAndPathButHideSchemeForDisplay() {
        val detail = LinkPreviewDetail("Project URL", "https://github.com/owner/project/")
        assertEquals("https://github.com/owner/project/", detail.projectLink())
        assertEquals("github.com/owner/project", detail.previewText())
        assertNull(LinkPreviewDetail("Project URL", "javascript:alert(1)").projectLink())
        assertNull(LinkPreviewDetail("Project URL", "/relative").projectLink())
        assertNull(LinkPreviewDetail("Author", "https://example.com").projectLink())
    }
}
