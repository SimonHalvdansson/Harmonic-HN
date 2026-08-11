package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyedRequestSessionTest {
    @Test
    fun beginningAndInvalidatingRequestsRejectsStaleResults() {
        val session = KeyedRequestSession<Int>()
        val first = session.begin(10)
        assertTrue(session.isCurrent(first, 10))
        assertFalse(session.isCurrent(first, 11))

        val second = session.begin(11)
        assertFalse(session.isCurrent(first, 10))
        assertTrue(session.isCurrent(second, 11))

        session.invalidate()
        assertFalse(session.isCurrent(second, 11))
    }
}
