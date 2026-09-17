package com.simon.harmonichackernews.presentation

/**
 * Portable grace/recheck bookkeeping for reader-mode availability. Native hosts supply only a
 * monotonic clock and their delayed-callback primitive.
 */
class ReaderModeAvailabilityCadence {
    private var initialGraceUsed = false
    private var initialGraceStartedAtMillis = 0L
    private var initialGraceGeneration = -1
    private var unavailableDelayGeneration = -1
    private var recheckGeneration = -1
    private var recheckUsed = false

    fun onLoadStarted(generation: Int, eligible: Boolean, nowMillis: Long) {
        recheckGeneration = -1
        recheckUsed = false
        unavailableDelayGeneration = -1
        if (!eligible) {
            initialGraceGeneration = -1
            return
        }
        val initialGraceStillActive = !initialGraceUsed || isGraceActive(nowMillis)
        if (!initialGraceStillActive) {
            initialGraceGeneration = -1
            return
        }
        if (!initialGraceUsed) {
            initialGraceUsed = true
            initialGraceStartedAtMillis = nowMillis
        }
        initialGraceGeneration = generation
    }

    fun onAvailable() {
        initialGraceGeneration = -1
        unavailableDelayGeneration = -1
        recheckGeneration = -1
    }

    fun onUnavailableNow() {
        initialGraceGeneration = -1
        unavailableDelayGeneration = -1
    }

    /** Returns a delay when unavailability should be deferred, or null when it applies now. */
    fun unavailableDelayMillis(generation: Int, nowMillis: Long): Long? {
        if (initialGraceGeneration != generation || !isGraceActive(nowMillis)) return null
        val remaining = WebContentTiming.READER_INITIAL_AVAILABILITY_GRACE_MILLIS -
            (nowMillis - initialGraceStartedAtMillis)
        if (remaining <= 0) return null
        unavailableDelayGeneration = generation
        return remaining
    }

    fun shouldApplyDelayedUnavailable(
        generation: Int,
        currentGeneration: Int,
    ): Boolean = unavailableDelayGeneration == generation &&
        generation == currentGeneration && initialGraceGeneration == generation

    fun scheduleRecheck(generation: Int, currentGeneration: Int): Boolean {
        if (recheckUsed || generation != currentGeneration) return false
        recheckUsed = true
        recheckGeneration = generation
        return true
    }

    fun shouldRunRecheck(generation: Int, currentGeneration: Int): Boolean =
        recheckGeneration == generation && generation == currentGeneration

    private fun isGraceActive(nowMillis: Long): Boolean = initialGraceGeneration >= 0 &&
        nowMillis - initialGraceStartedAtMillis <
        WebContentTiming.READER_INITIAL_AVAILABILITY_GRACE_MILLIS
}
