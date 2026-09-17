package com.simon.harmonichackernews

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

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
        view?.removeJavascriptInterface(JAVASCRIPT_BRIDGE_NAME)
        bridge?.close()
        bridge = null
    }

    fun release(view: WebView?, removeJavascriptInterface: Boolean) {
        currentFilePath = null
        if (removeJavascriptInterface) view?.removeJavascriptInterface(JAVASCRIPT_BRIDGE_NAME)
        bridge?.close()
        bridge = null
    }

    internal class PdfJavascriptBridge(filePath: String) {
        private val chunkReader = PdfFileChunkReader(java.io.File(filePath))
        private val active = AtomicBoolean(true)

        @JavascriptInterface
        fun getChunk(begin: Long, end: Long): String {
            if (!active.get()) return ""
            return try {
                val data = chunkReader.read(begin, end) ?: return ""
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
                    chunkReader.size()
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
            try {
                chunkReader.close()
            } catch (error: IOException) {
                Log.e(TAG, "Unable to close PDF data", error)
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
