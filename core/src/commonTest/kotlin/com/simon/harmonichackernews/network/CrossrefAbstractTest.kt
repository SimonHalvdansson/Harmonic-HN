package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossrefAbstractTest {
    private fun parse(fields: String) = CrossrefLinkPreview.parseCrossref(
        """{"message":{"title":["Article"],$fields}}""", "10.1000/example", "https://doi.org/10.1000/example",
    )

    @Test fun extractsJatsParagraphsAndDecodesEntitiesWithoutJoiningWords() {
        val info = parse(""""abstract":"<jats:title>Abstract</jats:title><jats:p>A <jats:italic>small</jats:italic> study &amp; results.</jats:p><jats:p>Second paragraph.</jats:p><script>ignored()</script>"""")
        assertEquals("Abstract A small study & results. Second paragraph.", info.description)
    }

    @Test fun missingOrEmptyAbstractDoesNotInventDescription() {
        for (fields in listOf("\"abstract\":null", "\"abstract\":\"<jats:p> </jats:p>\"", "\"type\":\"journal-article\"")) {
            assertNull(parse(fields).description)
        }
    }

    @Test fun longAbstractIsBoundedAtAWordBoundary() {
        val info = parse(""""abstract":"${"A long abstract sentence. ".repeat(100)}"""")
        assertTrue(info.description!!.length <= 1201)
        assertTrue(info.description.endsWith("…"))
    }

    @Test fun preservesOrganizationAuthorsAndIndicatesOmittedAuthors() {
        val info = parse(""""author":[{"name":"Research Group"},{"given":"Ada","family":"Lovelace"},{"family":"Smith"},{"name":"Other Group"}],"type":"journal-article"""")
        assertEquals("Research Group, Ada Lovelace, Smith et al.", info.details.single { it.label == "Authors" }.value)
        assertEquals("Journal article", info.details.single { it.label == "Type" }.value)
    }
}
