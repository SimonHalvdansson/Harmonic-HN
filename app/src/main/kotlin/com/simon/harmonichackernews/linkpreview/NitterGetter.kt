package com.simon.harmonichackernews.linkpreview

import android.webkit.WebView
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.network.NitterPreview

/** Android JavaScript evaluator for the shared Nitter extraction program and decoder. */
object NitterGetter {
    fun getInfo(webView: WebView, callback: GetterCallback) {
        webView.evaluateJavascript(NitterPreview.extractionScript) { value ->
            runCatching { NitterPreview.parseEvaluationResult(value) }
                .onSuccess(callback::onSuccess)
                .onFailure { callback.onFailure("Failed at getting Nitter info") }
        }
    }

    interface GetterCallback {
        fun onSuccess(nitterInfo: NitterInfo?)
        fun onFailure(reason: String?)
    }
}
