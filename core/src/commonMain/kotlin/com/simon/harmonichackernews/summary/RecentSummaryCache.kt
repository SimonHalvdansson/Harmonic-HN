package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.AiSummarySettingsSnapshot
import com.simon.harmonichackernews.settings.GeminiNanoSummaryMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Owned by the app composition. Only the presentation dispatcher accesses these results. */
class RecentSummaryCache(
    private val settings: AiSummarySettingsRepository,
    private val localModel: () -> LocalModelDefinition? = { null },
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    internal data class Key(val source: String, val configuration: Configuration)

    internal data class Configuration(
        val mode: AiSummaryMode,
        val endpoint: String?,
        val cloudModel: String?,
        val localModel: LocalModelDefinition?,
        val prompt: String,
        val threeBullets: Boolean,
        val mock: Boolean,
    )

    internal class Results<T>(private val clock: TimeSource) {
        private data class Entry<T>(val completedAt: TimeMark, val result: T)
        private val entries = linkedMapOf<Key, Entry<T>>()

        fun isEmpty(): Boolean = entries.isEmpty()

        operator fun get(key: Key): T? {
            val entry = entries[key] ?: return null
            if (entry.completedAt.elapsedNow() < 15.minutes) return entry.result
            entries.remove(key)
            return null
        }

        operator fun set(key: Key, result: T) {
            entries.remove(key)
            if (entries.size >= 16) entries.remove(entries.keys.first())
            entries[key] = Entry(clock.markNow(), result)
        }
    }

    internal val articles = Results<StorySummaryState>(timeSource)
    internal data class Discussion(val digest: String, val state: DiscussionSummaryState) {
        fun matching(sourceDigest: String, cached: Boolean): DiscussionSummaryState? =
            if (digest == sourceDigest) state.copy(coverage = state.coverage?.copy(cached = cached)) else null
    }
    internal val discussions = Results<Discussion>(timeSource)

    internal fun configuration(
        discussion: Boolean, mock: Boolean, config: AiSummarySettingsSnapshot = settings.snapshot(),
    ): Configuration {
        val cloud = config.mode == AiSummaryMode.CLOUD
        return Configuration(
            mode = config.mode,
            endpoint = config.baseUrl.takeIf { cloud },
            cloudModel = config.model.takeIf { cloud },
            localModel = if (cloud) null else localModel(),
            prompt = if (discussion) config.discussionSystemPrompt else config.systemPrompt,
            threeBullets = !cloud && !discussion && config.geminiNanoSummaryMode == GeminiNanoSummaryMode.THREE_BULLETS,
            mock = mock,
        )
    }
}
