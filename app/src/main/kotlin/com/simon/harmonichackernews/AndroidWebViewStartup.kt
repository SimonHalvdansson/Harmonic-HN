package com.simon.harmonichackernews

import android.content.Context
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Wait before touching WebView APIs, including feature checks and CookieManager. */
internal suspend fun awaitAndroidWebViewStartup(context: Context) =
    startAndroidWebView(context, runUiThreadTasks = true)

/** Prepares the provider off the UI thread; still await full startup before using WebView APIs. */
internal suspend fun awaitAndroidWebViewBackgroundStartup(context: Context) =
    startAndroidWebView(context, runUiThreadTasks = false)

private suspend fun startAndroidWebView(context: Context, runUiThreadTasks: Boolean) {
    suspendCancellableCoroutine { continuation ->
        val config = WebViewStartUpConfig.Builder { command ->
            Dispatchers.IO.asExecutor().execute {
                try {
                    command.run()
                } catch (error: RuntimeException) {
                    // Older providers can throw while loading, before reaching the error callback.
                    continuation.resumeWithException(error)
                }
            }
        }.setShouldRunUiThreadStartUpTasks(runUiThreadTasks).build()
        WebViewCompat.startUpWebView(
            context.applicationContext,
            config,
            object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                override fun onResult(result: WebViewStartUpResult) {
                    continuation.resume(Unit)
                }

                override fun onError(error: WebViewStartupException) {
                    continuation.resumeWithException(error)
                }
            },
        )
    }
}
