package com.simon.harmonichackernews

import coil3.annotation.ExperimentalCoilApi
import coil3.svg.Svg
import com.caverock.androidsvg.SVG
import okio.Buffer
import okio.BufferedSource
import okio.GzipSource
import okio.buffer
import java.io.IOException

/** Bounds untrusted SVGs before AndroidSVG parses or renders them. */
@OptIn(ExperimentalCoilApi::class)
internal object AndroidCompatibleSvgParser : Svg.Parser {
    internal const val MAX_SOURCE_BYTES = 2L * 1024 * 1024
    internal const val MAX_DOCUMENT_BYTES = 4L * 1024 * 1024

    init {
        // AndroidSVG otherwise switches to SAX and expands internal XML entities.
        SVG.setInternalEntitiesEnabled(false)
    }

    override fun parse(source: BufferedSource): Svg {
        val encoded = source.readBounded(MAX_SOURCE_BYTES)
        val document = if (encoded.size >= 2 &&
            encoded[0] == 0x1f.toByte() && encoded[1] == 0x8b.toByte()
        ) {
            // Do not let AndroidSVG inflate SVGZ without an expanded-size budget.
            GzipSource(encoded).buffer().use { it.readBounded(MAX_DOCUMENT_BYTES) }
        } else {
            encoded
        }
        return AndroidSvgDocumentPolicy.prepare(document).use(Svg.Parser.DEFAULT::parse)
    }
}

private fun BufferedSource.readBounded(limit: Long): Buffer {
    val result = Buffer()
    while (read(result, minOf(8_192L, limit + 1 - result.size)) != -1L) {
        if (result.size > limit) throw IOException("SVG exceeds byte limit ($limit)")
    }
    return result
}
