package com.simon.harmonichackernews

import android.view.View
import com.simon.harmonichackernews.ui.comments.WebContentOverlayState

/** Owns loading animations; the web-content session decides when a page has settled. */
internal class CommentsWebViewLoadingUi {
    private var indicator: WebContentOverlayState? = null
    private var backdrop: View? = null
    private val fadeInBackdrop = Runnable {
        backdrop?.animate()?.alpha(1f)?.setDuration(BACKDROP_FADE_MILLIS)?.start()
    }

    val isBound: Boolean get() = indicator != null && backdrop != null

    fun bind(indicator: WebContentOverlayState, backdrop: View) {
        release()
        this.indicator = indicator
        this.backdrop = backdrop
        indicator.reset()
    }

    fun beginLoad() {
        backdrop?.apply {
            removeCallbacks(fadeInBackdrop)
            animate().cancel()
            alpha = 0f
            visibility = View.VISIBLE
            postDelayed(fadeInBackdrop, BACKDROP_DELAY_MILLIS)
        }
        indicator?.beginLoad()
    }

    fun updateProgress(progress: Int) { indicator?.updateProgress(progress) }

    fun showProgress() { indicator?.showProgress() }

    fun finishLoad(completeProgress: Boolean) {
        backdrop?.apply {
            removeCallbacks(fadeInBackdrop)
            animate().cancel()
            visibility = View.GONE
            alpha = 0f
        }
        indicator?.finishLoad(completeProgress)
    }

    fun cancelAnimations() {
        indicator?.reset()
        backdrop?.apply {
            removeCallbacks(fadeInBackdrop)
            animate().cancel()
        }
    }

    fun release() {
        cancelAnimations()
        indicator = null
        backdrop = null
    }

    private companion object {
        const val BACKDROP_DELAY_MILLIS = 2_000L
        const val BACKDROP_FADE_MILLIS = 300L
    }
}
