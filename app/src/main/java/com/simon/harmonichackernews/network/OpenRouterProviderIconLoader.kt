package com.simon.harmonichackernews.network

import android.os.Handler
import android.os.Looper
import com.simon.harmonichackernews.network.NetworkComponent.httpClientInstance
import java.io.IOException
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document

/** Resolves the provider artwork embedded in OpenRouter provider pages.  */
object OpenRouterProviderIconLoader {
    private const val OPENROUTER_URL = "https://openrouter.ai"
    private const val REQUEST_SPACING_MS = 120L
    private const val RETRY_DELAY_MS = 750L
    private const val MAX_ATTEMPTS = 3
    private val UNSUPPORTED_DISPLAY_P3_STYLE = Regex(
        "\\sstyle=(\"[^\"]*color\\(display-p3[^\"]*\"|'[^']*color\\(display-p3[^']*')",
        RegexOption.IGNORE_CASE
    )
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val CACHE = mutableMapOf<String, Any>()
    private val IN_FLIGHT = mutableMapOf<String, MutableList<CallbackListener>>()
    private val QUEUE = ArrayDeque<String>()
    private var queueRunning = false

    fun resolve(providerSlug: String?, listener: CallbackListener) {
        val normalizedSlug = providerSlug.orEmpty().trim { it <= ' ' }.lowercase()
        if (normalizedSlug.isEmpty()) {
            MAIN_HANDLER.post { listener.onResolved(normalizedSlug, null) }
            return
        }

        var startQueue = false
        synchronized(CACHE) {
            val cached = CACHE[normalizedSlug]
            if (cached != null) {
                MAIN_HANDLER.post { listener.onResolved(normalizedSlug, cached) }
                return
            }
            val waiting = IN_FLIGHT[normalizedSlug]
            if (waiting != null) {
                waiting.add(listener)
                return
            }
            IN_FLIGHT[normalizedSlug] = mutableListOf(listener)
            QUEUE.add(normalizedSlug)
            if (!queueRunning) {
                queueRunning = true
                startQueue = true
            }
        }
        if (startQueue) {
            requestNext(0L)
        }
    }

    private fun requestNext(delayMs: Long) {
        MAIN_HANDLER.postDelayed(Runnable queueTask@ {
            val providerSlug = synchronized(CACHE) {
                QUEUE.removeFirstOrNull().also {
                    if (it == null) {
                        queueRunning = false
                    }
                }
            }
            if (providerSlug == null) {
                return@queueTask
            }
            requestProviderPage(providerSlug, 1)
        }, delayMs)
    }

    private fun requestProviderPage(normalizedSlug: String, attempt: Int) {
        val providerUrl = OPENROUTER_URL.toNetworkUrl().newBuilder()
            .addPathSegment(normalizedSlug)
            .build()
        val request = HttpRequest.Builder().url(providerUrl).build()
        httpClientInstance.newCall(request).enqueue(object : HttpCallback {
            override fun onFailure(call: HttpCall, e: IOException) {
                retryOrFinish(normalizedSlug, attempt, null)
            }

            override fun onResponse(call: HttpCall, response: HttpResponse) {
                var iconUrl: String? = null
                var retryable = false
                try {
                    response.use { closeableResponse ->
                        val body = closeableResponse.body
                        if (closeableResponse.isSuccessful && body != null) {
                            val page = Ksoup.parse(
                                body.string(), baseUri = providerUrl.toString()
                            )
                            iconUrl = findProviderIcon(page, normalizedSlug)
                        } else {
                            val code = closeableResponse.code
                            retryable = code == 403 || code == 408 || code == 429 || code >= 500
                        }
                    }
                } catch (ignored: Exception) {
                    // Provider initials remain visible when OpenRouter changes or rejects the page.
                }
                val resolvedIconUrl = iconUrl
                if (retryable) {
                    retryOrFinish(normalizedSlug, attempt, resolvedIconUrl)
                } else if (resolvedIconUrl != null && isSvgUrl(resolvedIconUrl)) {
                    fetchSvg(normalizedSlug, resolvedIconUrl)
                } else {
                    finish(normalizedSlug, resolvedIconUrl)
                }
            }
        })
    }

    private fun fetchSvg(providerSlug: String, iconUrl: String) {
        val request = HttpRequest.Builder().url(iconUrl).build()
        httpClientInstance.newCall(request).enqueue(object : HttpCallback {
            override fun onFailure(call: HttpCall, e: IOException) {
                finish(providerSlug, iconUrl)
            }

            override fun onResponse(call: HttpCall, response: HttpResponse) {
                try {
                    response.use { closeableResponse ->
                        val body = closeableResponse.body
                        if (!closeableResponse.isSuccessful || body == null) {
                            finish(providerSlug, iconUrl)
                            return
                        }
                        val svg = body.bytes().decodeToString()
                        val sanitized = UNSUPPORTED_DISPLAY_P3_STYLE.replace(svg, "")
                        finish(providerSlug, sanitized.encodeToByteArray())
                    }
                } catch (ignored: Exception) {
                    finish(providerSlug, iconUrl)
                }
            }
        })
    }

    private fun isSvgUrl(iconUrl: String): Boolean {
        return iconUrl.lowercase().contains(".svg")
    }

    private fun retryOrFinish(
        providerSlug: String, attempt: Int,
        iconUrl: String?
    ) {
        if (attempt < MAX_ATTEMPTS) {
            MAIN_HANDLER.postDelayed(
                Runnable { requestProviderPage(providerSlug, attempt + 1) },
                RETRY_DELAY_MS * attempt
            )
        } else {
            finish(providerSlug, iconUrl)
        }
    }

    private fun findProviderIcon(page: Document, providerSlug: String): String? {
        val expectedAlt = "Favicon for $providerSlug"
        for (image in page.select("img[alt][src]")) {
            if (!expectedAlt.equals(image.attr("alt").trim { it <= ' ' }, ignoreCase = true)) {
                continue
            }
            val iconUrl = image.absUrl("src")
            try {
                val parsed = iconUrl.toNetworkUrl()
                if ("https" == parsed.scheme || "http" == parsed.scheme) {
                    return parsed.toString()
                }
            } catch (ignored: IllegalArgumentException) {
                return null
            }
        }
        return null
    }

    private fun finish(providerSlug: String, iconData: Any?) {
        val listeners: MutableList<CallbackListener>?
        synchronized(CACHE) {
            if (iconData != null) {
                CACHE[providerSlug] = iconData
            }
            listeners = IN_FLIGHT.remove(providerSlug)
        }
        if (listeners != null) {
            MAIN_HANDLER.post {
                listeners.forEach { it.onResolved(providerSlug, iconData) }
            }
        }
        requestNext(REQUEST_SPACING_MS)
    }

    fun interface CallbackListener {
        fun onResolved(providerSlug: String, iconData: Any?)
    }
}
