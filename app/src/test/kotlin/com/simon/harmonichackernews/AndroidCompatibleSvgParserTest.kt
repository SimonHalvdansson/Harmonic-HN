package com.simon.harmonichackernews

import android.app.Application
import android.graphics.Color
import android.util.Base64
import coil3.annotation.ExperimentalCoilApi
import coil3.svg.Svg
import coil3.toBitmap
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32
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
    fun compressedSvgRendersWithoutCorruptingItsPayload() {
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

    @Test
    fun sourceLimitRejectsBeforeReadingTheWholePayloadAndAcceptsTheExactLimit() {
        val source = Buffer().write(ByteArray((AndroidCompatibleSvgParser.MAX_SOURCE_BYTES + 100).toInt()))
        val error = assertThrows(IOException::class.java) { AndroidCompatibleSvgParser.parse(source) }
        assertTrue(error.message.orEmpty().contains("byte limit"))
        assertEquals(99L, source.size)

        val empty = svg("")
        val padding = AndroidCompatibleSvgParser.MAX_SOURCE_BYTES.toInt() - empty.toByteArray().size - 7
        AndroidCompatibleSvgParser.parse(Buffer().writeUtf8(svg("<!--${" ".repeat(padding)}-->")))
    }

    @Test
    fun compressedInputHasBothSourceAndInflatedLimits() {
        val expanded = svg("<!--${" ".repeat(AndroidCompatibleSvgParser.MAX_DOCUMENT_BYTES.toInt())}-->")
        assertRejected(gzip(expanded.toByteArray()), "byte limit")
        val exact = svg("<!--${" ".repeat(AndroidCompatibleSvgParser.MAX_DOCUMENT_BYTES.toInt() - svg("").length - 7)}-->")
        AndroidCompatibleSvgParser.parse(Buffer().write(gzip(exact.toByteArray())))
        val oversizedCompressed = ByteArrayOutputStream().apply {
            object : GZIPOutputStream(this) {
                init { def.setLevel(Deflater.NO_COMPRESSION) }
            }.use { it.write(exact.toByteArray()) }
        }.toByteArray()
        assertRejected(oversizedCompressed, "byte limit")
    }

    @Test
    fun entityDeclarationsAreRejectedInPlainCompressedAndUtf16Documents() {
        val document = """<!DOCTYPE svg [<!ENTITY paint "red">]>""" +
            svg("""<rect width="20" height="20" fill="&paint;"/>""")
        assertRejected(document.toByteArray(), "DTD")
        assertRejected(gzip(document.toByteArray()), "DTD")
        assertRejected(document.toByteArray(Charsets.UTF_16), "DTD")
        assertFalse(com.caverock.androidsvg.SVG.isInternalEntitiesEnabled())
    }

    @Test
    fun ordinaryDoctypeNumericEntitiesCdataAndNamespacesKeepWorking() {
        val document = """<?xml version="1.0"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
            "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">""" + svg("""
            <style><![CDATA[rect { fill: red; }]]></style>
            <rect width="20" height="20" fill="&#99;urrentcolor"/>
            <text>&amp;&lt;&#65;</text>
        """)
        assertEquals(Color.RED, pixel(document))
        assertEquals(Color.RED, AndroidCompatibleSvgParser.parse(Buffer().write(gzip(document.toByteArray())))
            .asImage(20, 20).toBitmap().getPixel(10, 10))
    }

    @Test
    fun utf16AndCompressedLowercasePaintsKeepWorking() {
        val document = svg("""<rect width="20" height="20" fill="currentcolor" color="red"/>""")
        for (bytes in listOf(document.toByteArray(Charsets.UTF_16), gzip(document.toByteArray()))) {
            assertEquals(Color.RED, AndroidCompatibleSvgParser.parse(Buffer().write(bytes))
                .asImage(20, 20).toBitmap().getPixel(10, 10))
        }
    }

    @Test
    fun excessiveXmlDepthAndElementCountAreRejected() {
        assertRejected(svg("<g>".repeat(AndroidSvgDocumentPolicy.MAX_DEPTH) +
            "</g>".repeat(AndroidSvgDocumentPolicy.MAX_DEPTH)).toByteArray(), "nesting")
        assertRejected(svg("<path/>".repeat(AndroidSvgDocumentPolicy.MAX_ELEMENTS)).toByteArray(), "element")
        AndroidCompatibleSvgParser.parse(Buffer().writeUtf8(svg(
            "<g>".repeat(AndroidSvgDocumentPolicy.MAX_DEPTH - 1) +
                "</g>".repeat(AndroidSvgDocumentPolicy.MAX_DEPTH - 1),
        )))
    }

    @Test
    fun recursiveReferencesCannotBypassXmlLimitsThroughAliasesOrEscapes() {
        for (reference in listOf("#a", "&#35;a", " '#a' ")) {
            assertRejected(svg("""<g xml:id=" a " xmlns:link="http://www.w3.org/1999/xlink">
                <use link:href="$reference"/></g>""").toByteArray(), "recursive")
        }
        assertRejected(svg("""<defs><linearGradient id="a" href="#b"/>
            <linearGradient id="b" href="#c"/><linearGradient id="c" href="#b"/></defs>""")
            .toByteArray(), "recursive")
    }

    @Test
    fun referenceExpansionAndEffectiveDepthAreBounded() {
        val doubling = buildString {
            append("""<defs><g id="n0"><path/></g>""")
            for (index in 1..18) append("""<g id="n$index"><use href="#n${index - 1}"/>
                <use href="#n${index - 1}"/></g>""")
            append("</defs>")
        }
        assertRejected(svg(doubling).toByteArray(), "expansion")
        val chain = buildString {
            append("<defs>")
            for (index in 0..70) append("""<g id="n$index"><use href="#n${index + 1}"/></g>""")
            append("</defs>")
        }
        assertRejected(svg(chain).toByteArray(), "reference")
    }

    @Test
    fun normalSymbolReuseAndEmbeddedRasterStillRender() {
        assertEquals(Color.RED, pixel(svg("""<defs><g id="shape">
            <rect width="20" height="20" fill="red"/></g></defs>
            <use xmlns:link="http://www.w3.org/1999/xlink" link:href="#shape"/>""")))
        val image = Base64.encodeToString(png(1, 1), Base64.NO_WRAP)
        assertEquals(Color.RED, pixel(svg("""<image width="20" height="20"
            xmlns:link="http://www.w3.org/1999/xlink" link:href="data:image/png;base64,$image"/>""")))
    }

    @Test
    fun embeddedRasterDimensionsAreCheckedEvenWhenHiddenAndEscaped() {
        val image = Base64.encodeToString(png(4096, 4096), Base64.NO_WRAP)
        assertRejected(svg("""<defs><image width="1" height="1" display="none"
            xmlns:link="http://www.w3.org/1999/xlink" link:href=" &#100;ata:image/png;base64,$image "/>
            </defs>""").toByteArray(), "embedded image dimensions")
    }

    @Test
    fun embeddedRastersHaveAnAggregatePixelBudget() {
        val image = Base64.encodeToString(png(2048, 2048), Base64.NO_WRAP)
        assertRejected(svg(("""<image width="1" height="1" href="data:image/png;base64,$image"/>""")
            .repeat(3)).toByteArray(), "embedded image budget")
    }

    @Test
    fun reusingAnEmbeddedRasterCannotBypassThePixelBudget() {
        val image = Base64.encodeToString(png(2048, 2048), Base64.NO_WRAP)
        assertRejected(svg("""<defs><image id="raster" width="1" height="1"
            href="data:image/png;base64,$image"/></defs><use href="#raster"/><use href="#raster"/>""")
            .toByteArray(), "embedded image reference budget")
    }

    @Test
    fun externalEntitySubsetsAndMalformedCompressedDocumentsFailClosed() {
        assertRejected(("""<!DOCTYPE svg [<!ENTITY leak SYSTEM "file:///does-not-exist">]>""" + svg("""
            <text>&leak;</text>""")).toByteArray(), "DTD")
        assertThrows(Exception::class.java) { AndroidCompatibleSvgParser.parse(Buffer().writeUtf8("<svg><g>")) }
        val compressed = gzip(svg("").toByteArray())
        assertThrows(IOException::class.java) {
            AndroidCompatibleSvgParser.parse(Buffer().write(compressed.copyOf(compressed.size - 5)))
        }
    }

    @Test
    fun recursivePaintResourcesAreRejectedInPresentationInlineAndStylesheetCss() {
        for ((style, paint) in listOf(
            "" to """fill="url(#p)"""",
            "" to """style="fill: url('#p')"""",
            "<style>.recursive { fill: url('#p'); }</style>" to """class="recursive"""",
        )) {
            assertRejected(svg("""$style<defs>
                <pattern id="p" width="1" height="1" patternUnits="userSpaceOnUse">
                    <rect width="1" height="1" $paint/>
                </pattern></defs><rect width="20" height="20" fill="url(#p)"/>""").toByteArray(), "recursive")
        }
        assertRejected(svg("""<defs><mask id="m"><rect width="20" height="20" mask="url(#m)"/></mask>
            </defs><rect width="20" height="20" mask="url(#m)"/>""").toByteArray(), "recursive")
    }

    @Test
    fun normalCssPatternsAndMasksStillRenderIncludingOverriddenPaintReferences() {
        assertEquals(Color.RED, pixel(svg("""<style>.outer { fill: url('#p'); }</style><defs>
            <pattern id="p" width="10" height="10" patternUnits="userSpaceOnUse">
                <rect width="10" height="10" fill="url(#p)" style="fill: red"/>
            </pattern></defs><rect width="20" height="20" class="outer"/>""")))
        assertEquals(Color.RED, pixel(svg("""<defs><mask id="m">
            <rect width="20" height="20" fill="white"/></mask></defs>
            <rect width="20" height="20" fill="red" style="mask: url('#m')"/>""")))
    }

    private fun assertRejected(bytes: ByteArray, reason: String) {
        val error = assertThrows(IOException::class.java) {
            AndroidCompatibleSvgParser.parse(Buffer().write(bytes))
        }
        assertTrue(error.message, error.message.orEmpty().contains(reason))
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).use { it.write(bytes) }
    }.toByteArray()

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val bytes = ByteArrayOutputStream().apply {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        bitmap.recycle()
        // Bounds-only decoding reads IHDR; no large bitmap needs to be allocated for these tests.
        ByteBuffer.wrap(bytes).putInt(16, width).putInt(20, height)
        ByteBuffer.wrap(bytes).putInt(29, CRC32().apply { update(bytes, 12, 17) }.value.toInt())
        return bytes
    }

    private fun pixel(source: String, parser: Svg.Parser = AndroidCompatibleSvgParser): Int =
        parser.parse(Buffer().writeUtf8(source)).asImage(20, 20).toBitmap().getPixel(10, 10)

    private fun svg(content: String): String =
        """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="none">$content</svg>"""
}
