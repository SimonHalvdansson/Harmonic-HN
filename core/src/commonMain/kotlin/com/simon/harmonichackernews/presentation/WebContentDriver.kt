package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.flow.StateFlow

data class WebContentDriverState(
    val currentUrl: String? = null,
    val loading: Boolean = false,
    val pageReady: Boolean = false,
    val canGoBack: Boolean = false,
    val showingError: Boolean = false,
    val showingCachedContent: Boolean = false,
)

/**
 * Small browser boundary implemented by WebView, WKWebView and a desktop web engine.
 * No feature code needs to know which native view evaluates scripts or keeps history.
 */
interface WebContentDriver {
    val state: StateFlow<WebContentDriverState>
    fun load(url: String)
    fun reload()
    fun goBack(): Boolean
    fun evaluateJavaScript(script: String, onResult: (String?) -> Unit = {})
    fun readPageText(onResult: (String?) -> Unit)
}
