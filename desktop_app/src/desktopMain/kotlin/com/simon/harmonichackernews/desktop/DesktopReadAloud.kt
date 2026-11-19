package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.presentation.ReadAloudController
import kotlinx.coroutines.*

internal object DesktopReadAloud {
    private val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val mac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    val supported = windows || mac
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller by lazy {
        check(supported)
        ReadAloudController(if (mac) MacSpeechEngine(scope) else WindowsSpeechEngine(scope))
    }
}
