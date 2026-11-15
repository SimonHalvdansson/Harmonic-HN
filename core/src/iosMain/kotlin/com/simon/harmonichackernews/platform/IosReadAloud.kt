package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.presentation.ReadAloudController
import com.simon.harmonichackernews.presentation.ReadAloudState
import kotlinx.coroutines.*

/** Speech, audio-session ownership and system media controls belong to the native host. */
interface IosReadAloudHost : ReadAloudEngine {
    fun setCommandHandler(handler: ((String) -> Unit)?)
    fun update(state: ReadAloudState)
}

/** Process-owned playback survives navigation, backgrounding and scene replacement. */
class IosReadAloudSession(private val host: IosReadAloudHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller = ReadAloudController(host)

    init {
        host.setCommandHandler(::command)
        scope.launch { controller.state.collect(host::update) }
    }

    /** Keep JsonObject inside Kotlin; Swift's NSDictionary bridge cannot preserve its type. */
    fun command(raw: String) {
        ReadAloudController.parseCommand(raw)?.let(controller::command)
    }

    fun close() {
        host.setCommandHandler(null)
        controller.close()
        scope.cancel()
    }
}
