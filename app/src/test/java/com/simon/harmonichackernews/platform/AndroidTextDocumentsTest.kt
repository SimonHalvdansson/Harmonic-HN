package com.simon.harmonichackernews.platform

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidTextDocumentsTest {
    @Test
    fun boundedReaderPreservesAllLineEndingsAndTrailingWhitespace() {
        val text = "first\r\nsecond\n\nthird\r\n"

        assertEquals(text, StringReader(text).readBoundedText(text.length))
    }

    @Test
    fun boundedReaderAcceptsExactlyTheLimit() {
        assertEquals("abcd", StringReader("abcd").readBoundedText(4))
    }

    @Test
    fun boundedReaderRejectsContentAboveTheLimit() {
        assertThrows(TextDocumentTooLargeException::class.java) {
            StringReader("abcde").readBoundedText(4)
        }
    }
}
