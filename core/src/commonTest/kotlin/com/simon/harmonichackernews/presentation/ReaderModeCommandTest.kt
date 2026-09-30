package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class ReaderModeCommandTest {
    private val runtime = WebContentService().createRuntime()
    private val driver = Driver()
    private val controller = WebContentController(runtime, driver)
    private val theme = ReaderModeTheme(true, "#fff", "#000", "#000", "#333", "#00f", "#aaa",
        "#eee", fontFaceCss = "FONT_BYTES".repeat(1000), font = "georgia", fontSizePx = 16)

    @Test fun confirmedFontIsReusedWhileColorsAndSizeStillUpdate() {
        apply(theme)
        apply(theme.copy(textColor = "#123456", fontSizePx = 24))
        assertTrue(driver.commands.first().contains(theme.fontFaceCss))
        val update = driver.commands.last()
        assertFalse(update.contains("FONT_BYTES"))
        assertTrue(update.contains("#123456"))
        assertTrue(update.contains("\"fontSizePx\":24"))
        assertTrue(update.contains("'HarmonicReaderFont'"))
    }

    @Test fun fontsScriptsAndNavigationInvalidateInstallation() {
        apply(theme)
        apply(theme.copy(fontFaceCss = "NEW_FONT"))
        assertTrue(driver.commands.last().contains("NEW_FONT"))
        apply(theme.copy(fontFaceCss = "", font = null))
        assertTrue(driver.commands.last().contains("\"fontFaceCss\":\"\""))
        apply(theme)
        runtime.load.begin()
        apply(theme)
        assertTrue(driver.commands.last().contains(theme.fontFaceCss))
        controller.evaluateReaderMode("updated script", theme, false) {}
        assertTrue(driver.commands.last().startsWith("updated script"))
        controller.reset()
        apply(theme)
        assertTrue(driver.commands.last().contains(theme.fontFaceCss))
    }

    @Test fun replacedDocumentRetriesWithFullInstallationAndReportsOneResult() {
        apply(theme)
        driver.responses.add("\"needs_install\"")
        val results = mutableListOf<ReaderModeScriptStatus>()
        controller.evaluateReaderMode("script", theme, true, results::add)
        assertEquals(3, driver.commands.size)
        assertFalse(driver.commands[1].contains("FONT_BYTES"))
        assertTrue(driver.commands[2].contains(theme.fontFaceCss))
        assertEquals(listOf(ReaderModeScriptStatus.DISABLED), results)
    }

    @Test fun failedEvaluationDoesNotConfirmInstallation() {
        driver.responses.add(null)
        apply(theme)
        apply(theme)
        assertTrue(driver.commands.all { it.contains(theme.fontFaceCss) })
    }

    @Test fun staleCallbackCannotConfirmAnOldFontOrRetryAnOldAction() {
        apply(theme)
        driver.automatic = false
        apply(theme)
        apply(theme.copy(fontFaceCss = "NEW_FONT"))
        driver.callbacks[1]("\"disabled\"")
        driver.callbacks[0]("\"needs_install\"")
        assertEquals(3, driver.commands.size)
        apply(theme.copy(fontFaceCss = "NEW_FONT"))
        assertFalse(driver.commands.last().contains("NEW_FONT"))
    }

    private fun apply(value: ReaderModeTheme) =
        controller.evaluateReaderMode("script", value, false) {}

    private class Driver : WebContentDriver {
        override val state = MutableStateFlow(WebContentDriverState())
        val commands = mutableListOf<String>()
        val responses = mutableListOf<String?>()
        val callbacks = mutableListOf<(String?) -> Unit>()
        var automatic = true
        override fun load(url: String) = Unit
        override fun reload() = Unit
        override fun goBack() = false
        override fun readPageText(onResult: (String?) -> Unit) = onResult(null)
        override fun evaluateJavaScript(script: String, onResult: (String?) -> Unit) {
            commands.add(script)
            if (automatic) onResult(if (responses.isEmpty()) "\"disabled\"" else responses.removeAt(0))
            else callbacks.add(onResult)
        }
    }
}
