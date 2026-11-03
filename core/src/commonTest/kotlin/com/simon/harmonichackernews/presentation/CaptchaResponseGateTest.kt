package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CaptchaResponseGateTest {
    @Test
    fun overlappingReadsAndDuplicateCompletionCannotReturnTwoTokens() {
        val gate = CaptchaResponseGate()
        val ticket = assertNotNull(gate.beginRead())
        assertNull(gate.beginRead())
        assertEquals("token \"quoted\" ☃", gate.finishRead(ticket, "  token \"quoted\" ☃  "))
        assertNull(gate.finishRead(ticket, "duplicate"))
        assertNull(gate.beginRead())
    }

    @Test
    fun anIncompleteOrFailedReadCanBeRetriedInTheSameDocument() {
        val gate = CaptchaResponseGate()
        assertNull(gate.finishRead(assertNotNull(gate.beginRead()), " \n\t"))
        assertNull(gate.finishRead(assertNotNull(gate.beginRead()), null))
        assertEquals("solved", gate.finishRead(assertNotNull(gate.beginRead()), "solved"))
    }

    @Test
    fun aStaleReadCannotConsumeANewDocumentResponse() {
        val gate = CaptchaResponseGate()
        val oldTicket = assertNotNull(gate.beginRead())
        gate.reset()
        val newTicket = assertNotNull(gate.beginRead())
        assertNull(gate.finishRead(oldTicket, "stale"))
        assertNull(gate.beginRead(), "The valid new read is still in progress")
        assertEquals("new", gate.finishRead(newTicket, "new"))
    }

    @Test
    fun closingRejectsOutstandingCallbacksAndExplicitResetStartsANewAttempt() {
        val gate = CaptchaResponseGate()
        val ticket = assertNotNull(gate.beginRead())
        gate.close()
        assertNull(gate.finishRead(ticket, "late"))
        assertNull(gate.beginRead())
        gate.reset()
        val next = assertNotNull(gate.beginRead())
        assertNull(gate.finishRead(ticket, "old"))
        assertEquals("retry", gate.finishRead(next, "retry"))
    }
}
