package com.simon.harmonichackernews

import android.view.View
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw

/** Defers browser startup until the first frame and owns cancellation of all opening callbacks. */
internal class CommentsOpeningWork(
    private val integrated: () -> Boolean,
    private val showingWebsite: () -> Boolean,
    private val hiddenBrowserDelayMillis: () -> Long,
    private val loadComments: () -> Unit,
    private val initializeVisibleBrowser: () -> Unit,
    private val initializeConfiguredBrowser: () -> Unit,
    private val startSummary: () -> Unit,
) {
    private var root: View? = null
    private var preDraw: OneShotPreDrawListener? = null
    private val callbacks = mutableSetOf<Runnable>()
    private var firstDrawCompleted = false
    private var pendingVisibleBrowser = false
    private var pendingSummary = false
    private var closed = false

    fun schedule(view: View) {
        check(root == null) { "Opening work has already been scheduled" }
        root = view
        preDraw = view.doOnPreDraw {
            preDraw = null
            post {
                firstDrawCompleted = true
                // Cache/network work can overlap the transition. Hidden Chromium startup cannot.
                loadComments()
                val visible = showingWebsite() || pendingVisibleBrowser
                post(delayMillis = if (visible) 0L else hiddenBrowserDelayMillis(), nextFrame = visible) {
                    if (visible) requestVisibleBrowser() else requestConfiguredBrowser()
                    if (pendingSummary) {
                        pendingSummary = false
                        requestSummary()
                    }
                }
            }
        }
    }

    fun requestVisibleBrowser() {
        if (closed || !integrated()) return
        if (!firstDrawCompleted) {
            pendingVisibleBrowser = true
            return
        }
        pendingVisibleBrowser = false
        initializeVisibleBrowser()
    }

    fun requestConfiguredBrowser() {
        if (!closed && integrated() && firstDrawCompleted) initializeConfiguredBrowser()
    }

    fun requestSummary() {
        if (closed) return
        if (!firstDrawCompleted) {
            pendingSummary = true
            return
        }
        startSummary()
    }

    fun close() {
        closed = true
        preDraw?.removeListener()
        preDraw = null
        callbacks.forEach { root?.removeCallbacks(it) }
        callbacks.clear()
        root = null
    }

    private fun post(delayMillis: Long = 0L, nextFrame: Boolean = false, action: () -> Unit) {
        val view = root ?: return
        val callback = object : Runnable {
            override fun run() {
                callbacks.remove(this)
                if (!closed && root === view) action()
            }
        }
        callbacks += callback
        when {
            nextFrame -> view.postOnAnimation(callback)
            delayMillis > 0 -> view.postDelayed(callback, delayMillis)
            else -> view.post(callback)
        }
    }
}
