package com.simon.harmonichackernews.network

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.simon.harmonichackernews.network.NetworkComponent.okHttpClientInstance
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Locale
import java.util.regex.Pattern
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Resolves the provider artwork embedded in OpenRouter provider pages.  */
object OpenRouterProviderIconLoader {
    private const val OPENROUTER_URL = "https://openrouter.ai"
    private const val REQUEST_SPACING_MS = 120L
    private const val RETRY_DELAY_MS = 750L
    private const val MAX_ATTEMPTS = 3
    private val UNSUPPORTED_DISPLAY_P3_STYLE: Pattern = Pattern.compile(
        "\\sstyle=(\"[^\"]*color\\(display-p3[^\"]*\"|'[^']*color\\(display-p3[^']*')",
        Pattern.CASE_INSENSITIVE
    )
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val CACHE: MutableMap<String, Any> = HashMap()
    private val IN_FLIGHT: MutableMap<String, MutableList<CallbackListener>> = HashMap()
    private val QUEUE = ArrayDeque<String>()
    private var queueRunning = false

    fun resolve(providerSlug: String?, listener: CallbackListener) {
        val normalizedSlug = if (providerSlug == null)
            ""
        else
            providerSlug.trim { it <= ' ' }.lowercase()
        if (normalizedSlug.isEmpty()) {
            MAIN_HANDLER.post(Runnable { listener.onResolved(normalizedSlug, null) })
            return
        }

        var startQueue = false
        synchronized(CACHE) {
            val cached = CACHE.get(normalizedSlug)
            if (cached != null) {
                MAIN_HANDLER.post(Runnable { listener.onResolved(normalizedSlug, cached) })
                return
            }
            var waiting = IN_FLIGHT.get(normalizedSlug)
            if (waiting != null) {
                waiting.add(listener)
                return
            }
            waiting = ArrayList()
            waiting.add(listener)
            IN_FLIGHT.put(normalizedSlug, waiting)
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
            val providerSlug: String?
            synchronized(CACHE) {
                providerSlug = QUEUE.poll()
                if (providerSlug == null) {
                    queueRunning = false
                    return@queueTask
                }
            }
            requestProviderPage(providerSlug!!, 1)
        }, delayMs)
    }

    private fun requestProviderPage(normalizedSlug: String, attempt: Int) {
        val providerUrl = OPENROUTER_URL.toHttpUrl().newBuilder()
            .addPathSegment(normalizedSlug)
            .build()
        val request = Request.Builder().url(providerUrl).build()
        okHttpClientInstance!!.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                retryOrFinish(normalizedSlug, attempt, null)
            }

            override fun onResponse(call: Call, response: Response) {
                var iconUrl: String? = null
                var retryable = false
                try {
                    response.use { closeableResponse ->
                        if (closeableResponse.isSuccessful && closeableResponse.body != null) {
                            val page = Jsoup.parse(
                                closeableResponse.body.string(), providerUrl.toString()
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
                if (retryable) {
                    retryOrFinish(normalizedSlug, attempt, iconUrl)
                } else if (iconUrl != null && OpenRouterProviderIconLoader.isSvgUrl(iconUrl!!)) {
                    OpenRouterProviderIconLoader.fetchSvg(normalizedSlug, iconUrl!!)
                } else {
                    finish(normalizedSlug, iconUrl)
                }
            }
        })
    }

    private fun fetchSvg(providerSlug: String, iconUrl: String) {
        val request = Request.Builder().url(iconUrl).build()
        okHttpClientInstance!!.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finish(providerSlug, iconUrl)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful || closeableResponse.body == null) {
                            finish(providerSlug, iconUrl)
                            return
                        }
                        val rawSvg: ByteArray? = closeableResponse.body.bytes()
                        val svg = kotlin.text.String(rawSvg!!, StandardCharsets.UTF_8)
                        val sanitized = UNSUPPORTED_DISPLAY_P3_STYLE.matcher(svg).replaceAll("")
                        finish(providerSlug, sanitized.toByteArray(StandardCharsets.UTF_8))
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
        val expectedAlt = "Favicon for " + providerSlug
        for (image in page.select("img[alt][src]")) {
            if (!expectedAlt.equals(image.attr("alt").trim { it <= ' ' }, ignoreCase = true)) {
                continue
            }
            val iconUrl = image.absUrl("src")
            try {
                val parsed = iconUrl.toHttpUrl()
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
            MAIN_HANDLER.post(Runnable {
                for (listener in listeners) {
                    listener.onResolved(providerSlug, iconData)
                }
            })
        }
        requestNext(REQUEST_SPACING_MS)
    }

    fun interface CallbackListener {
        fun onResolved(providerSlug: String, iconData: Any?)
    }
}
