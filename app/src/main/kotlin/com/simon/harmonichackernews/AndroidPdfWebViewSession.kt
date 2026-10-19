package com.simon.harmonichackernews

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/** Owns the PDF viewer's file reference and JavaScript bridge lifecycle. */
internal class AndroidPdfWebViewSession {
    var currentFilePath: String? = null
        private set

    private var bridge: PdfJavascriptBridge? = null

    fun attach(view: WebView, filePath: String) {
        revokeBridge(view)
        currentFilePath = filePath
        val nextBridge = PdfJavascriptBridge(filePath)
        bridge = nextBridge
        view.addJavascriptInterface(nextBridge, JAVASCRIPT_BRIDGE_NAME)
        view.setInitialScale(100)
        view.settings.loadWithOverviewMode = true
        view.settings.useWideViewPort = true
    }

    fun clearCurrentFileReference() {
        currentFilePath = null
    }

    fun revokeBridge(view: WebView?) {
        bridge?.close()
        bridge = null
        view?.removeJavascriptInterface(JAVASCRIPT_BRIDGE_NAME)
    }

    fun release(view: WebView?, removeJavascriptInterface: Boolean) {
        currentFilePath = null
        bridge?.close()
        bridge = null
        if (removeJavascriptInterface) view?.removeJavascriptInterface(JAVASCRIPT_BRIDGE_NAME)
    }

    internal class PdfJavascriptBridge(
        private val chunkReader: PdfFileChunkReader,
        private val closeExecutor: Executor = Dispatchers.IO.asExecutor(),
    ) {
        constructor(filePath: String) : this(PdfFileChunkReader(java.io.File(filePath)))

        private val active = AtomicBoolean(true)

        @JavascriptInterface
        fun getChunk(begin: Long, end: Long): String {
            if (!active.get()) return ""
            return try {
                val data = chunkReader.read(begin, end) ?: return ""
                if (!active.get()) return ""
                Base64.encodeToString(data, Base64.NO_WRAP)
            } catch (error: IOException) {
                Log.e(TAG, "Unable to read PDF data", error)
                ""
            }
        }

        @get:JavascriptInterface
        val size: Long
            get() {
                if (!active.get()) return 0L
                return try {
                    chunkReader.size().takeIf { active.get() } ?: 0L
                } catch (error: IOException) {
                    Log.e(TAG, "Unable to read PDF size", error)
                    0L
                }
            }

        @JavascriptInterface
        fun onLoad() = Unit

        @JavascriptInterface
        fun onFailure() = Unit

        fun close() {
            if (!active.compareAndSet(true, false)) return
            // A JavaScript read holds the reader's monitor during disk I/O. Revoke access now,
            // but wait for that read and close the file away from the UI. This task must outlive
            // the screen's cancelled coroutine scope so navigating away cannot leak the file.
            closeExecutor.execute {
                try {
                    chunkReader.close()
                } catch (error: IOException) {
                    Log.e(TAG, "Unable to close PDF data", error)
                }
            }
        }

        private companion object {
            const val TAG = "PdfJavascriptBridge"
        }
    }

    private companion object {
        const val JAVASCRIPT_BRIDGE_NAME = "PdfAndroidJavascriptBridge"
    }
}
