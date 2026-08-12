package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentTextPolicyTest {
    @Test
    fun preservesLegacySpacingAcrossParagraphAndDivisionBoundaries() {
        assertEquals(
            "<br><br>First</p><br><p class=\"next\">Second</p></div><br><div>Third",
            CommentTextPolicy.preserveLegacyParagraphSpacing(
                "<P >First</p> \n <p class=\"next\">Second</p></div>\t<div>Third",
            ),
        )
    }
}
