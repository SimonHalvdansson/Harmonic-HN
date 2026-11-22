package com.simon.harmonichackernews

import com.simon.harmonichackernews.platform.ReadAloudEngine

internal suspend fun naturalVoiceAvailability(): Boolean = false

internal fun createReadAloudEngine(
    system: ReadAloudEngine,
    preferNaturalVoice: () -> Boolean,
): ReadAloudEngine = system
