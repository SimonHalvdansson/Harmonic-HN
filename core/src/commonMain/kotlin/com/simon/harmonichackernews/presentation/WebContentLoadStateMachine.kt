package com.simon.harmonichackernews.presentation

data class WebContentLoadState(
    val generation: Int = 0,
    val inProgress: Boolean = false,
    val uiSettled: Boolean = true,
    val committedVisible: Boolean = false,
)

/** Portable ordering rules for an embedded browser load. The platform only renders the result. */
class WebContentLoadStateMachine {
    var state: WebContentLoadState = WebContentLoadState()
        private set

    fun begin(): Int {
        state = WebContentLoadState(
            generation = state.generation + 1,
            inProgress = true,
            uiSettled = false,
        )
        return state.generation
    }

    fun commitVisible(generation: Int = state.generation): Boolean {
        if (!isCurrent(generation)) return false
        state = state.copy(committedVisible = true)
        return true
    }

    fun shouldSettleCommittedLoad(generation: Int): Boolean =
        isCurrent(generation) && state.inProgress && state.committedVisible

    fun isActive(generation: Int): Boolean = isCurrent(generation) && state.inProgress

    fun finish(generation: Int): Boolean {
        if (!isCurrent(generation)) return false
        state = state.copy(inProgress = false, uiSettled = true)
        return true
    }

    fun reset() {
        state = WebContentLoadState(generation = state.generation + 1)
    }

    private fun isCurrent(generation: Int): Boolean = generation == state.generation
}
