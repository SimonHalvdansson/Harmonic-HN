package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject

/** Only the top document is evaluated; no native JavascriptInterface is exposed to subframes. */
object ReadAloudWebSession {
    suspend fun run(
        enabled: Boolean,
        script: String,
        evaluate: suspend (String) -> String?,
        state: () -> ReadAloudState,
        command: (JsonObject) -> Unit,
        visible: () -> Boolean = { true },
    ) {
        if (!enabled) {
            evaluate("window.HarmonicReaderMode && HarmonicReaderMode.clearReadAloud();")
            return
        }
        var installed = false
        while (currentCoroutineContext().isActive) {
            if (visible()) {
                if (!installed) {
                    evaluate(script)
                    installed = true
                }
                val result = evaluate("window.HarmonicReaderMode && HarmonicReaderMode.readAloudTick(${state().json()});")
                ReadAloudController.parseCommand(result)?.let(command)
            }
            delay(500)
        }
    }
}
