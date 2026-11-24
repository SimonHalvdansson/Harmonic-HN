package com.simon.harmonichackernews.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslatableCommentTest {
    @Test fun preservesMarkupLinksAndCodeAndEscapesModelOutput() = runTest {
        val comment = TranslatableComment("""<p>Hello <i>world</i> <a href="https://example.org">https://example.org</a></p><pre><code>x &lt; y</code></pre>""")
        assertEquals("Hello world", comment.languageSample)
        val translated = comment.translate { if (it == "Hello") "<bonjour>" else "monde" }
        assertEquals("""<p>&lt;bonjour&gt; <i>monde</i> <a href="https://example.org">https://example.org</a></p><pre><code>x &lt; y</code></pre>""", translated)
    }
}
