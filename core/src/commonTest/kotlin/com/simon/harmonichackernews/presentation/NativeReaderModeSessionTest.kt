package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.presentation.ReaderModeTheme
import com.simon.harmonichackernews.settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NativeReaderModeSessionTest {
    private val reading = AppSettingsRepository(InMemoryKeyValueStore(), kotlinx.coroutines.flow.emptyFlow()).snapshot().reading
    private val theme = ReaderModeTheme(true, "#fff", "#111", "#111", "#555", "#00f", "#ddd", "#eee", fontSizePx = 17)

    @Test
    fun defaultEnableManualDisableAndNavigationUseSharedPolicy() = runTest {
        val commands = mutableListOf<String>()
        val session = NativeReaderModeSession(this, { command ->
            commands += command
            when {
                "isAvailable" in command -> "available"
                ".disable()" in command -> "disabled"
                else -> "enabled"
            }
        }, script = { "reader source" })
        session.configure(reading.copy(readerModeDefault = true)) { theme }
        session.navigationStarted("https://example.com/article")
        session.pageFinished()
        runCurrent()
        assertTrue(session.state.value.enabled)
        session.toggle()
        runCurrent()
        assertFalse(session.state.value.enabled)
        session.pageFinished()
        runCurrent()
        assertFalse(session.state.value.enabled)
        session.navigationStarted("https://example.com/next")
        assertFalse(session.state.value.available)
        session.pageFinished()
        runCurrent()
        assertTrue(session.state.value.enabled)
        session.configure(reading.copy(readerModeEnabled = false)) { theme }
        runCurrent()
        assertFalse(session.state.value.enabled)
        assertTrue(commands.last().contains(".disable()"))
        session.dispose()
    }

    @Test
    fun lateResultsCannotEnableReaderOnAnotherPageAndErrorsStayContained() = runTest {
        val result = CompletableDeferred<String>()
        var evaluations = 0
        val session = NativeReaderModeSession(this, { evaluations++; result.await() }, script = { "source" })
        session.configure(reading.copy(readerModeDefault = true)) { theme }
        session.navigationStarted("https://example.com/article")
        session.pageFinished()
        runCurrent()
        session.navigationStarted("about:harmonic-error")
        result.complete("enabled")
        runCurrent()
        assertFalse(session.state.value.enabled)
        session.pageFinished()
        runCurrent()
        assertEquals(1, evaluations)
        session.dispose()
        val failing = NativeReaderModeSession(this, { error("renderer gone") }, script = { "source" })
        failing.configure(reading) { theme }
        failing.navigationStarted("https://example.com/article")
        failing.pageFinished()
        advanceUntilIdle()
        assertFalse(failing.state.value.available)
        failing.dispose()
    }

    @Test
    fun finalRedirectUrlIsUsedAndAnUnresponsiveRendererTimesOut() = runTest {
        val session = NativeReaderModeSession(this, { "enabled" }, script = { "source" })
        session.configure(reading.copy(readerModeDefault = true)) { theme }
        session.navigationStarted(null)
        session.pageFinished("https://example.com/final")
        runCurrent()
        assertTrue(session.state.value.enabled)
        session.dispose()

        val messages = mutableListOf<String>()
        val stuck = NativeReaderModeSession(this, { kotlinx.coroutines.awaitCancellation() },
            script = { "source" }, onMessage = messages::add)
        stuck.configure(reading.copy(readerModeDefault = true)) { theme }
        stuck.navigationStarted("https://example.com/article")
        stuck.pageFinished()
        advanceUntilIdle()
        assertFalse(stuck.state.value.enabled)
        stuck.toggle()
        advanceUntilIdle()
        assertEquals(listOf("Couldn't simplify this page"), messages)
        stuck.dispose()
    }

    @Test
    fun delayedArticlesAreRecheckedAndDisposalCancelsWork() = runTest {
        var evaluations = 0
        val session = NativeReaderModeSession(this, { if (++evaluations == 1) "unavailable" else "available" }, script = { "source" })
        session.configure(reading) { theme }
        session.navigationStarted("https://example.com/article")
        session.pageFinished()
        advanceUntilIdle()
        assertEquals(2, evaluations)
        assertTrue(session.state.value.available)
        session.navigationStarted("https://example.com/next")
        session.pageFinished()
        session.dispose()
        advanceUntilIdle()
        assertEquals(2, evaluations)
    }
}
