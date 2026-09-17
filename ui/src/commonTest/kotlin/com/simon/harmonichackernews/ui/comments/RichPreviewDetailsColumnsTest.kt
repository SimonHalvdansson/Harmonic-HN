package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.LinkPreviewDetail
import kotlin.test.Test
import kotlin.test.assertEquals

class RichPreviewDetailsColumnsTest {
    @Test
    fun preservesAlternatingOrderAcrossColumns() {
        val details = List(5) { index ->
            LinkPreviewDetail(label = "label-$index", value = "value-$index")
        }

        val columns = splitRichPreviewDetails(details)

        assertEquals(listOf("value-0", "value-2", "value-4"), columns.left.map { it.value })
        assertEquals(listOf("value-1", "value-3"), columns.right.map { it.value })
    }
}
