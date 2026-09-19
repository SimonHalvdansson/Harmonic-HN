package com.simon.harmonichackernews

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.simon.harmonichackernews.presentation.WebContentAssets
import com.simon.harmonichackernews.resources.Res
import java.io.ByteArrayInputStream
import java.io.IOException

/** Gives PDF.js modules, fonts and workers a local HTTPS origin without enabling file access. */
internal object AndroidPdfWebViewAssets {
    const val VIEWER_URL = "https://appassets.androidplatform.net/harmonic-pdf/index.html"
    private const val PATH_PREFIX = "/harmonic-pdf/"
    private val assetRoot = Res.getUri("files/web/${WebContentAssets.PDF_VIEWER_INDEX}")
        .removePrefix("file:///android_asset/")
        .substringBeforeLast('/') + "/"
    private val statusFontAsset = Res.getUri("font/product_sans_regular.ttf")
        .removePrefix("file:///android_asset/")

    fun loader(context: Context): WebViewAssetLoader {
        val assets = context.applicationContext.assets
        return WebViewAssetLoader.Builder()
            .addPathHandler(PATH_PREFIX) { path ->
                // Failed local resources must stay local rather than falling through to a network
                // request. Only the PDF directory and the bundled status font are exposed.
                if (path.split('/').any { it == ".." || it.contains('\\') }) {
                    missing()
                } else {
                    try {
                        val asset = if (path == "fonts/product_sans_regular.ttf") {
                            statusFontAsset
                        } else {
                            assetRoot + path
                        }
                        WebResourceResponse(mimeType(path), "UTF-8", assets.open(asset))
                    } catch (_: IOException) {
                        missing()
                    }
                }
            }
            .build()
    }

    private fun mimeType(path: String): String = when (path.substringAfterLast('.')) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "wasm" -> "application/wasm"
        "svg" -> "image/svg+xml"
        "gif" -> "image/gif"
        "png" -> "image/png"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

    private fun missing() = WebResourceResponse(
        "text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()),
    )
}
