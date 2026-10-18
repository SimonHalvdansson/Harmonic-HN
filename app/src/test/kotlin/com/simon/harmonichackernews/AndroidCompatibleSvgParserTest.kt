package com.simon.harmonichackernews

import android.app.Application
import android.graphics.Color
import coil3.annotation.ExperimentalCoilApi
import coil3.svg.Svg
import coil3.toBitmap
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidCompatibleSvgParserTest {
    @Test
    fun lowercaseCurrentColorDoesNotSilentlyRenderATransparentPreview() {
        // Same inheritance as blog.davidv.dev/images/logo.svg: root fill=none, path
        // fill=currentcolor. AndroidSVG reports success but ignores the lowercase fill.
        val source = svg("""<path d="M0 0H20V20H0Z" fill="currentcolor"/>""")
        assertEquals(Color.TRANSPARENT, pixel(source, Svg.Parser.DEFAULT))
        assertEquals(Color.BLACK, pixel(source))
    }

    @Test
    fun keywordVariantsInheritTheDeclaredColor() {
        for (keyword in listOf("currentColor", "currentcolor", "CURRENTCOLOR", "CurrentColor", " currentcolor ")) {
            assertEquals(
                keyword,
                Color.RED,
                pixel(svg("""<rect width="20" height="20" color="red" fill='$keyword'/>""")),
            )
        }
    }

    @Test
    fun strokeAlsoInheritsTheDeclaredColor() {
        assertEquals(Color.BLUE, pixel(svg("""
            <path d="M0 10H20" stroke="CURRENTCOLOR" color="blue" stroke-width="4"/>
        """)))
    }

    @Test
    fun referencesAndExplicitPaintsRemainUnchanged() {
        assertEquals(Color.GREEN, pixel(svg("""
            <defs><linearGradient id="currentcolor">
                <stop offset="0" stop-color="#00ff00"/>
                <stop offset="1" stop-color="#00ff00"/>
            </linearGradient></defs>
            <rect width="20" height="20" fill="url(#currentcolor)"/>
        """)))
        assertEquals(Color.TRANSPARENT, pixel(svg("""<rect width="20" height="20" fill="none"/>""")))
        assertEquals(Color.BLUE, pixel(svg("""
            <rect width="20" height="20" fill="currentcolor" style="fill: blue"/>
        """)))
    }

    @Test
    fun nonUtf8DocumentsKeepTheirEncodingAndReferences() {
        val document = """<?xml version="1.0" encoding="ISO-8859-1"?>""" + svg("""
            <defs><linearGradient id="café">
                <stop offset="0" stop-color="red"/><stop offset="1" stop-color="red"/>
            </linearGradient></defs>
            <rect width="20" height="20" fill="url(#café)"/>
        """)
        val parsed = AndroidCompatibleSvgParser.parse(Buffer().write(document.toByteArray(Charsets.ISO_8859_1)))
        assertEquals(Color.RED, parsed.asImage(20, 20).toBitmap().getPixel(10, 10))
    }

    @Test
    fun compressedSvgIsPassedThroughWithoutCorruptingItsPayload() {
        val bytes = ByteArrayOutputStream().apply {
            object : GZIPOutputStream(this) {
                init { def.setLevel(Deflater.NO_COMPRESSION) }
            }.use { gzip ->
                gzip.write(svg("""<rect width="20" height="20" color="red" fill="currentColor"/>""").toByteArray())
            }
        }.toByteArray()
        val parsed = AndroidCompatibleSvgParser.parse(Buffer().write(bytes))
        assertEquals(Color.RED, parsed.asImage(20, 20).toBitmap().getPixel(10, 10))
    }

    private fun pixel(source: String, parser: Svg.Parser = AndroidCompatibleSvgParser): Int =
        parser.parse(Buffer().writeUtf8(source)).asImage(20, 20).toBitmap().getPixel(10, 10)

    private fun svg(content: String): String =
        """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="none">$content</svg>"""
}
