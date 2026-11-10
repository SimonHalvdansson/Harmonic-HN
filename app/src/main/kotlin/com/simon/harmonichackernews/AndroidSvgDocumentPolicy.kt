package com.simon.harmonichackernews

import android.util.Xml
import com.caverock.androidsvg.HarmonicSvgLimits
import okio.Buffer
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.OutputStream

/** XML-aware checks also cover SVGZ, non-UTF-8 input and escaped attribute values. */
internal object AndroidSvgDocumentPolicy {
    internal const val MAX_ELEMENTS = 10_000
    internal const val MAX_DEPTH = 64
    private const val MAX_NORMALIZED_BYTES = 8L * 1024 * 1024

    fun prepare(document: Buffer): Buffer {
        val parser = Xml.newPullParser().apply {
            // Preserve qualified names and namespace declarations verbatim in the serializer.
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(document.inputStream(), null)
        }
        val output = Buffer()
        val serializer = Xml.newSerializer().apply {
            setOutput(object : OutputStream() {
                override fun write(value: Int) {
                    if (output.size >= MAX_NORMALIZED_BYTES) reject("normalized byte limit")
                    output.writeByte(value)
                }

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    if (length > MAX_NORMALIZED_BYTES - output.size) reject("normalized byte limit")
                    output.write(bytes, offset, length)
                }
            }, "UTF-8")
            startDocument("UTF-8", null)
        }
        var elements = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.DOCDECL -> {
                    // Ordinary exported SVG DOCTYPEs work; internal subsets/entities do not.
                    if ('[' in parser.text) reject("internal DTD subset")
                }
                XmlPullParser.START_TAG -> {
                    if (++elements > MAX_ELEMENTS) reject("element limit")
                    if (parser.depth > MAX_DEPTH) reject("nesting limit")
                    if (parser.attributeCount > 128) reject("attribute limit")
                    serializer.startTag(null, parser.name)
                    for (attribute in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(attribute)
                        val value = parser.getAttributeValue(attribute)
                        // AndroidSVG 1.4 treats this CSS keyword as case-sensitive. Parse XML
                        // rather than applying regexes to untrusted markup or changing IDs/text.
                        val normalized = if ((name == "fill" || name == "stroke") &&
                            value.trim().equals("currentColor", ignoreCase = true)
                        ) "currentColor" else value
                        serializer.attribute(null, name, normalized)
                    }
                }
                XmlPullParser.END_TAG -> {
                    serializer.endTag(null, parser.name)
                }
                XmlPullParser.CDSECT -> serializer.cdsect(parser.text)
                XmlPullParser.TEXT, XmlPullParser.IGNORABLE_WHITESPACE ->
                    serializer.text(parser.text)
                XmlPullParser.ENTITY_REF -> {
                    // Predefined/numeric references remain supported, unresolved entities fail.
                    serializer.text(parser.text ?: reject("unresolved entity"))
                }
                // Comments, DTDs and processing instructions are not needed for image rendering.
            }
            event = parser.nextToken()
        }
        serializer.endDocument()
        // Use AndroidSVG's own CSS and IRI interpretation for resource graph checks. The
        // bounded document is parsed again by Coil so its public SVG adapter stays in use.
        output.peek().inputStream().use { HarmonicSvgLimits.validate(it, MAX_DEPTH) }
        return output
    }

    private fun reject(reason: String): Nothing = throw IOException("SVG rejected: $reason")
}
