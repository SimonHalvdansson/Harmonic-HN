package com.simon.harmonichackernews

import android.animation.ValueAnimator
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Owns loading animations; the web-content session decides when a page has settled. */
internal class CommentsWebViewLoadingUi {
    private var indicator: LinearProgressIndicator? = null
    private var backdrop: View? = null
    private var progressAnimator: ValueAnimator? = null
    private var targetVisible = false
    private val fadeInBackdrop = Runnable {
        backdrop?.animate()?.alpha(1f)?.setDuration(BACKDROP_FADE_MILLIS)?.start()
    }

    val isBound: Boolean get() = indicator != null && backdrop != null

    fun bind(indicator: LinearProgressIndicator, backdrop: View) {
        release()
        this.indicator = indicator
        this.backdrop = backdrop
        indicator.visibility = View.GONE
        indicator.alpha = 0f
        indicator.progress = 0
    }

    fun beginLoad() {
        backdrop?.apply {
            removeCallbacks(fadeInBackdrop)
            animate().cancel()
            alpha = 0f
            visibility = View.VISIBLE
            postDelayed(fadeInBackdrop, BACKDROP_DELAY_MILLIS)
        }
        cancelProgressAnimator()
        indicator?.progress = 0
        showProgress()
    }

    fun updateProgress(progress: Int) {
        val currentIndicator = indicator ?: return
        cancelProgressAnimator()
        val current = currentIndicator.progress
        if (progress <= current) {
            currentIndicator.progress = progress
            return
        }

        val animator = ValueAnimator.ofInt(current, progress)
        progressAnimator = animator
        animator.duration = PROGRESS_DURATION_MILLIS
        animator.addUpdateListener { animation ->
            if (progressAnimator === animation && indicator === currentIndicator) {
                currentIndicator.progress = animation.animatedValue as Int
            }
        }
        animator.start()
    }

    fun showProgress() {
        val currentIndicator = indicator ?: return
        if (targetVisible && currentIndicator.isVisible) return

        targetVisible = true
        currentIndicator.animate().cancel()
        if (!currentIndicator.isVisible) {
            currentIndicator.alpha = 0f
            currentIndicator.visibility = View.VISIBLE
        }
        currentIndicator.animate()
            .alpha(1f)
            .setDuration(INDICATOR_FADE_MILLIS)
            .start()
    }

    fun finishLoad(completeProgress: Boolean) {
        backdrop?.apply {
            removeCallbacks(fadeInBackdrop)
            animate().cancel()
            visibility = View.GONE
            alpha = 0f
        }
        cancelProgressAnimator()
        val currentIndicator = indicator ?: return
        if (completeProgress) currentIndicator.progress = 100
        if (!targetVisible && !currentIndicator.isVisible) return

        targetVisible = false
        currentIndicator.animate().cancel()
        currentIndicator.animate()
            .alpha(0f)
            .setDuration(INDICATOR_FADE_MILLIS)
            .withEndAction {
                if (indicator === currentIndicator && !targetVisible) {
                    currentIndicator.visibility = View.GONE
                }
            }
            .start()
    }

    fun cancelAnimations() {
        cancelProgressAnimator()
        indicator?.animate()?.cancel()
        backdrop?.apply {
            removeCallbacks(fadeInBackdrop)
            animate().cancel()
        }
    }

    fun release() {
        cancelAnimations()
        indicator = null
        backdrop = null
        targetVisible = false
    }

    private fun cancelProgressAnimator() {
        progressAnimator?.cancel()
        progressAnimator = null
    }

    private companion object {
        const val BACKDROP_DELAY_MILLIS = 2_000L
        const val BACKDROP_FADE_MILLIS = 300L
        const val PROGRESS_DURATION_MILLIS = 400L
        const val INDICATOR_FADE_MILLIS = 50L
    }
}
