package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/** Only send prose to the translator. DOM text setters escape model output and preserve link targets. */
class TranslatableComment(html: String) {
    private val document = Ksoup.parse(html).apply { outputSettings().prettyPrint(false) }
    private val prose = buildList<TextNode> {
        fun visit(node: Node) {
            when (node) {
                is TextNode -> {
                    val text = node.getWholeText().trim()
                    if (text.any(Char::isLetter) && !text.startsWith("https://") && !text.startsWith("http://")) add(node)
                }
                is Element -> if (node.tagName() !in setOf("code", "pre", "script", "style")) {
                    node.childNodes().forEach(::visit)
                }
            }
        }
        visit(document.body())
    }
    val languageSample: String get() = prose.joinToString(" ") { it.getWholeText().trim() }.take(1000)

    suspend fun translate(transform: suspend (String) -> String): String {
        for (node in prose) {
            val raw = node.getWholeText()
            val translated = transform(raw.trim())
            node.text(raw.takeWhile(Char::isWhitespace) + translated + raw.takeLastWhile(Char::isWhitespace))
        }
        return document.body().html()
    }
}
