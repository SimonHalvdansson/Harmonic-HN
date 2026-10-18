package com.simon.harmonichackernews

import coil3.annotation.ExperimentalCoilApi
import coil3.svg.Svg
import okio.Buffer
import okio.BufferedSource

/** AndroidSVG 1.4 treats the CSS currentColor keyword as case-sensitive. */
@OptIn(ExperimentalCoilApi::class)
internal object AndroidCompatibleSvgParser : Svg.Parser {
    override fun parse(source: BufferedSource): Svg {
        // AndroidSVG also accepts SVGZ. Leave compressed bytes to its own decompressor.
        if (source.request(2) && source.buffer[0] == 0x1f.toByte() && source.buffer[1] == 0x8b.toByte()) {
            return Svg.Parser.DEFAULT.parse(source)
        }
        // A byte-preserving mapping lets us change ASCII attributes without re-encoding the
        // document's text or overriding its XML encoding. Non-ASCII-compatible encodings pass
        // through unchanged to AndroidSVG.
        val document = source.readByteArray().toString(Charsets.ISO_8859_1)
        if (!document.contains("currentcolor", ignoreCase = true)) {
            return parseDocument(document)
        }
        val normalized = xmlMarkup.replace(document) { markup ->
            val tag = markup.value
            if (tag.startsWith("<!") || tag.startsWith("<?")) {
                tag
            } else {
                attribute.replace(tag) { match ->
                    val name = match.groupValues[1]
                    val value = match.groupValues[4]
                    if (name in paintAttributes && value.trim().equals("currentColor", ignoreCase = true)) {
                        "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}currentColor${match.groupValues[3]}"
                    } else {
                        match.value
                    }
                }
            }
        }
        return parseDocument(normalized)
    }

    private fun parseDocument(document: String): Svg =
        Buffer().write(document.toByteArray(Charsets.ISO_8859_1)).use(Svg.Parser.DEFAULT::parse)

    // Match whole quoted attributes and skip comments/CDATA so IDs, URLs, text, and unrelated
    // attribute values keep their original case. Do not replace arbitrary keyword occurrences.
    private val xmlMarkup = Regex(
        """<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|<\?[\s\S]*?\?>|<(?:[^>"']++|"[^"]*"|'[^']*')*>""",
    )
    private val attribute = Regex("""([\w:.-]+)(\s*=\s*)(["'])(.*?)\3""", RegexOption.DOT_MATCHES_ALL)
    private val paintAttributes = setOf("fill", "stroke")
}
