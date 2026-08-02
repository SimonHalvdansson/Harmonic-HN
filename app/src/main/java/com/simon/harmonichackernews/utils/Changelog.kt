package com.simon.harmonichackernews.utils

import android.content.Context
import android.text.Spanned
import android.util.TypedValue
import androidx.annotation.NonNull
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max

object Changelog {
    private const val CHANGELOG_ASSET = "changelog.md"
    private const val FALLBACK_CHANGELOG = "Changelog unavailable."
    private val BULLET_WIDTH_SP = 15f * 0.28f
    private const val UTF8_BOM = '\uFEFF'
    private var cachedMarkdown: String? = null

    fun getFormatted(context: Context): Spanned {
        val bulletWidth = max(
            1, Math.round(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    BULLET_WIDTH_SP,
                    context.getResources().getDisplayMetrics()
                )
            )
        )
        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.bulletWidth(bulletWidth)
                }
            })
            .build()
            .toMarkdown(readMarkdown(context)!!)
    }

    fun getMarkdown(context: Context): String? {
        return readMarkdown(context)
    }

    private fun readMarkdown(context: Context): String? {
        if (cachedMarkdown != null) {
            return cachedMarkdown
        }

        try {
            context.getAssets().open(CHANGELOG_ASSET).use { inputStream ->
                ByteArrayOutputStream().use { outputStream ->
                    val buffer = ByteArray(4096)
                    var read: Int
                    while ((inputStream.read(buffer).also { read = it }) != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    cachedMarkdown = outputStream.toString(StandardCharsets.UTF_8.name())
                    if (!cachedMarkdown!!.isEmpty() && cachedMarkdown!!.get(0) == UTF8_BOM) {
                        cachedMarkdown = cachedMarkdown!!.substring(1)
                    }
                }
            }
        } catch (e: IOException) {
            Utils.log("Failed to read changelog: " + e)
            cachedMarkdown = FALLBACK_CHANGELOG
        }

        return cachedMarkdown
    }
}
