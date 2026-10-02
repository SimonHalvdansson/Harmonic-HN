package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.MutatePriority
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job

/** Material sends mouse hover at UserInput priority; keyboard focus and long press stay immediate. */
@OptIn(ExperimentalMaterial3Api::class)
internal class HoverDelayedTooltipState(
    private val delegate: TooltipState,
    private val hoverDelayMillis: Long = 500,
) : TooltipState by delegate {
    private var pendingHover: Job? = null

    override suspend fun show(mutatePriority: MutatePriority) {
        if (mutatePriority != MutatePriority.UserInput) {
            pendingHover?.cancel()
            delegate.show(mutatePriority)
            return
        }
        coroutineScope {
            val request = coroutineContext.job
            pendingHover?.cancel()
            pendingHover = request
            try {
                delay(hoverDelayMillis)
                pendingHover = null
                delegate.show(mutatePriority)
            } finally {
                if (pendingHover === request) pendingHover = null
            }
        }
    }

    override fun dismiss() {
        pendingHover?.cancel()
        pendingHover = null
        delegate.dismiss()
    }

    override fun onDispose() {
        pendingHover?.cancel()
        pendingHover = null
        delegate.onDispose()
    }
}
